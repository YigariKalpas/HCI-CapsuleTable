package com.example.hci_demo.logic

import android.animation.ValueAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.hci_demo.MainActivity
import com.example.hci_demo.R
import kotlin.math.abs

// ═══════════════════════════════════════════════════
//  悬浮窗多态状态机（成员 C 的状态机控制）
// ═══════════════════════════════════════════════════
enum class WidgetState {
    CAPSULE, EXPANDED
}

// ═══════════════════════════════════════════════════
//  FloatingService - 全局跨应用前台悬浮窗服务
// ═══════════════════════════════════════════════════
class FloatingService : LifecycleService() {

    // ── 窗口管理 ──
    private lateinit var windowManager: WindowManager
    private var containerView: FloatingWindowContainer? = null
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var viewTreeOwner: ViewTreeOwner? = null

    companion object {
        private const val CHANNEL_ID = "floating_channel"
        private const val NOTIFICATION_ID = 1001

        /** 外部可观测的运行状态 */
        var isRunning = false
            private set
    }

    // ═════════════════════════════════════════════
    //  ComposeView 所需的生命周期宿主
    //  独立于 Service 自身生命周期，避免时序冲突
    // ═════════════════════════════════════════════

    private class ViewTreeOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)
        private val store = ViewModelStore()

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateRegistryController.savedStateRegistry
        override val viewModelStore: ViewModelStore get() = store

        fun onCreate() {
            savedStateRegistryController.performRestore(null)
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
        }

        fun onResume() {
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }

        fun onDestroy() {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
            store.clear()
        }
    }

    // ═════════════════════════════════════════════
    //  生命周期
    // ═════════════════════════════════════════════

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        createNotificationChannel()
        startForegroundWithType()

        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (containerView == null) {
            showFloatingWindow()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        viewTreeOwner?.onDestroy()
        viewTreeOwner = null
        containerView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        containerView = null
        isRunning = false
        super.onDestroy()
    }

    // ═════════════════════════════════════════════
    //  前台通知
    // ═════════════════════════════════════════════

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TaskFlow 悬浮窗服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持 TaskFlow 悬浮窗持续运行"
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun startForegroundWithType() {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TaskFlow 运行中")
            .setContentText("悬浮胶囊正在其他应用上方显示")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    // ═════════════════════════════════════════════
    //  WindowManager 悬浮窗创建
    // ═════════════════════════════════════════════

    private fun showFloatingWindow() {
        // ── LayoutParams ──
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - dpToPx(200)
            y = dpToPx(120)
        }

        // ── 独立的生命周期宿主 ──
        val owner = ViewTreeOwner()
        viewTreeOwner = owner
        owner.onCreate()

        // ── 拖拽容器（窗口根视图）──
        val container = FloatingWindowContainer(this)
        containerView = container

        // 在根视图上设置 ViewTree owners，
        // 因为 Compose 的 WindowRecomposer 从窗口根视图向上查找
        container.setViewTreeLifecycleOwner(owner)
        container.setViewTreeSavedStateRegistryOwner(owner)
        container.setViewTreeViewModelStoreOwner(owner)

        // ── ComposeView ──
        val composeView = ComposeView(this).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                MaterialTheme {
                    FloatingWidgetContent()
                }
            }
        }
        container.addView(composeView)

        // ── 添加到屏幕 ──
        windowManager.addView(container, layoutParams)

        // 激活 Compose 组合（必须在 addView 之后）
        owner.onResume()
    }

    // ═════════════════════════════════════════════
    //  边缘吸附动画
    // ═════════════════════════════════════════════

    private fun snapToEdge() {
        val view = containerView ?: return
        val screenWidth = resources.displayMetrics.widthPixels
        val viewWidth = view.width

        // 判断最近的边缘
        val centerX = layoutParams.x + viewWidth / 2
        val targetX = if (centerX < screenWidth / 2) 0 else screenWidth - viewWidth

        ValueAnimator.ofInt(layoutParams.x, targetX).apply {
            duration = 350
            interpolator = OvershootInterpolator(0.8f)
            addUpdateListener { animator ->
                layoutParams.x = animator.animatedValue as Int
                try { windowManager.updateViewLayout(view, layoutParams) } catch (_: Exception) {}
            }
            start()
        }
    }

    // ═════════════════════════════════════════════
    //  拖拽拦截容器（处理 WindowManager 级别拖拽）
    // ═════════════════════════════════════════════

    /**
     * 自定义 FrameLayout，通过 [onInterceptTouchEvent] 实现拖拽检测：
     * - 小幅触摸 → 不拦截，交给子 ComposeView 处理（点击切换形态）
     * - 超出 touchSlop → 拦截并拖拽整个悬浮窗，松手后触发边缘吸附
     */
    private inner class FloatingWindowContainer(context: Context) : FrameLayout(context) {

        private var startRawX = 0f
        private var startRawY = 0f
        private var lastRawX = 0f
        private var lastRawY = 0f
        private var isDragging = false
        private val scaledTouchSlop = ViewConfiguration.get(context).scaledTouchSlop

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = ev.rawX
                    startRawY = ev.rawY
                    lastRawX = ev.rawX
                    lastRawY = ev.rawY
                    isDragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!isDragging) {
                        val dx = abs(ev.rawX - startRawX)
                        val dy = abs(ev.rawY - startRawY)
                        if (dx > scaledTouchSlop || dy > scaledTouchSlop) {
                            isDragging = true
                            return true          // 拦截！后续事件进入 onTouchEvent
                        }
                    }
                }
            }
            return false
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val wmParams = this@FloatingService.layoutParams
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = event.rawX
                    startRawY = event.rawY
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - lastRawX
                    val dy = event.rawY - lastRawY
                    wmParams.x += dx.toInt()
                    wmParams.y += dy.toInt()
                    try {
                        windowManager.updateViewLayout(this, wmParams)
                    } catch (_: Exception) {}
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        snapToEdge()
                    }
                    isDragging = false
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
    }

    // ═════════════════════════════════════════════
    //  工具
    // ═════════════════════════════════════════════

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()
}

