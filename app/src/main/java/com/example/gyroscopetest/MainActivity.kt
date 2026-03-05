package com.example.gyroscopetest

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels



// 【Step 1】 實作 SensorEventListener 介面
// 寫下類別定義時，Android Studio 會提示要實作成員，這時順便讓 IDE 自動幫你產生 onSensorChanged 和 onAccuracyChanged
class MainActivity : ComponentActivity(), SensorEventListener {

    // 【Step 2】 宣告核心變數
    // 這些是你要控制感測器與連結 ViewModel 的必要工具
    private val viewModel: GameViewModel by viewModels()
    private lateinit var sensorManager: SensorManager
    private var gyroSensor: Sensor? = null
    private var timestamp: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 【Step 3】 初始化感測器與掛載 UI
        // 先讓畫面能跑起來，這時候紅點還不會動是正常的
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        setContent {
            AngleTestScreen(viewModel = viewModel)
        }
    }

    // 【Step 4】 生命週期管控
    // 寫完 onCreate 後，先寫這兩個。確保你的感測器有「開關」，這對節省手機電量非常重要
    override fun onResume() {
        super.onResume()
        // 開始監聽：設定頻率為 SENSOR_DELAY_GAME（適合遊戲的高頻率）
        gyroSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    override fun onPause() {
        super.onPause()
        // 停止監聽：APP 跳到後台時立刻關閉，否則會極度耗電
        sensorManager.unregisterListener(this)
    }

    // 介面要求必須存在，通常不寫內容
    // 不寫會報錯
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    // 【Step 5】 實作數據傳輸邏輯
    // 這是最後一步，將硬體數值餵給 ViewModel。放在最後是因為它最容易出數學計算錯誤
    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_GYROSCOPE) return

        // 判斷場景：只在遊戲中消耗資源
        if (viewModel.currentScene == GameScene.PLAYING) {
            if (timestamp != 0L) {
                // 微積分時間差：dt = (T2 - T1) / 1,000,000,000
                val dt = (event.timestamp - timestamp) * 1.0e-9f

                // 傳遞給 ViewModel 進行物理運算
                viewModel.processSensorData(event.values[0], event.values[1], dt)
            }
        }
        timestamp = event.timestamp
    }


}