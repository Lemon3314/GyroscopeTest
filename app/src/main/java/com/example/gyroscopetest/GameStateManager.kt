package com.example.gyroscopetest

import androidx.compose.runtime.*
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// --- 遊戲環境配置 ---
object GameConfig {
    const val MOVE_SENSITIVITY = 4.0f
    const val ANGLE_THRESHOLD = 20f

    const val BOUND_OPTION_A_BOTTOM = 0.15f
    const val BOUND_OPTION_B_TOP = 0.85f
    const val BOUND_OPTION_C_RIGHT = 0.20f
    const val BOUND_OPTION_D_LEFT = 0.80f
}

enum class FeedbackType { NONE, CORRECT, WRONG }

class GameViewModel(private val savedState: SavedStateHandle) : ViewModel() {

    // --- 觀測型狀態 (UI 會自動監聽這些變數) ---


    // --- [關鍵數據：存檔區] ---
    // 使用 SavedStateHandle 讓資料在「App 被後台殺掉」後仍能恢復
    var score by mutableIntStateOf(savedState["score"] ?: 0)
        private set // private set 確保外部(UI)不能亂改分數，只能由 ViewModel 改
    var correctCount by mutableIntStateOf(savedState["correctCount"] ?: 0)
        private set
    var wrongCount by mutableIntStateOf(savedState["wrongCount"] ?: 0)
        private set
    var currentIndexInShuffled by mutableIntStateOf(savedState["currentIndex"] ?: 0)
        private set

    // 游標與選項狀態
    // 游標位置：0.0 ~ 1.0 的百分比，這讓 UI 在不同解析度都能正確繪製
    var cursorX by mutableFloatStateOf(0.5f)
        private set
    var cursorY by mutableFloatStateOf(0.5f)
        private set
    var selectedOption by mutableStateOf("")
        private set

    // 回饋與倒數狀態 (關鍵：觀測型數據)
    var feedback by mutableStateOf(FeedbackType.NONE)
        private set

    // 鎖定倒數：觀測型 State，數值變動時 UI 會自動更新文字
    var lockRemainingSeconds by mutableIntStateOf(0)
        private set

    // --- 內部私有狀態 ---
    private var shuffledIndices: List<Int> = savedState.get<List<Int>>("indices")
        ?: QuizRepository.questions.indices.shuffled()

    private var lockUntilTimestamp by mutableLongStateOf(savedState["lockUntil"] ?: 0L)
    private var feedbackUntilTimestamp by mutableLongStateOf(0L)

    // --- [內部邏輯變數] ---
    private var angleX = 0f // 累計的 X 軸弧度
    private var angleY = 0f // 累計的 Y 軸弧度
    private var offsetPitch = 0f // 校正點的 Pitch 角度
    private var offsetRoll = 0f  // 校正點的 Roll 角度

    private var timerJob: Job? = null

    val currentQuestion: Question
        get() = QuizRepository.questions[shuffledIndices[currentIndexInShuffled]]

    // 初始化時，如果發現還在鎖定時間內，自動重啟計時器
    init {
        if (isLocked()) {
            startLockTimer()
        }
    }

    private fun persist() {
        savedState["score"] = score
        savedState["correctCount"] = correctCount
        savedState["wrongCount"] = wrongCount
        savedState["indices"] = shuffledIndices
        savedState["currentIndex"] = currentIndexInShuffled
        savedState["lockUntil"] = lockUntilTimestamp
    }

    // --- 倒數計時核心邏輯 ---
    // --- [核心邏輯 B：倒數計時器] ---
    private fun startLockTimer() {
        timerJob?.cancel() // 啟動前先取消舊的，避免多個計時器疊加導致秒數跳動
        timerJob = viewModelScope.launch {
            while (isLocked()) {
                val remaining = lockUntilTimestamp - System.currentTimeMillis()
                // 將毫秒轉為秒，+1 是為了讓顯示從 5 開始而不是 4.9
                lockRemainingSeconds = (remaining / 1000).toInt() + 1
                delay(500) // 每 0.5 秒檢查一次即可
            }
            lockRemainingSeconds = 0
        }
    }

