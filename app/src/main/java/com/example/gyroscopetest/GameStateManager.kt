package com.example.gyroscopetest

import androidx.compose.runtime.*

// 定義回饋類型
enum class FeedbackType { NONE, CORRECT, WRONG }

class GameStateManager {
    // 1. 題目隨機化邏輯
    private var shuffledIndices = QuizRepository.questions.indices.shuffled()
    private var currentIndexInShuffled by mutableIntStateOf(0)

    // 遊戲數據
    var score by mutableIntStateOf(0)
    var correctCount by mutableIntStateOf(0)
    var wrongCount by mutableIntStateOf(0)

    // 2. 狀態管理：回饋與鎖定
    var feedback by mutableStateOf(FeedbackType.NONE) // 控制畫面顏色與文字
    private var feedbackUntilTimestamp by mutableLongStateOf(0L) // 回饋顯示截止時間
    private var lockUntilTimestamp by mutableLongStateOf(0L)     // 答錯鎖定截止時間

    // 取得當前題目
    val currentQuestion: Question
        get() = QuizRepository.questions[shuffledIndices[currentIndexInShuffled]]

    // 檢查是否處於「回饋顯示中」
    fun isShowingFeedback(): Boolean {
        val active = System.currentTimeMillis() < feedbackUntilTimestamp
        if (!active) feedback = FeedbackType.NONE // 時間到自動重設回饋類型
        return active
    }

    // 檢查是否處於「答錯鎖定」狀態 (維持原本的 3 秒邏輯)
    fun isLocked(): Boolean {
        return System.currentTimeMillis() < lockUntilTimestamp
    }

    // 取得剩餘鎖定秒數 (UI 顯示用)
    fun getLockRemainingSeconds(): Int {
        val remaining = lockUntilTimestamp - System.currentTimeMillis()
        return if (remaining > 0) (remaining / 1000).toInt() + 1 else 0
    }

    /**
     * 提交答案
     * @param answerCode "A", "B", "C" 或 "D"
     */
    fun submitAnswer(answerCode: String) {
        // 如果正在顯示回饋或處於鎖定狀態，則不接受新答案
        if (isShowingFeedback() || isLocked()) return

        if (answerCode == currentQuestion.correctAnswerCode) {
            // --- 答對邏輯 ---
            score += 10
            correctCount++
            triggerFeedback(FeedbackType.CORRECT, 1500) // 顯示綠色回饋 1.5 秒
            goToNextQuestion()
        } else {
            // --- 答錯邏輯 ---
            score = (score - 5).coerceAtLeast(0)
            wrongCount++
            triggerFeedback(FeedbackType.WRONG, 1500)    // 顯示紅色回饋 1.5 秒
            lockUntilTimestamp = System.currentTimeMillis() + 5000 // 維持原本 5 秒鎖定
        }
    }

    // 觸發視覺回饋
    private fun triggerFeedback(type: FeedbackType, duration: Long) {
        feedback = type
        feedbackUntilTimestamp = System.currentTimeMillis() + duration
    }

    private fun goToNextQuestion() {
        if (currentIndexInShuffled < shuffledIndices.size - 1) {
            currentIndexInShuffled++
        } else {
            resetQuizOrder()
        }
    }

    private fun resetQuizOrder() {
        shuffledIndices = QuizRepository.questions.indices.shuffled()
        currentIndexInShuffled = 0
    }
}