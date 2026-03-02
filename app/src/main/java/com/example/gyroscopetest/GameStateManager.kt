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
    var cursorX by mutableFloatStateOf(0.5f)
        private set
    var cursorY by mutableFloatStateOf(0.5f)
        private set
    var selectedOption by mutableStateOf("")
        private set

    // 回饋與鎖定狀態
    var feedback by mutableStateOf(FeedbackType.NONE)
        private set
    var lockRemainingSeconds by mutableIntStateOf(0)
        private set

    // ==========================================
    // 3. 內部控制變數 (Private States)
    // ==========================================
    private var shuffledIndices: List<Int> = savedState.get<List<Int>>("indices")
        ?: QuizRepository.questions.indices.shuffled()

    private var lockUntilTimestamp by mutableLongStateOf(savedState["lockUntil"] ?: 0L)
    private var feedbackUntilTimestamp by mutableLongStateOf(0L)

    // 物理運算變數
    private var angleX = 0f
    private var angleY = 0f
    private var offsetPitch = 0f
    private var offsetRoll = 0f
    private var timerJob: Job? = null

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
        score = 0
        correctCount = 0
        wrongCount = 0
        currentIndexInShuffled = 0
        shuffledIndices = QuizRepository.questions.indices.shuffled()

        currentScene = GameScene.PLAYING
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
     * 答題提交
     */
    fun submitAnswer(answerCode: String) {
        if (isShowingFeedback() || isLocked()) return

        if (answerCode == currentQuestion.correctAnswerCode) {
            score += 10
            correctCount++
            triggerFeedback(FeedbackType.CORRECT, 1500)
        } else {
            score = (score - 5).coerceAtLeast(0)
            wrongCount++
            triggerFeedback(FeedbackType.WRONG, 1500)

            // 處發 5 秒懲罰
            lockUntilTimestamp = System.currentTimeMillis() + 5000
            startLockTimer()
        }

        goToNextQuestion()
        resetCursor()
        persist()
    }

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

    private fun goToNextQuestion() {
        if (currentIndexInShuffled < GameConfig.MAX_QUESTIONS - 1) {
            currentIndexInShuffled++
        } else {
            finishGame()
        }
    }

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

    fun isShowingFeedback(): Boolean {
        val active = System.currentTimeMillis() < feedbackUntilTimestamp
        if (!active && feedback != FeedbackType.NONE) feedback = FeedbackType.NONE
        return active
    }

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
                lockRemainingSeconds = (remaining / 1000).toInt() + 1
                delay(500)
            }
            lockRemainingSeconds = 0
        }
    }

    //feedback不需要persist(接續)
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