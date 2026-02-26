package com.example.gyroscopetest

import androidx.compose.runtime.*
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

// 回饋類型定義
enum class FeedbackType { NONE, CORRECT, WRONG }

class GameViewModel(private val savedState: SavedStateHandle) : ViewModel() {

    // --- 狀態持久化 (SavedStateHandle) ---
    // 使用 SavedStateHandle 確保 App 被系統殺掉後仍能恢復數據
    var score by mutableIntStateOf(savedState["score"] ?: 0)
    var correctCount by mutableIntStateOf(savedState["correctCount"] ?: 0)
    var wrongCount by mutableIntStateOf(savedState["wrongCount"] ?: 0)

    // 題目順序持久化
    private var shuffledIndices: List<Int> = savedState.get<List<Int>>("indices")
        ?: QuizRepository.questions.indices.shuffled()

    var currentIndexInShuffled by mutableIntStateOf(savedState["currentIndex"] ?: 0)

    // --- 臨時視覺狀態 (不需要持久化) ---
    var feedback by mutableStateOf(FeedbackType.NONE)
    private var feedbackUntilTimestamp by mutableLongStateOf(0L)

    // 鎖定狀態持久化 (防止玩家透過重開 App 規避懲罰)
    private var lockUntilTimestamp by mutableLongStateOf(savedState["lockUntil"] ?: 0L)

    // 取得當前題目邏輯
    val currentQuestion: Question
        get() = QuizRepository.questions[shuffledIndices[currentIndexInShuffled]]

    /**
     * 持久化存檔：每次重要數據變動時呼叫
     */
    private fun persist() {
        savedState["score"] = score
        savedState["correctCount"] = correctCount
        savedState["wrongCount"] = wrongCount
        savedState["indices"] = shuffledIndices
        savedState["currentIndex"] = currentIndexInShuffled
        savedState["lockUntil"] = lockUntilTimestamp
    }

    // --- 業務邏輯方法 ---

    fun isShowingFeedback(): Boolean {
        val active = System.currentTimeMillis() < feedbackUntilTimestamp
        if (!active) feedback = FeedbackType.NONE
        return active
    }

    fun isLocked(): Boolean = System.currentTimeMillis() < lockUntilTimestamp

    fun getLockRemainingSeconds(): Int {
        val remaining = lockUntilTimestamp - System.currentTimeMillis()
        return if (remaining > 0) (remaining / 1000).toInt() + 1 else 0
    }

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
            lockUntilTimestamp = System.currentTimeMillis() + 5000 // 5秒懲罰
        }
        persist() // 存檔
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