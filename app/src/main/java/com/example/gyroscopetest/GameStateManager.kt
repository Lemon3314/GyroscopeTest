package com.example.gyroscopetest

import androidx.compose.runtime.*
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ==========================================
// 1. 外部配置與類型定義
// ==========================================
object GameConfig {
    const val MOVE_SENSITIVITY = 4.0f
    const val ANGLE_THRESHOLD = 20f

    const val BOUND_OPTION_A_BOTTOM = 0.15f
    const val BOUND_OPTION_B_TOP = 0.85f
    const val BOUND_OPTION_C_RIGHT = 0.20f
    const val BOUND_OPTION_D_LEFT = 0.80f
    const val MAX_QUESTIONS = 5
}

enum class GameScene { START, PLAYING, RESULT }
enum class FeedbackType { NONE, CORRECT, WRONG }

class GameViewModel(private val savedState: SavedStateHandle) : ViewModel() {

    // ==========================================
    // 2. UI 觀測狀態 (Public States)
    // ==========================================

    // 場景與分數
    //5項
    var currentScene by mutableStateOf(savedState["scene"] ?: GameScene.START)
        private set
    var score by mutableIntStateOf(savedState["score"] ?: 0)
        private set
    var correctCount by mutableIntStateOf(savedState["correctCount"] ?: 0)
        private set
    var wrongCount by mutableIntStateOf(savedState["wrongCount"] ?: 0)
        private set
    var currentIndexInShuffled by mutableIntStateOf(savedState["currentIndex"] ?: 0)
        private set

    // 游標與交互
    //3項
    var cursorX by mutableFloatStateOf(0.5f)
        private set
    var cursorY by mutableFloatStateOf(0.5f)
        private set
    var selectedOption by mutableStateOf("")
        private set

    // 回饋與鎖定狀態
    //2項
    var feedback by mutableStateOf(FeedbackType.NONE)
        private set
    var lockRemainingSeconds by mutableIntStateOf(0)
        private set

    // ==========================================
    // 3. 內部控制變數 (Private States)
    // ==========================================

    //5項
    private var shuffledIndices: List<Int> = savedState.get<List<Int>>("indices")
        ?: QuizRepository.questions.indices.shuffled()

    //鎖定的時間 (未來時間點)
    private var lockUntilTimestamp by mutableLongStateOf(savedState["lockUntil"] ?: 0L)
    //反饋的時間 (未來時間點)
    private var feedbackUntilTimestamp by mutableLongStateOf(0L)
    //倒數計時cancel掉上一個
    private var timerJob: Job? = null
    //cancel掉 submitAnswer
    private var sumbitJob: Job? = null



    // 物理運算變數
    //4項
    private var angleX = 0f
    private var angleY = 0f
    private var offsetPitch = 0f
    private var offsetRoll = 0f


    // ==========================================
    // 4. 初始化與計算屬性
    // ==========================================
    val currentQuestion: Question
        get() = QuizRepository.questions[shuffledIndices[currentIndexInShuffled]]

    init {
        // 如果 App 被重啟時還在鎖定時間內，自動喚醒倒數計時器
        if (isLocked()) startLockTimer()
    }

    // ==========================================
    // 5. 場景導航 (Navigation)
    // ==========================================
    fun startGame() {
        currentScene = GameScene.PLAYING
        score = 0
        correctCount = 0
        wrongCount = 0
        currentIndexInShuffled = 0


        shuffledIndices = QuizRepository.questions.indices.shuffled()

        // 強制重設所有時間與 Job
        lockUntilTimestamp = 0L
        feedbackUntilTimestamp = 0L
        lockRemainingSeconds = 0
        feedback = FeedbackType.NONE

        timerJob?.cancel()
        sumbitJob?.cancel() // 也要把跳題協程砍掉


        calibrateCenter()
        persist()
    }

    fun finishGame() {
        currentScene = GameScene.RESULT
        persist()
    }

    fun backToStart() {
        currentScene = GameScene.START
        persist()
    }

    // ==========================================
    // 6. 核心遊戲邏輯 (Core Logic)
    // ==========================================

    /**
     * 感測器數據處理：將角速度轉化為游標移動
     */
    fun processSensorData(gyroX: Float, gyroY: Float, dt: Float) {
        if (isLocked() || isShowingFeedback()) return

        // 積分運算：$$ \Delta \theta = \omega \cdot \Delta t $$
        angleX += gyroX * dt
        angleY += gyroY * dt

        val relPitch = Math.toDegrees(angleX.toDouble()).toFloat() - offsetPitch
        val relRoll = Math.toDegrees(angleY.toDouble()).toFloat() - offsetRoll

        // 計算位移推力 (加上死區判定)
        val dx = calculatePush(relRoll, GameConfig.ANGLE_THRESHOLD) * GameConfig.MOVE_SENSITIVITY * dt
        val dy = calculatePush(relPitch, GameConfig.ANGLE_THRESHOLD) * GameConfig.MOVE_SENSITIVITY * dt

        // 座標更新與邊界檢查
        cursorX = (cursorX + dx / 100f).coerceIn(0f, 1f)
        cursorY = (cursorY + dy / 100f).coerceIn(0f, 1f)

        // 區域判定
        updateSelectedOption()
    }