    fun isLocked(): Boolean = System.currentTimeMillis() < lockUntilTimestamp

    fun isShowingFeedback(): Boolean {
        val active = System.currentTimeMillis() < feedbackUntilTimestamp
        if (!active && feedback != FeedbackType.NONE) {
            feedback = FeedbackType.NONE
        }
        return active
    }

    // --- 感測器處理 ---
    // --- [核心邏輯 A：感測器處理] ---
    fun processSensorData(gyroX: Float, gyroY: Float, dt: Float) {
        // 如果還在「答錯鎖定」或「顯示正確/錯誤回饋」，則不讓下游標移動
        if (isLocked() || isShowingFeedback()) return

        // 【物理公式】：角度 = 角速度 * 時間差 (Integration)
        // $$ \Delta \theta = \omega \cdot \Delta t $$
        angleX += gyroX * dt
        angleY += gyroY * dt

        // 將弧度轉為角度，並減去「校正偏移量」
        val relPitch = Math.toDegrees(angleX.toDouble()).toFloat() - offsetPitch
        val relRoll = Math.toDegrees(angleY.toDouble()).toFloat() - offsetRoll

        // 【推力計算】：超過死區 (Threshold) 才開始移動，防止手抖
        val dx = calculatePush(relRoll, GameConfig.ANGLE_THRESHOLD) * GameConfig.MOVE_SENSITIVITY * dt
        val dy = calculatePush(relPitch, GameConfig.ANGLE_THRESHOLD) * GameConfig.MOVE_SENSITIVITY * dt

        // 更新座標：/100f 是為了將角度縮放到 0~1 的範圍，coerceIn 確保游標不飛出螢幕
        cursorX = (cursorX + dx / 100f).coerceIn(0f, 1f)
        cursorY = (cursorY + dy / 100f).coerceIn(0f, 1f)

        // 【區域判定】：檢查目前座標落在哪個選項的感應區內
        selectedOption = when {
            cursorY < GameConfig.BOUND_OPTION_A_BOTTOM -> "A"
            cursorY > GameConfig.BOUND_OPTION_B_TOP -> "B"
            cursorX < GameConfig.BOUND_OPTION_C_RIGHT -> "C"
            cursorX > GameConfig.BOUND_OPTION_D_LEFT -> "D"
            else -> ""
        }
    }

    private fun calculatePush(angle: Float, thres: Float): Float = when {
        angle > thres -> angle - thres
        angle < -thres -> angle + thres
        else -> 0f
    }

    fun calibrateCenter() {
        offsetPitch = Math.toDegrees(angleX.toDouble()).toFloat()
        offsetRoll = Math.toDegrees(angleY.toDouble()).toFloat()
        cursorX = 0.5f
        cursorY = 0.5f
        selectedOption = ""
    }

    // --- 答題邏輯 ---
    fun submitAnswer(answerCode: String) {
        if (isShowingFeedback() || isLocked()) return

        if (answerCode == currentQuestion.correctAnswerCode) {
            score += 10
            correctCount++
            triggerFeedback(FeedbackType.CORRECT, 1500)
            goToNextQuestion()
        } else {
            score = (score - 5).coerceAtLeast(0)
            wrongCount++
            triggerFeedback(FeedbackType.WRONG, 1500)

            // 處發 5 秒懲罰
            lockUntilTimestamp = System.currentTimeMillis() + 5000
            startLockTimer() // 立即啟動 UI 觀測計時器
        }

        cursorX = 0.5f
        cursorY = 0.5f
        selectedOption = ""
        persist()
    }

    private fun triggerFeedback(type: FeedbackType, duration: Long) {
        feedback = type
        feedbackUntilTimestamp = System.currentTimeMillis() + duration
    }

    private fun goToNextQuestion() {
        if (currentIndexInShuffled < shuffledIndices.size - 1) {
            currentIndexInShuffled++
        } else {
            shuffledIndices = QuizRepository.questions.indices.shuffled()
            currentIndexInShuffled = 0
        }
        persist()
    }
}