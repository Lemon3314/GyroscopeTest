package com.example.gyroscopetest

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
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
    private lateinit var sensorManager: SensorManager
    private var gyroSensor: Sensor? = null

    private var angleX by mutableStateOf(0f)
    private var angleY by mutableStateOf(0f)
    private var timestamp: Long = 0

    private var offsetPitch by mutableStateOf(0f)
    private var offsetRoll by mutableStateOf(0f)

    // 游標位置：0.0f ~ 1.0f 之間，這是適配螢幕的關鍵
    private var cursorX by mutableStateOf(0.5f)
    private var cursorY by mutableStateOf(0.5f)

    private val moveSensitivity = 4.0f
    private val angleThreshold = 20f
    private var selectedOption by mutableStateOf("")
    private val gameState = GameStateManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        setContent {
            val degX = Math.toDegrees(angleX.toDouble()).toFloat()
            val degY = Math.toDegrees(angleY.toDouble()).toFloat()

            AngleTestScreen(
                relativePitch = (degX - offsetPitch).toInt(),
                relativeRoll = (degY - offsetRoll).toInt(),
                cursorX = cursorX,
                cursorY = cursorY,
                selectedOption = selectedOption,
                gameState = gameState,
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

            val curRelPitch = Math.toDegrees(angleX.toDouble()).toFloat() - offsetPitch
            val curRelRoll = Math.toDegrees(angleY.toDouble()).toFloat() - offsetRoll

            val dx = calculateEffectivePush(curRelRoll, angleThreshold) * moveSensitivity * dt
            val dy = calculateEffectivePush(curRelPitch, angleThreshold) * moveSensitivity * dt

            // 更新相對座標 (0~1)，這部分與解析度無關
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

    private fun calculateEffectivePush(currentAngle: Float, threshold: Float): Float = when {
        currentAngle > threshold -> currentAngle - threshold
        currentAngle < -threshold -> currentAngle + threshold
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

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

@Composable
fun AngleTestScreen(
    relativePitch: Int,
    relativeRoll: Int,
    cursorX: Float,
    cursorY: Float,
    selectedOption: String,
    gameState: GameStateManager,
    onCalibrate: () -> Unit
) {
    var screenSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    val currentQuestion = gameState.currentQuestion
    val isLocked = gameState.isLocked()
    val feedback = gameState.feedback
    val isShowingFeedback = gameState.isShowingFeedback()

    val backgroundColor = when (feedback) {
        FeedbackType.CORRECT -> Color(0xFFE8F5E9)
        FeedbackType.WRONG -> Color(0xFFFFEBEE)
        else -> if (isLocked) Color(0xFFFFEBEE) else Color.White
    }

    var progress by remember { mutableStateOf(0f) }

    LaunchedEffect(selectedOption, isLocked) {
        if (selectedOption == "" || isLocked) {
            progress = 0f
            return@LaunchedEffect
        }
        while (selectedOption != "" && !isLocked) {
            progress = 0f
            val duration = 2000L
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < duration) {
                if (selectedOption == "" || isLocked) {
                    progress = 0f
                    return@LaunchedEffect
                }
                progress = (System.currentTimeMillis() - startTime).toFloat() / duration
                delay(16)
            }
            gameState.submitAnswer(selectedOption)
            progress = 0f
            delay(2000L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .onGloballyPositioned { screenSize = it.size }
    ) {
        // --- 1. 選項區塊 (使用百分比高度避免小螢幕跑版) ---
        AnswerBox(Modifier.fillMaxWidth().fillMaxHeight(0.15f).align(Alignment.TopCenter),
            "A: ${currentQuestion.options[0]}", selectedOption == "A")

        AnswerBox(Modifier.fillMaxWidth().fillMaxHeight(0.15f).align(Alignment.BottomCenter),
            "B: ${currentQuestion.options[1]}", selectedOption == "B")

        AnswerBox(Modifier.fillMaxHeight().fillMaxWidth(0.20f).align(Alignment.CenterStart),
            "C:\n${currentQuestion.options[2]}", selectedOption == "C")

        AnswerBox(Modifier.fillMaxHeight().fillMaxWidth(0.20f).align(Alignment.CenterEnd),
            "D:\n${currentQuestion.options[3]}", selectedOption == "D")

        // --- 2. 中央資訊區 ---
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 95.dp), // 略大於側邊寬度避免文字重疊
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("得分: ${gameState.score} | 對: ${gameState.correctCount} 錯: ${gameState.wrongCount}",
                fontSize = 14.sp, color = Color.Gray)

            Box(modifier = Modifier.height(60.dp), contentAlignment = Alignment.Center) {
                if (isShowingFeedback) {
                    Text(
                        text = if (feedback == FeedbackType.CORRECT) "✨ 恭喜答對！ ✨" else "❌ 答錯了！ ❌",
                        fontSize = 24.sp, // 稍微調小一點確保相容性
                        fontWeight = FontWeight.Black,
                        color = if (feedback == FeedbackType.CORRECT) Color(0xFF2E7D32) else Color(0xFFC62828),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Text(text = "Q: ${currentQuestion.text}", fontSize = 20.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 26.sp)

            if (isLocked) {
                Spacer(Modifier.height(10.dp))
                Text(text = "答錯懲罰：鎖定中 (${gameState.getLockRemainingSeconds()}s)",
                    color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            Spacer(Modifier.height(20.dp))
            Button(onClick = onCalibrate) { Text("校正中心點") }
        }

        // --- 3. 游標與進度環 (適配解析度關鍵) ---
        if (screenSize.width > 0 && screenSize.height > 0) {
            val animatedProgress by animateFloatAsState(targetValue = progress)

            // 將座標比例轉換為對應螢幕的 DP
            val cursorXpx = cursorX * screenSize.width
            val cursorYpx = cursorY * screenSize.height

            with(density) {
                Box(
                    modifier = Modifier
                        .offset(
                            x = cursorXpx.toDp() - 12.5.dp, // 減去一半寬度讓中心對準座標
                            y = cursorYpx.toDp() - 12.5.dp
                        )
                        .size(25.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (progress > 0f) {
                        CircularProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.requiredSize(46.dp),
                            color = Color(0xFFE91E63),
                            strokeWidth = 4.dp,
                        )
                    }
                    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFE91E63), CircleShape).border(2.dp, Color.White, CircleShape))
                }
            }
        }
    }
}

@Composable
fun AnswerBox(modifier: Modifier, text: String, isActive: Boolean) {
    val backgroundColor = if (isActive) Color(0xFF4CAF50) else Color(0xFFE0E0E0).copy(alpha = 0.3f)
    Box(
        modifier = modifier.background(backgroundColor).border(1.dp, Color.LightGray.copy(0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.Black,
            color = if (isActive) Color.White else Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp))
    }
}