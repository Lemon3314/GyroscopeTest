package com.example.gyroscopetest

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.delay

/**
 * 🎮 頂層場景切換器
 */
@Composable
fun AngleTestScreen(viewModel: GameViewModel) {
    when (viewModel.currentScene) {
        GameScene.START -> StartScreen(onStart = { viewModel.startGame() })
        GameScene.PLAYING -> QuizPlayScreen(viewModel = viewModel)
        GameScene.RESULT -> ResultScreen(
            score = viewModel.score,
            correct = viewModel.correctCount,
            wrong = viewModel.wrongCount,
            onRestart = { viewModel.backToStart() }
        )
    }
}

/**
 * 🏠 1. 起始頁面：負責遊戲說明與引導
 */
@Composable
fun StartScreen(onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5)).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.gyroicon), // 這裡需確認專案內有此圖，或改用 Icon
            contentDescription = "Logo",
            modifier = Modifier.size(120.dp)
        )
        Text("GyroQuiz", fontSize = 40.sp, fontWeight = FontWeight.Black, color = Color(0xFF333333))
        Text("體感知識競賽", fontSize = 18.sp, color = Color.Gray)

        Spacer(modifier = Modifier.height(48.dp))

        // 規則卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp)) {
                Text("遊戲規則：", fontWeight = FontWeight.Bold, color = Color.Black)
                Text("• 傾斜手機控制紅點移動。", color = Color.DarkGray)
                Text("• 將紅點停留在選項上 2 秒作答。", color = Color.DarkGray)
                Text("• 答錯將扣分並鎖定移動 5 秒。", color = Color.DarkGray)
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF66C2EE))
        ) {
            Text("開始挑戰", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * 🎯 2. 遊戲主畫面：核心體感互動區
 */
@Composable
fun QuizPlayScreen(viewModel: GameViewModel) {
    var screenSize by remember { mutableStateOf(IntSize.Zero) } //獲取螢幕大小
    var progress by remember { mutableFloatStateOf(0f) }

    // 【邏輯：長按判定】當停留區域改變時重啟
    //輔助進度增加
    LaunchedEffect(viewModel.selectedOption, viewModel.isLocked()) {
        if (viewModel.selectedOption == "" || viewModel.isLocked()) {
            progress = 0f
            return@LaunchedEffect
        }

        while (viewModel.selectedOption != "" && !viewModel.isLocked()) {
            val duration = 2000L
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < duration) {
                if (viewModel.selectedOption == "") {
                    progress = 0f
                    return@LaunchedEffect
                }
                progress = (System.currentTimeMillis() - start).toFloat() / duration
                delay(16)
            }
            // 觸發答題後，ViewModel 會自動將游標 reset，selectedOption 變 ""，
            // 從而終止此 LaunchedEffect 的執行，不再需要額外 delay。
            viewModel.submitAnswer(viewModel.selectedOption)
            progress = 0f
        }
    }

    // 【修改】依據狀態決定遊戲區"底層"背景色
    // 正確/錯誤的綠色紅色已經交給遮罩了，這裡只保留鎖定中的淺紅警告色
    val bgColor = when {
        viewModel.isLocked() -> Color(0xFFFFEBEE) // 錯誤紅 (鎖定殘留)
        else -> Color.White
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .onGloballyPositioned { screenSize = it.size } // 獲取螢幕實際尺寸
    ) {
        // --- 選項區域佈局 (A, B, C, D) ---
        AnswerBox(Modifier
            .fillMaxWidth()
            .fillMaxHeight(GameConfig.BOUND_OPTION_A_BOTTOM)
            .align(Alignment.TopCenter),
            "A: ${viewModel.currentQuestion.options[0]}",
            viewModel.selectedOption == "A"
        )

        AnswerBox(Modifier
            .fillMaxWidth()
            .fillMaxHeight(1f - GameConfig.BOUND_OPTION_B_TOP)
            .align(Alignment.BottomCenter),
            "B: ${viewModel.currentQuestion.options[1]}",
            viewModel.selectedOption == "B"
        )

        AnswerBox(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(GameConfig.BOUND_OPTION_C_RIGHT)
                .align(Alignment.CenterStart),
            "C:\n${viewModel.currentQuestion.options[2]}",
            viewModel.selectedOption == "C"
        )

        AnswerBox(Modifier
            .fillMaxHeight()
            .fillMaxWidth(1f - GameConfig.BOUND_OPTION_D_LEFT)
            .align(Alignment.CenterEnd),
            "D:\n${viewModel.currentQuestion.options[3]}",
            viewModel.selectedOption == "D"
        )

        // --- 中間資訊區：分數、題目、狀態回饋 ---
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 95.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("得分: ${viewModel.score} | 對: ${viewModel.correctCount} 錯: ${viewModel.wrongCount}", fontSize = 14.sp, color = Color.Gray)

            // 【修改】原「正確/錯誤 動態回饋文字」已移至全螢幕遮罩，保留 Spacer 維持排版高度
            Spacer(Modifier.height(60.dp))

            Text("Q: ${viewModel.currentQuestion.text}", fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)

            // 鎖定狀態顯示
            if (viewModel.lockRemainingSeconds > 0) {
                Spacer(Modifier.height(8.dp))
                Text("鎖定中 (${viewModel.lockRemainingSeconds}s)", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }

            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = { viewModel.calibrateCenter() }) { Text("校正中心點") }
        }

        // --- 呼叫抽離出的體感游標 ---
        GyroCursor(
            cursorX = viewModel.cursorX,
            cursorY = viewModel.cursorY,
            screenSize = screenSize,
            progress = progress
        )

        // --- 呼叫抽離出的全螢幕遮罩層 ---
        val isLastQuestion = viewModel.currentIndexInShuffled == GameConfig.MAX_QUESTIONS - 1
        QuizFeedbackOverlay(
            feedback = viewModel.feedback,
            isLast = isLastQuestion
        )
    }
}
// --- 新增：全螢幕遮罩層 Composable ---
@Composable
fun QuizFeedbackOverlay(
    feedback: FeedbackType,
    isLast: Boolean
) {
    // --- 🌟 全螢幕遮罩層 (Overlay) 🌟 ---
    // 必須放在 Box 內部結構的最下方，才能覆蓋在所有 UI 元件的最上層
    if (feedback != FeedbackType.NONE) {
        // 設定 90% 不透明度 (0xEE)，讓玩家隱約看到底層已經切換好的新題目
        val overlayColor = if (feedback == FeedbackType.CORRECT) {
            Color(0x804CAF50) // 綠色
        } else {
            Color(0x80DB4437) // 紅色
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(overlayColor)
                // 攔截所有觸控事件，防止玩家在遮罩期間亂點按鈕
                .clickable(enabled = false) {},
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (feedback == FeedbackType.CORRECT) "✨ 答對了！" else "❌ 答錯了！",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isLast) "準備結算..." else "準備下一題...",
                    fontSize = 20.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

// --- 新增：體感游標 Composable ---
@Composable
fun GyroCursor(
    cursorX: Float,
    cursorY: Float,
    screenSize: IntSize,
    progress: Float
) {
    val density = LocalDensity.current //取得空間翻譯官(關鍵的單位轉換區塊.toDp()

    // --- 核心：體感游標繪製 ---
    if (screenSize.width > 0) {
        val animProgress by animateFloatAsState(progress) // 讓進度環轉動更平滑
        with(density) {
            Box(
                Modifier
                    .offset(
                        // 【百分比轉像素公式】
                        x = (cursorX * screenSize.width).toDp() - 12.5.dp,
                        y = (cursorY * screenSize.height).toDp() - 12.5.dp
                    )
                    //Box(球)的大小
                    .size(25.dp),
                contentAlignment = Alignment.Center
            ) {
                // 停留時顯示的 "進度圓圈"
                if (progress > 0f) {
                    CircularProgressIndicator(
                        progress = { animProgress },
                        modifier = Modifier.requiredSize(46.dp),
                        color = Color(0xFFE91E63),
                        strokeWidth = 4.dp
                    )
                }
                // "游標紅點本體"
                Box(Modifier.fillMaxSize().background(Color(0xFFE91E63), CircleShape).border(2.dp, Color.White, CircleShape))
            }
        }
    }
}

/**
 * 🏆 3. 結算頁面：展示最終成績
 */
@Composable
fun ResultScreen(score: Int, correct: Int, wrong: Int, onRestart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFE8F5E9)).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("挑戰結束！", fontSize = 32.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(16.dp))

        Text("最終得分", fontSize = 18.sp, color = Color.Gray)
        Text("$score", fontSize = 80.sp, fontWeight = FontWeight.Black, color = Color(0xFF2E7D32))

        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("答對", color = Color.Gray)
                Text("$correct", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("答錯", color = Color.Gray)
                Text("$wrong", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
            }
        }

        Spacer(modifier = Modifier.height(64.dp))

        Button(onClick = onRestart, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("返回主選單", fontSize = 18.sp)
        }
    }
}

/**
 * 📦 組件：選項框
 */
@Composable
fun AnswerBox(modifier: Modifier, text: String, isActive: Boolean) {
    val color = if (isActive) Color(0xFF4CAF50) else Color(0xFFE0E0E0).copy(0.3f)
    Box(
        modifier = modifier
            .background(color)
            .border(1.dp, Color.LightGray.copy(0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            color = if (isActive) Color.White else Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(8.dp)
        )
    }
}


@Preview
@Composable
fun Test() {
    //AnswerBox(modifier = Modifier.padding(16.dp).fillMaxWidth(),"測試",true)
    //ResultScreen(0,0, 0, onRestart = {})
    //StartScreen {  }
    //QuizPlayScreen(viewModel = GameViewModel(SavedStateHandle()))
}