package com.example.hci_demo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.example.hci_demo.logic.FloatingService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                FloatingServiceControlPanel()
            }
        }
    }
}

// ═══════════════════════════════════════════════════
//  控制面板 UI
// ═══════════════════════════════════════════════════

@Composable
private fun FloatingServiceControlPanel() {
    val context = LocalContext.current

    // ── 权限与服务状态 ──
    var hasOverlayPermission by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }
    var isServiceRunning by remember {
        mutableStateOf(FloatingService.isRunning)
    }

    // 从系统设置页返回时刷新权限状态
    @Suppress("DEPRECATION")
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = Settings.canDrawOverlays(context)
                isServiceRunning = FloatingService.isRunning
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ── 主界面 ──
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 标题
            Text(
                "TaskFlow 仿真环境",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A1A)
            )
            Text(
                "多态悬浮胶囊 · 跨应用任务流转",
                fontSize = 14.sp,
                color = Color(0xFF888888)
            )

            Spacer(Modifier.height(8.dp))

            // ── 权限状态卡片 ──
            PermissionCard(
                hasPermission = hasOverlayPermission,
                onRequestPermission = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                }
            )

            // ── 服务控制卡片 ──
            if (hasOverlayPermission) {
                ServiceControlCard(
                    isRunning = isServiceRunning,
                    onToggle = {
                        if (!isServiceRunning) {
                            val intent = Intent(context, FloatingService::class.java)
                            ContextCompat.startForegroundService(context, intent)
                            isServiceRunning = true
                        } else {
                            context.stopService(Intent(context, FloatingService::class.java))
                            isServiceRunning = false
                        }
                    }
                )
            }

            // ── 使用说明 ──
            HelpCard()
        }
    }
}

// ═══════════════════════════════════════════════════
//  权限状态卡片
// ═══════════════════════════════════════════════════

@Composable
private fun PermissionCard(hasPermission: Boolean, onRequestPermission: () -> Unit) {
    val indicatorColor by animateColorAsState(
        targetValue = if (hasPermission) Color(0xFF4CAF50) else Color(0xFFFF9800),
        label = "permission-indicator"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 状态指示灯
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(indicatorColor)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "悬浮窗权限",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Color(0xFF1A1A1A)
                )
                Text(
                    if (hasPermission) "已授权 · 可在其他应用上方显示"
                    else "未授权 · 需要开启「显示在其他应用的上层」",
                    fontSize = 12.sp,
                    color = Color(0xFF888888)
                )
            }

            if (!hasPermission) {
                TextButton(onClick = onRequestPermission) {
                    Text("去授权", color = Color(0xFF1E88E5))
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════
//  服务控制卡片
// ═══════════════════════════════════════════════════

@Composable
private fun ServiceControlCard(isRunning: Boolean, onToggle: () -> Unit) {
    val gradientColors = if (isRunning)
        listOf(Color(0xFFEF5350), Color(0xFFE53935))
    else
        listOf(Color(0xFF42A5F5), Color(0xFF1E88E5))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "悬浮窗服务",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = Color(0xFF1A1A1A)
                    )
                    Text(
                        if (isRunning) "运行中 · 悬浮胶囊已显示" else "已停止",
                        fontSize = 12.sp,
                        color = Color(0xFF888888)
                    )
                }

                // 运行状态指示灯
                val dotColor by animateColorAsState(
                    targetValue = if (isRunning) Color(0xFF4CAF50) else Color(0xFFBDBDBD),
                    label = "service-indicator"
                )
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }

            // 启动/停止按钮
            Button(
                onClick = onToggle,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.linearGradient(gradientColors),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isRunning) "停止悬浮窗" else "启动悬浮窗",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════
//  使用说明卡片
// ═══════════════════════════════════════════════════

@Composable
private fun HelpCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F7FF)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "使用说明",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = Color(0xFF1565C0)
            )
            HelpItem("拖拽", "按住胶囊自由拖动，松手自动吸附屏幕边缘")
            HelpItem("点击", "轻触胶囊展开详情，再次点击收起")
            HelpItem("跨应用", "启动后可切换到任意 App，悬浮窗始终在最上层")
        }
    }
}

@Composable
private fun HelpItem(label: String, description: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E88E5)
        )
        Text(
            description,
            fontSize = 12.sp,
            color = Color(0xFF666666)
        )
    }
}