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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var gyroSensor: Sensor? = null

    // 陀螺儀狀態
    private var angleX by mutableStateOf(0f)
    private var angleY by mutableStateOf(0f)
    private var timestamp: Long = 0

    // 校正與游標
    private var offsetPitch by mutableStateOf(0f)
    private var offsetRoll by mutableStateOf(0f)
    private var cursorX by mutableStateOf(0.5f)
    private var cursorY by mutableStateOf(0.5f)

    private val moveSensitivity = 4.0f
    private val angleThreshold = 20f

    // 當前偵測到的選項 (邏輯判定)
    private var selectedOption by mutableStateOf("")

    // 實例化遊戲管理員
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

            cursorX = (cursorX + dx / 100f).coerceIn(0f, 1f)
            cursorY = (cursorY + dy / 100f).coerceIn(0f, 1f)

            // 碰撞偵測：鎖定邏輯
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

    private fun calculateEffectivePush(currentAngle: Float, threshold: Float): Float {
        return when {
            currentAngle > threshold -> currentAngle - threshold
            currentAngle < -threshold -> currentAngle + threshold
            else -> 0f
        }
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

    // --- GameState管理
    val currentQuestion = gameState.currentQuestion //目前問題
    val isLocked = gameState.isLocked() //是否被鎖定懲罰
    val feedback = gameState.feedback //當前的反饋
    val isShowingFeedback = gameState.isShowingFeedback() //是否正在反饋中

    // 動態背景色
    val backgroundColor = when (feedback) {
        FeedbackType.CORRECT -> Color(0xFFE8F5E9) // 答對：淡綠色
        FeedbackType.WRONG -> Color(0xFFFFEBEE)   // 答錯：淡紅色
        else -> Color.White
    }

    // --- 答題確認進度邏輯 ---
    var progress by remember { mutableStateOf(0f) }

    // 當 selectedOption 改變或被鎖定時，處理進度條
    LaunchedEffect(selectedOption, isLocked) {
        // 如果沒選中或被鎖定，歸零並退出
        if (selectedOption == "" || isLocked) {
            progress = 0f
            return@LaunchedEffect
        }

        // 使用循環達成連續觸發
        while (selectedOption != "" && !isLocked) {
            progress = 0f
            val duration = 2000L // 第一次觸發需要 2 秒
            val startTime = System.currentTimeMillis()

            // 進度條增加過程
            while (System.currentTimeMillis() - startTime < duration) {
                if (selectedOption == "" || isLocked) {
                    progress = 0f
                    return@LaunchedEffect
                }
                progress = (System.currentTimeMillis() - startTime).toFloat() / duration
                delay(16)
            }

            // 觸發答題
            gameState.submitAnswer(selectedOption)
            progress = 0f

            // --- 關鍵：連續觸發間隔 ---
            // 答完後，強制等待 1 秒（這段時間 progress 為 0）
            // 這讓使用者有時間看到「恭喜答對」並決定是否移開游標
            delay(2000L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isLocked) Color(0xFFFFEBEE) else Color.White)
            .onGloballyPositioned { screenSize = it.size }
    ) {
        // --- 1. 選項區塊 ---
        AnswerBox(Modifier.fillMaxWidth().height(100.dp).align(Alignment.TopCenter),
            "A: ${currentQuestion.options[0]}", selectedOption == "A")
        AnswerBox(Modifier.fillMaxWidth().height(100.dp).align(Alignment.BottomCenter),
            "B: ${currentQuestion.options[1]}", selectedOption == "B")
        AnswerBox(Modifier.fillMaxHeight().width(90.dp).align(Alignment.CenterStart),
            "C:\n${currentQuestion.options[2]}", selectedOption == "C")
        AnswerBox(Modifier.fillMaxHeight().width(90.dp).align(Alignment.CenterEnd),
            "D:\n${currentQuestion.options[3]}", selectedOption == "D")

        // --- 2. 中央資訊區 ---
        Column(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- A. 玩家狀態顯示 (最上方) ---
            Text("得分: ${gameState.score} | 對: ${gameState.correctCount} 錯: ${gameState.wrongCount}",
                fontSize = 14.sp, color = Color.Gray)

            Spacer(Modifier.height(16.dp))

            // --- B. 回饋文字預留區 (這部分就是你要求的位置) ---
            // 為了不讓畫面跳動，我們固定保留這個高度，或者使用 AnimatedVisibility
            Box(modifier = Modifier.height(60.dp), contentAlignment = Alignment.Center) {
                if (isShowingFeedback) {
                    Text(
                        text = if (feedback == FeedbackType.CORRECT) "✨ 恭喜答對！ ✨" else "❌ 答錯了！ ❌",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = if (feedback == FeedbackType.CORRECT) Color(0xFF2E7D32) else Color(0xFFC62828),
                        textAlign = TextAlign.Center
                    )
                }
            }
            // --- C. 題目文字 (始終顯示) ---
            Spacer(Modifier.height(10.dp))
            Text(text = "Q: ${currentQuestion.text}", fontSize = 22.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            // --- D. 鎖定狀態 (出現在題目下方) ---
            if (isLocked) {
                Spacer(Modifier.height(15.dp))
                Box(
                    modifier = Modifier
                        .background(Color.Red.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "答錯懲罰：鎖定中 (${gameState.getLockRemainingSeconds()}s)",
                        color = Color.Red,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }




            Spacer(Modifier.height(30.dp))
            Button(onClick = onCalibrate) { Text("校正中心點") }
        }

        // --- 3. 游標與進度環 ---
        val animatedProgress by animateFloatAsState(targetValue = progress)

        Box(
            modifier = Modifier
                .offset(
                    x = (cursorX * (screenSize.width / 2.75f)).dp,
                    y = (cursorY * (screenSize.height / 2.75f)).dp
                )
                .size(45.dp),
            contentAlignment = Alignment.Center
        ) {
            // 背景白點
            Box(modifier = Modifier.size(25.dp).background(Color(0xFFE91E63), CircleShape).border(2.dp, Color.White, CircleShape))

            // 進度環 (視覺回饋)
            if (progress > 0f) {
                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFE91E63),
                    strokeWidth = 4.dp,
                )
            }
        }
    }
}

@Composable
fun AnswerBox(modifier: Modifier, text: String, isActive: Boolean) {
    val backgroundColor = if (isActive) Color(0xFF4CAF50) else Color(0xFFE0E0E0).copy(alpha = 0.3f)
    Box(
        modifier = modifier.background(backgroundColor).border(1.dp, Color.LightGray.copy(0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.Black,
            color = if (isActive) Color.White else Color.Gray, textAlign = TextAlign.Center)
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun GyroPreview() {
    val gameState = GameStateManager()
    var offsetRoll = 0
    var offsetPitch = 0
    var cursorX = 0.5f
    var cursorY = 0.5f
    var selectedOption = "Text"
    var degX = 0
    var degY =0
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