// ═══════════════════════════════════════════════════
//  Composable: 悬浮窗 UI（胶囊态 / 展开态）
// ═══════════════════════════════════════════════════

@Composable
private fun FloatingWidgetContent() {
    var currentState by remember { mutableStateOf(WidgetState.CAPSULE) }

    // 弹簧动画驱动的尺寸变化（成员 B 的弹簧动效）
    val width by animateDpAsState(
        targetValue = if (currentState == WidgetState.CAPSULE) 160.dp else 280.dp,
        animationSpec = spring(dampingRatio = 0.75f),
        label = "capsule-width"
    )
    val height by animateDpAsState(
        targetValue = if (currentState == WidgetState.CAPSULE) 48.dp else 180.dp,
        animationSpec = spring(dampingRatio = 0.75f),
        label = "capsule-height"
    )

    // 外层 padding 为阴影留出渲染空间
    Box(modifier = Modifier.padding(16.dp)) {
        Box(
            modifier = Modifier
                .shadow(12.dp, RoundedCornerShape(24.dp))
                .width(width)
                .height(height)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF42A5F5), Color(0xFF1E88E5))
                    )
                )
                .clickable {
                    currentState = if (currentState == WidgetState.CAPSULE)
                        WidgetState.EXPANDED else WidgetState.CAPSULE
                }
                .padding(12.dp)
        ) {
            AnimatedContent(
                targetState = currentState,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "state-transition"
            ) { state ->
                if (state == WidgetState.CAPSULE) {
                    // ── 胶囊态：核心倒计时 ──
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Badge(containerColor = Color.White.copy(alpha = 0.3f)) {
                            Text("15 min", color = Color.White)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "用户交互技术",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    // ── 展开态：完整课程路径 ──
                    Column {
                        Text(
                            "下一节课",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                        Text(
                            "用户交互技术 (HCI)",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        @Suppress("DEPRECATION")
                        Divider(
                            Modifier.padding(vertical = 8.dp),
                            color = Color.White.copy(alpha = 0.2f)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(" 信工楼 402", color = Color.White, fontSize = 14.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { /* 模拟跳转地图 */ },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.2f)
                            ),
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
}
