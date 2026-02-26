package com.example.gyroscopetest

import android.content.Context
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

    // --- 1. 使用 ViewModel (MVVM 核心) ---
    private val viewModel: GameViewModel by viewModels()

    // --- 2. 硬體變數 ---
    private lateinit var sensorManager: SensorManager
    private var gyroSensor: Sensor? = null
    private var timestamp: Long = 0

    // --- 3. 實時感測狀態 (僅在 Activity 內運作) ---
    private var angleX by mutableStateOf(0f)
    private var angleY by mutableStateOf(0f)
    private var offsetPitch by mutableStateOf(0f)
    private var offsetRoll by mutableStateOf(0f)
    private var cursorX by mutableStateOf(0.5f)
    private var cursorY by mutableStateOf(0.5f)
    private var selectedOption by mutableStateOf("")

    private val moveSensitivity = 4.0f
    private val angleThreshold = 20f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        setContent {
            // 計算目前相對於校正點的角度
            val degX = Math.toDegrees(angleX.toDouble()).toFloat()
            val degY = Math.toDegrees(angleY.toDouble()).toFloat()

            AngleTestScreen(
                cursorX = cursorX,
                cursorY = cursorY,
                selectedOption = selectedOption,
                viewModel = viewModel,
                onCalibrate = {
                    offsetPitch = degX
                    offsetRoll = degY
                    cursorX = 0.5f
                    cursorY = 0.5f
                }
            )
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_GYROSCOPE) return
        if (timestamp != 0L) {
            val dt = (event.timestamp - timestamp) * 1.0e-9f
            angleX += event.values[0] * dt
            angleY += event.values[1] * dt

            val relPitch = Math.toDegrees(angleX.toDouble()).toFloat() - offsetPitch
            val relRoll = Math.toDegrees(angleY.toDouble()).toFloat() - offsetRoll

            val dx = calculatePush(relRoll, angleThreshold) * moveSensitivity * dt
            val dy = calculatePush(relPitch, angleThreshold) * moveSensitivity * dt

            cursorX = (cursorX + dx / 100f).coerceIn(0f, 1f)
            cursorY = (cursorY + dy / 100f).coerceIn(0f, 1f)

            selectedOption = when {
                cursorY < 0.15f -> "A"
                cursorY > 0.85f -> "B"
                cursorX < 0.20f -> "C"
                cursorX > 0.80f -> "D"
                else -> ""
            }
        }
        timestamp = event.timestamp
    }

    private fun calculatePush(angle: Float, thres: Float): Float = when {
        angle > thres -> angle - thres
        angle < -thres -> angle + thres
        else -> 0f
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

/**
 * UI 層：純粹根據數據進行繪製
 */
@Composable
fun AngleTestScreen(
    cursorX: Float,
    cursorY: Float,
    selectedOption: String,
    viewModel: GameViewModel,
    onCalibrate: () -> Unit
) {
    var screenSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    var progress by remember { mutableStateOf(0f) }

    // 取得 ViewModel 中的狀態
    val currentQuestion = viewModel.currentQuestion
    val isLocked = viewModel.isLocked()
    val isShowingFeedback = viewModel.isShowingFeedback()

    // 背景顏色邏輯
    val bgColor = when (viewModel.feedback) {
        FeedbackType.CORRECT -> Color(0xFFE8F5E9)
        FeedbackType.WRONG -> Color(0xFFFFEBEE)
        else -> if (isLocked) Color(0xFFFFEBEE) else Color.White
    }

    // 答題計時邏輯 (Side Effect)
    LaunchedEffect(selectedOption, isLocked) {
        if (selectedOption == "" || isLocked) {
            progress = 0f
            return@LaunchedEffect
        }
        while (selectedOption != "" && !isLocked) {
            val duration = 2000L
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < duration) {
                if (selectedOption == "" || isLocked) {
                    progress = 0f
                    return@LaunchedEffect
                }
                progress = (System.currentTimeMillis() - start).toFloat() / duration
                delay(16)
            }
            viewModel.submitAnswer(selectedOption)
            progress = 0f
            delay(2000L) // 答對/答錯後的冷卻
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(bgColor).onGloballyPositioned { screenSize = it.size }) {

        // --- 選項渲染 (比例適配) ---
        AnswerBox(Modifier.fillMaxWidth().fillMaxHeight(0.15f).align(Alignment.TopCenter), "A: ${currentQuestion.options[0]}", selectedOption == "A")
        AnswerBox(Modifier.fillMaxWidth().fillMaxHeight(0.15f).align(Alignment.BottomCenter), "B: ${currentQuestion.options[1]}", selectedOption == "B")
        AnswerBox(Modifier.fillMaxHeight().fillMaxWidth(0.20f).align(Alignment.CenterStart), "C:\n${currentQuestion.options[2]}", selectedOption == "C")
        AnswerBox(Modifier.fillMaxHeight().fillMaxWidth(0.20f).align(Alignment.CenterEnd), "D:\n${currentQuestion.options[3]}", selectedOption == "D")

        // --- 中央資訊 ---
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

            if (isLocked) {
                Spacer(Modifier.height(10.dp))
                Text("鎖定中 (${viewModel.getLockRemainingSeconds()}s)", color = Color.Red, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(20.dp))
            Button(onClick = onCalibrate) { Text("校正中心點") }
        }

        // --- 游標渲染 ---
        if (screenSize.width > 0) {
            val animProgress by animateFloatAsState(progress)
            with(density) {
                Box(
                    Modifier.offset((cursorX * screenSize.width).toDp() - 12.5.dp, (cursorY * screenSize.height).toDp() - 12.5.dp).size(25.dp),
                    contentAlignment = Alignment.Center
                ) {
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