    /**
     * 答題提交 (🔥 統一的遮罩式邏輯)
     */
    fun submitAnswer(answerCode: String) {
        if (isShowingFeedback() || isLocked()) return

        // --- A. 立即處理數據 (就算 App 崩潰，這部分也要先保住) ---
        if (answerCode == currentQuestion.correctAnswerCode) {
            score += 10
            correctCount++
            triggerFeedback(FeedbackType.CORRECT, 1500)
        } else {
            score = (score - 5).coerceAtLeast(0)
            wrongCount++
            triggerFeedback(FeedbackType.WRONG, 1500)
            lockUntilTimestamp = System.currentTimeMillis() + 5000
            startLockTimer()
        }

        resetCursor() // 游標立刻回中間

        // --- B. 立即更新後台題目 (玩家被遮罩擋住看不到) ---
        val isLastQuestion = currentIndexInShuffled == GameConfig.MAX_QUESTIONS - 1
        if (!isLastQuestion) {
            currentIndexInShuffled++
        }
//        currentIndexInShuffled++

        // 這裡先存一次存檔 (分數、題號與鎖定狀態都最新了)，防止玩家刷分
        persist()

        // --- C. 處理「遮罩消失」的非同步邏輯 ---
        sumbitJob?.cancel()
        sumbitJob = viewModelScope.launch {

            // 只需等待 1.5 秒讓玩家看清楚遮罩動畫
            delay(1500)

            // 時間到，手動清空回饋 (遮罩瞬間消失，露出早已換好的新題目)
            feedback = FeedbackType.NONE

            // 如果是最後一題，等遮罩拿掉後才切換到結算畫面
            if (isLastQuestion) {
                finishGame()
            }
        }
    }


    //校準中心
    fun calibrateCenter() {
        offsetPitch = Math.toDegrees(angleX.toDouble()).toFloat()
        offsetRoll = Math.toDegrees(angleY.toDouble()).toFloat()
        resetCursor()
    }

    // ==========================================
    // 7. 輔助工具函數 (Helpers)
    // ==========================================

    private fun updateSelectedOption() {
        selectedOption = when {
            cursorY < GameConfig.BOUND_OPTION_A_BOTTOM -> "A"
            cursorY > GameConfig.BOUND_OPTION_B_TOP -> "B"
            cursorX < GameConfig.BOUND_OPTION_C_RIGHT -> "C"
            cursorX > GameConfig.BOUND_OPTION_D_LEFT -> "D"
            else -> ""
        }
    }

    // (goToNextQuestion 函式已刪除，因為邏輯全部統一到 submitAnswer 中了)

    private fun resetCursor() {
        cursorX = 0.5f
        cursorY = 0.5f
        selectedOption = ""
    }

    private fun triggerFeedback(type: FeedbackType, duration: Long) {
        feedback = type
        feedbackUntilTimestamp = System.currentTimeMillis() + duration
    }

    fun isLocked(): Boolean = System.currentTimeMillis() < lockUntilTimestamp

    // 只負責回傳布林值，不要偷偷改變狀態
    fun isShowingFeedback(): Boolean = System.currentTimeMillis() < feedbackUntilTimestamp

    private fun calculatePush(angle: Float, thres: Float): Float = when {
        angle > thres -> angle - thres
        angle < -thres -> angle + thres
        else -> 0f
    }

    private fun startLockTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isLocked()) {
                val remaining = lockUntilTimestamp - System.currentTimeMillis()
                // 【修正 6 秒問題】：加上 999 再除，這樣 5000ms 會顯示 5，4001ms 也會顯示 5
                lockRemainingSeconds = ((remaining + 999) / 1000).toInt()
                delay(250) // 縮短檢查時間，讓數字跳動更即時
            }
            lockRemainingSeconds = 0
        }
    }

    //feedback不需要persist(接續)
    //共7個變數需要
    private fun persist() {
        savedState["scene"] = currentScene
        savedState["score"] = score
        savedState["correctCount"] = correctCount
        savedState["wrongCount"] = wrongCount
        savedState["indices"] = shuffledIndices
        savedState["currentIndex"] = currentIndexInShuffled
        savedState["lockUntil"] = lockUntilTimestamp
    }
}