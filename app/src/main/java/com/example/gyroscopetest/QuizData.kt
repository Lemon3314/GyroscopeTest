package com.example.gyroscopetest

// 題目資料結構
data class Question(
    val id: Int,
    val text: String,
    val options: List<String>,
    val correctAnswerCode: String // 存儲 "A", "B", "C" 或 "D"
)

// 靜態題庫
object QuizRepository {
    val questions = listOf(
        Question(1, "Android 官方推薦的 UI 框架為何？", listOf("XML", "Compose", "Flutter", "React"), "B"),
        Question(2, "陀螺儀感測器回傳的數值單位為何？", listOf("m/s²", "Lux", "rad/s", "cm"), "C"),
        Question(3, "哪種組件負責提供應用程式間的資料共享？", listOf("Activity", "Service", "Provider", "Receiver"), "C"),
        Question(4, "開發 Android 應用的主要語言是？", listOf("Swift", "Kotlin", "Go", "C#"), "B"),
        Question(5, "請選A", listOf("A", "B", "C", "D"), "A"),
        Question(6, "請選B", listOf("A", "B", "C", "D"), "B"),
        Question(7, "請選C", listOf("A", "B", "C", "D"), "C"),
        Question(8, "請選D", listOf("A", "B", "C", "D"), "D"),
        Question(9, "蘋果的英文", listOf("Apple", "Banana", "Coconut", "Dragonfruit"), "A"),
        Question(10, "30/2(3+3*4)", listOf("1", "30", "15", "225"), "D"),
    )
}