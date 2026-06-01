package com.example.hci_demo // 确保包名与你创建项目时一致

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                // 模拟底层 App 背景（多场景视图可以在此切换）
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5))) {
                    Column(Modifier.padding(20.dp)) {
                        Text("TaskFlow 仿真环境", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    }

                    // 核心悬浮组件
                    TaskFlowFloatingWidget()
                }
            }
        }
    }
}

// 悬浮窗形态枚举（由成员 C 的逻辑引擎驱动）
enum class WidgetState {
    CAPSULE, EXPANDED
}

@Composable
fun TaskFlowFloatingWidget() {
    // --- 状态管理 ---
    var currentState by remember { mutableStateOf(WidgetState.CAPSULE) }
    var offsetX by remember { mutableStateOf(20f) }
    var offsetY by remember { mutableStateOf(100f) }

    // --- 成员 B 关注：平滑尺寸动画 ---
    val width by animateDpAsState(
        targetValue = if (currentState == WidgetState.CAPSULE) 160.dp else 280.dp,
        animationSpec = spring(dampingRatio = 0.75f)
    )
    val height by animateDpAsState(
        targetValue = if (currentState == WidgetState.CAPSULE) 48.dp else 180.dp,
        animationSpec = spring(dampingRatio = 0.75f)
    )

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .shadow(12.dp, RoundedCornerShape(24.dp))
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(24.dp))
            // 成员 A 关注：毛玻璃与渐变视觉
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF42A5F5), Color(0xFF1E88E5))
                )
            )
            .pointerInput(Unit) {
                // --- 成员 B 关注：手势拖拽逻辑 ---
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                }
            }
            .clickable {
                // 点击切换形态（渐进式披露原则）
                currentState = if (currentState == WidgetState.CAPSULE)
                    WidgetState.EXPANDED else WidgetState.CAPSULE
            }
            .padding(12.dp)
    ) {
        // --- 成员 A & C 关注：内容渲染 ---
        AnimatedContent(
            targetState = currentState,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            }
        ) { state ->
            if (state == WidgetState.CAPSULE) {
                // 胶囊态：仅显示核心倒计时
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Badge(containerColor = Color.White.copy(alpha = 0.3f)) {
                        Text("15 min", color = Color.White)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("用户交互技术", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            } else {
                // 展开态：显示完整课程路径
                Column {
                    Text("下一节课", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    Text("用户交互技术 (HCI)", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Divider(Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.2f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text(" 信工楼 402", color = Color.White, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { /* 模拟跳转地图 */ },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("展示其他信息", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}