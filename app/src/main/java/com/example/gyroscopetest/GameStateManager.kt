package com.example.gyroscopetest

import androidx.compose.runtime.*

class GameStateManager {
    // 1. 建立一個存放打亂後索引的列表
    // 例如：如果題目有 4 題，原本是 [0, 1, 2, 3]，打亂後可能是 [2, 0, 3, 1]
    private var shuffledIndices = QuizRepository.questions.indices.shuffled()

    // 目前進行到打亂後的第幾個位置
    private var currentIndexInShuffled by mutableIntStateOf(0)

    // 遊戲數據
    var score by mutableIntStateOf(0)
    var correctCount by mutableIntStateOf(0)
    var wrongCount by mutableIntStateOf(0)

    // 懲罰狀態
    private var lockUntilTimestamp by mutableLongStateOf(0L)

    // 2. 取得當前題目：透過打亂後的索引去題庫拿題
    val currentQuestion: Question
        get() = QuizRepository.questions[shuffledIndices[currentIndexInShuffled]]

    fun isLocked(): Boolean {
        return System.currentTimeMillis() < lockUntilTimestamp
    }

    fun getLockRemainingSeconds(): Int {
        val remaining = lockUntilTimestamp - System.currentTimeMillis()
        return if (remaining > 0) (remaining / 1000).toInt() + 1 else 0
    }

    fun submitAnswer(answerCode: String) {
        if (isLocked()) return

        if (answerCode == currentQuestion.correctAnswerCode) {
            score += 10
            correctCount++
            goToNextQuestion()
        } else {
            score = (score - 5).coerceAtLeast(0)
            wrongCount++
            lockUntilTimestamp = System.currentTimeMillis() + 3000
        }
    }

    private fun goToNextQuestion() {
        // 3. 判斷是否還有下一題
        if (currentIndexInShuffled < shuffledIndices.size - 1) {
            currentIndexInShuffled++
        } else {
            // 如果全部題目都跑完了，重新打亂題庫，重新開始
            resetQuizOrder()
        }
    }

    // 4. 重新打亂題目的私有方法
    private fun resetQuizOrder() {
        shuffledIndices = QuizRepository.questions.indices.shuffled()
        currentIndexInShuffled = 0
    }
}