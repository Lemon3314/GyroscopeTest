package com.example.gyroscopetest

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity(), SensorEventListener {

    private val viewModel: GameViewModel by viewModels()
    private lateinit var sensorManager: SensorManager
    private var gyroSensor: Sensor? = null
    private var timestamp: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        setContent {
            AngleTestScreen(viewModel = viewModel)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_GYROSCOPE) return
        if (timestamp != 0L) {
            val dt = (event.timestamp - timestamp) * 1.0e-9f
            // 將感測器數據交由 ViewModel 處理運算
            viewModel.processSensorData(event.values[0], event.values[1], dt)
        }
        timestamp = event.timestamp
    }

    override fun onResume() {
        super.onResume()
        gyroSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
}

@Composable
fun AngleTestScreen(viewModel: GameViewModel) {
    // 從 ViewModel 讀取狀態
    var screenSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    var progress by remember { mutableFloatStateOf(0f) }
    val remainingSeconds = viewModel.lockRemainingSeconds // 這是觀測型數據
    val currentQuestion = viewModel.currentQuestion
    val isLocked = viewModel.isLocked()
    val isShowingFeedback = viewModel.isShowingFeedback()
    val selectedOption = viewModel.selectedOption

    val bgColor = when (viewModel.feedback) {
        FeedbackType.CORRECT -> Color(0xFFE8F5E9)
        FeedbackType.WRONG -> Color(0xFFFFEBEE)
        else -> if (isLocked) Color(0xFFFFEBEE) else Color.White
    }

    // --- [核心邏輯 C：長按判定] ---
    // LaunchedEffect 會在 selectedOption 改變時重啟
    LaunchedEffect(viewModel.selectedOption, viewModel.isLocked()) {
        // 如果沒選選項或被鎖定，歸零進度並退出
        if (viewModel.selectedOption == "" || viewModel.isLocked()) {
            progress = 0f
            return@LaunchedEffect
        }

        // 模擬「進度條填充」效果
        while (viewModel.selectedOption != "" && !viewModel.isLocked()) {
            val duration = 2000L // 需停留在區域內 2 秒
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < duration) {
                // 如果填充中途移出區域，立即停止
                if (viewModel.selectedOption == "") {
                    progress = 0f
                    return@LaunchedEffect
                }
                // 更新填充百分比 (0.0 ~ 1.0)
                progress = (System.currentTimeMillis() - start).toFloat() / duration
                delay(16) // 約 60 FPS
            }
            // 填充完成，提交答案
            viewModel.submitAnswer(viewModel.selectedOption)
            progress = 0f // 答完題歸零
            delay(2000L) // 答題後的暫停時間，讓玩家看清楚對錯
        }
    }

    // --- [核心邏輯 D：解析度適配] ---
    Box(modifier = Modifier.fillMaxSize().background(bgColor).onGloballyPositioned { screenSize = it.size }) {
        // --- 替換為 GameConfig 參數 ---
        // ... 選項佈局 ...
        AnswerBox(Modifier.fillMaxWidth().fillMaxHeight(GameConfig.BOUND_OPTION_A_BOTTOM).align(Alignment.TopCenter), "A: ${currentQuestion.options[0]}", selectedOption == "A")
        AnswerBox(Modifier.fillMaxWidth().fillMaxHeight(1f - GameConfig.BOUND_OPTION_B_TOP).align(Alignment.BottomCenter), "B: ${currentQuestion.options[1]}", selectedOption == "B")
        AnswerBox(Modifier.fillMaxHeight().fillMaxWidth(GameConfig.BOUND_OPTION_C_RIGHT).align(Alignment.CenterStart), "C:\n${currentQuestion.options[2]}", selectedOption == "C")
        AnswerBox(Modifier.fillMaxHeight().fillMaxWidth(1f - GameConfig.BOUND_OPTION_D_LEFT).align(Alignment.CenterEnd), "D:\n${currentQuestion.options[3]}", selectedOption == "D")

        Column(modifier = Modifier.align(Alignment.Center).padding(horizontal = 95.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("得分: ${viewModel.score} | 對: ${viewModel.correctCount} 錯: ${viewModel.wrongCount}", fontSize = 14.sp, color = Color.Gray)

            Box(Modifier.height(60.dp), contentAlignment = Alignment.Center) {
                if (isShowingFeedback) {
                    Text(
                        text = if (viewModel.feedback == FeedbackType.CORRECT) "✨ 恭喜答對！ ✨" else "❌ 答錯了！ ❌",
                        fontSize = 24.sp, fontWeight = FontWeight.Black,
                        color = if (viewModel.feedback == FeedbackType.CORRECT) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                }
            }

            Text("Q: ${currentQuestion.text}", fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            if (remainingSeconds > 0) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "鎖定中 (${remainingSeconds}s)",
                    color = Color.Red,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            Spacer(Modifier.height(20.dp))
            Button(onClick = { viewModel.calibrateCenter() }) { Text("校正中心點") }
        }

        if (screenSize.width > 0) {
            val animProgress by animateFloatAsState(progress)
            with(density) {
                Box(
                    Modifier.offset(
                        // 【關鍵公式】：百分比 * 螢幕總寬度 = 實際像素位置
                        // 再減去游標半徑 (12.5.dp)，讓游標中心點對準計算位置
                        x = (viewModel.cursorX * screenSize.width).toDp() - 12.5.dp,
                        y = (viewModel.cursorY * screenSize.height).toDp() - 12.5.dp).size(25.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // 游標與進度環渲染...
                    if (progress > 0f) {
                        CircularProgressIndicator(progress = { animProgress }, modifier = Modifier.requiredSize(46.dp), color = Color(0xFFE91E63), strokeWidth = 4.dp)
                    }
                    Box(Modifier.fillMaxSize().background(Color(0xFFE91E63), CircleShape).border(2.dp, Color.White, CircleShape))
                }
            }
        }
    }
}

@Composable
fun AnswerBox(modifier: Modifier, text: String, isActive: Boolean) {
    val color = if (isActive) Color(0xFF4CAF50) else Color(0xFFE0E0E0).copy(0.3f)
    Box(modifier.background(color).border(1.dp, Color.LightGray.copy(0.3f)), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Black, color = if (isActive) Color.White else Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp))
    }
}