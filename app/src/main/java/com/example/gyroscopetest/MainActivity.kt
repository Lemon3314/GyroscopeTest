package com.example.gyroscopetest

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var gyroSensor: Sensor? = null

    private var angleX by mutableStateOf(0f)
    private var angleY by mutableStateOf(0f)
    private var timestamp: Long = 0

    private var offsetPitch by mutableStateOf(0f)
    private var offsetRoll by mutableStateOf(0f)

    private var cursorX by mutableStateOf(0.5f)
    private var cursorY by mutableStateOf(0.5f)

    private val moveSensitivity = 4.0f // 配合死區，靈敏度調高一點
    private val angleThreshold = 20f

    // 當前選中的答案狀態
    private var selectedOption by mutableStateOf("請傾斜手機進行答題")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        setContent {
            val degX = Math.toDegrees(angleX.toDouble()).toFloat()
            val degY = Math.toDegrees(angleY.toDouble()).toFloat()
            val relativePitch = degX - offsetPitch
            val relativeRoll = degY - offsetRoll

            AngleTestScreen(
                relativePitch = relativePitch.toInt(),
                relativeRoll = relativeRoll.toInt(),
                cursorX = cursorX,
                cursorY = cursorY,
                selectedOption = selectedOption,
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

            val effectiveRoll = calculateEffectivePush(curRelRoll, angleThreshold)
            val effectivePitch = calculateEffectivePush(curRelPitch, angleThreshold)

            val dx = effectiveRoll * moveSensitivity * dt
            val dy = effectivePitch * moveSensitivity * dt

            cursorX = (cursorX + dx / 100f).coerceIn(0f, 1f)
            cursorY = (cursorY + dy / 100f).coerceIn(0f, 1f)

            // --- 碰撞偵測邏輯 ---
            selectedOption = when {
                cursorY < 0.15f -> "選項 A (上方)"
                cursorY > 0.85f -> "選項 B (下方)"
                cursorX < 0.20f -> "選項 C (左方)"
                cursorX > 0.80f -> "選項 D (右方)"
                else -> "請移動游標至答案區"
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
    onCalibrate: () -> Unit
) {
    var screenSize by remember { mutableStateOf(IntSize.Zero) }
    val activeColor = Color(0xFF4CAF50)
    val inactiveColor = Color(0xFFE0E0E0).copy(alpha = 0.3f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .onGloballyPositioned { screenSize = it.size }
    ) {
        // --- 1. 四週答題感應區 (加大左右區塊) ---
        // 上 (A)
        AnswerBox(
            modifier = Modifier.fillMaxWidth().height(100.dp).align(Alignment.TopCenter),
            text = "選項 A",
            isActive = cursorY < 0.15f,
            activeColor = activeColor,
            inactiveColor = inactiveColor
        )
        // 下 (B)
        AnswerBox(
            modifier = Modifier.fillMaxWidth().height(100.dp).align(Alignment.BottomCenter),
            text = "選項 B",
            isActive = cursorY > 0.85f,
            activeColor = activeColor,
            inactiveColor = inactiveColor
        )
        // 左 (C) - 加大寬度
        AnswerBox(
            modifier = Modifier.fillMaxHeight().width(90.dp).align(Alignment.CenterStart),
            text = "選項\nC",
            isActive = cursorX < 0.20f,
            activeColor = activeColor,
            inactiveColor = inactiveColor
        )
        // 右 (D) - 加大寬度
        AnswerBox(
            modifier = Modifier.fillMaxHeight().width(90.dp).align(Alignment.CenterEnd),
            text = "選項\nD",
            isActive = cursorX > 0.80f,
            activeColor = activeColor,
            inactiveColor = inactiveColor
        )

        // --- 2. 中央題目顯示區 ---
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 100.dp), // 避開左右 Box
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "題目：",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 28.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 顯示目前游標選中的狀態
            Box(
                modifier = Modifier
                    .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = selectedOption,
                    color = if (selectedOption.contains("選項")) Color(0xFF2E7D32) else Color.Gray,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            Button(onClick = onCalibrate) {
                Text("校正並重置游標")
            }

            Text(
                text = "角度: ${relativePitch}° / ${relativeRoll}°",
                fontSize = 12.sp,
                color = Color.LightGray,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // --- 3. 游標 (小白點) ---
        // 使用相對座標計算在螢幕上的偏移
        val cursorSize = 30.dp
        Box(
            modifier = Modifier
                .offset(
                    x = (cursorX * (screenSize.width / 2.75f)).dp,
                    y = (cursorY * (screenSize.height / 2.75f)).dp
                )
                .size(cursorSize)
                .background(Color(0xFFE91E63), CircleShape)
                .border(2.dp, Color.White, CircleShape)
        )
    }
}

@Composable
fun AnswerBox(
    modifier: Modifier,
    text: String,
    isActive: Boolean,
    activeColor: Color,
    inactiveColor: Color
) {
    Box(
        modifier = modifier
            .background(if (isActive) activeColor else inactiveColor)
            .border(1.dp, Color.LightGray.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (isActive) Color.White else Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun GyroPreview() {

    var offsetRoll = 0
    var offsetPitch = 0
    var cursorX = 0.5f
    var cursorY = 0.5f
    var selectedOption = "Text"
    AngleTestScreen(
        relativePitch = 0,
        relativeRoll = 0,
        cursorX = cursorX,
        cursorY = cursorY,
        selectedOption = selectedOption,
        onCalibrate = {
            offsetPitch = 0
            offsetRoll = 0
            cursorX = 0.5f
            cursorY = 0.5f
        }
    )
}