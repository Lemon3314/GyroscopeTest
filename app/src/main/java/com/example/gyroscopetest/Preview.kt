package com.example.gyroscopetest

import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.getValue

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun GyroPreview() {
    //val gameState = GameStateManager()
    var offsetRoll = 0
    var offsetPitch = 0
    var cursorX = 0.5f
    var cursorY = 0.5f
    var selectedOption = "Text"
    var degX = 0
    var degY =0
    val viewModel: GameViewModel = viewModel()

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