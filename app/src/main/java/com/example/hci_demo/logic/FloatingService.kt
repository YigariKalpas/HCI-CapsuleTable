package com.example.hci_demo.logic

import android.app.Service
import com.example.hci_demo.model.CourseTask
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
import kotlinx.coroutines.delay
import androidx.compose.runtime.derivedStateOf
// ═══════════════════════════════════════════════════
//  悬浮窗多态状态机（成员 C 的状态机控制）
// ═══════════════════════════════════════════════════
enum class WidgetState {
    CAPSULE, EXPANDED,WEEKSHOW,HIDE //HIDE预留给贴边自动隐藏功能
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
        //用于记录json文件解析出的课程类型
        var currentScheduleList = mutableStateListOf<CourseTask>()

        var isPinnedLeft by mutableStateOf(false)

        fun forceSnapToEdge(context: Context, service: FloatingService, state: WidgetState, widthDp: Int) {
            val view = service.containerView ?: return
            val wm = service.windowManager
            val params = service.layoutParams
            val screenWidth = context.resources.displayMetrics.widthPixels
            val density = context.resources.displayMetrics.density

            // 将 Compose 的 Dp 目标宽度加上外层 padding (32dp) 转化为真实的像素宽度
            val realViewWidth = ((widthDp + 32) * density).toInt()

            // 根据当前的贴边方向，重新精确计算 targetX
            val targetX = if (isPinnedLeft) {
                0 // 靠左贴边：坐标永远是 0
            } else {
                screenWidth - realViewWidth + 11 // 靠右贴边：屏幕宽度 减去 最新视图宽度
            }

            // 针对 HIDE 状态特殊优化：如果是隐藏态，靠右时可以让它往屏幕外多推一点，只露出半圆
            val finalX = if (state == WidgetState.HIDE && !isPinnedLeft) {
                targetX + (12 * density).toInt() // 右贴边隐藏时向右微调
            } else {
                targetX
            }

            // 平滑同步更新 WindowManager 布局
            params.x = finalX
            try {
                wm.updateViewLayout(view, params)
            } catch (_: Exception) {}
        }
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

        //新增判断吸附确定后，更新全局贴边方向状态
        isPinnedLeft = (targetX == 0)

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
//  Composable: 悬浮窗 UI（修复作用域、支持 HIDE 左右贴边形变）
// ═══════════════════════════════════════════════════

@Composable
private fun FloatingWidgetContent() {
    //状态绑定 ──
    // 获取当日日期编码 001~007
    val todayDateCode = remember { getCurrentWeekDateCode() }
    // 转为中文星期文案
    val todayWeekText = remember { getWeekdayText(todayDateCode) }

    // 过滤：只保留当天星期对应的课程
    val scheduleList by remember {
        derivedStateOf {
            FloatingService.currentScheduleList.filter { it.weekday == todayDateCode }
        }
    }

    var currentState by remember { mutableStateOf(WidgetState.CAPSULE) }

    // 初始化：页面刚加载就获取当前真实时间
    var currentVirtualTime by remember { mutableStateOf(getCurrentTime()) }

    // 每秒自动更新真实时间
    LaunchedEffect(Unit) {
        while (true) {
            currentVirtualTime = getCurrentTime()// 重新拿当前时分
            delay(1000) // 停顿1秒，再循环
        }
    }

    val isLeft = FloatingService.isPinnedLeft
    val context = androidx.compose.ui.platform.LocalContext.current

    val activeCourse = remember(scheduleList, currentVirtualTime) {
        val todayFinishCourse = scheduleList.firstOrNull { it.end_time > currentVirtualTime }
        // 情况1：还有课没上完，沿用原有当天逻辑
        if(todayFinishCourse != null){
            todayFinishCourse
        }else{
            // 情况2：今日全部结课 → 去找次日第一天课程作为activeCourse
            val todayCode = getCurrentWeekDateCode()
            val nextCode = when(todayCode){
                "001"->"002"
                "002"->"003"
                "003"->"004"
                "004"->"005"
                "005"->"006"
                "006"->"007"
                "007"->"001"
                else->"001"
            }
            val nextDayAllCourse = FloatingService.currentScheduleList.filter { it.weekday == nextCode }
            // 次日有课就选次日第一节，没有就设为null
            if(nextDayAllCourse.isNotEmpty()) nextDayAllCourse.first() else null
        }
    }

    // 根据课程、当前时间计算状态文案：上课倒计时/进行中/已结束，全天结课自动查找次日首课倒计时
    val countdownText = remember(activeCourse, currentVirtualTime, scheduleList) {
        if (activeCourse == null) return@remember "近期无课程"

        try {
            val startParts = activeCourse.start_time.split(":")
            val endParts = activeCourse.end_time.split(":")
            val nowParts = currentVirtualTime.split(":")

            val startMin = startParts[0].toInt() * 60 + startParts[1].toInt()
            val endMin = endParts[0].toInt() * 60 + endParts[1].toInt()
            val nowMin = nowParts[0].toInt() * 60 + nowParts[1].toInt()

            //获取今天是周几
            val todayCode = getCurrentWeekDateCode()

            //如果是今天的课
            if (activeCourse.weekday == todayCode) {
                when {
                    nowMin in startMin until endMin -> "进行中"
                    nowMin < startMin -> "${startMin - nowMin} min"
                    else -> "已结束"
                }
            }
            //如果是第二天的课，标注“明日”
            else {
                "明日"
            }

        } catch (e: Exception) {
            "-- min"
        }
    }

    // ── 监听状态机切换，一旦变动，立刻强制通知外层 WindowManager 贴边更新 ──
    LaunchedEffect(currentState) {
        val targetWidthDp = when (currentState) {
            WidgetState.HIDE -> 32
            WidgetState.CAPSULE -> 170
            WidgetState.EXPANDED -> 280
            WidgetState.WEEKSHOW -> 340
        }
        (context as? FloatingService)?.let { service ->
            FloatingService.forceSnapToEdge(context, service, currentState, targetWidthDp)
        }
    }

    // ── 宽高的状态流转 ──
    val width by animateDpAsState(
        targetValue = when (currentState) {
            WidgetState.HIDE -> 32.dp
            WidgetState.CAPSULE -> 170.dp
            WidgetState.EXPANDED -> 280.dp
            WidgetState.WEEKSHOW -> 340.dp
        },
        animationSpec = spring(dampingRatio = 0.55f),
        label = "capsule-width"
    )

    val height by animateDpAsState(
        targetValue = when (currentState) {
            WidgetState.HIDE -> 48.dp
            WidgetState.CAPSULE -> 48.dp
            WidgetState.EXPANDED -> 190.dp
            WidgetState.WEEKSHOW -> 260.dp
        },
        animationSpec = spring(dampingRatio = 0.55f),
        label = "capsule-height"
    )

    // ── 动态形状裁剪（左/右各异半圆表现） ──
    val containerShape = remember(currentState, isLeft) {
        if (currentState == WidgetState.HIDE) {
            if (isLeft) {
                // 左贴边：左直右圆
                RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 24.dp, bottomEnd = 24.dp)
            } else {
                // 右贴边：左圆右直
                RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = 0.dp, bottomEnd = 0.dp)
            }
        } else {
            RoundedCornerShape(24.dp)
        }
    }

    // ── 动态边缘间距（确保隐藏态完全贴死屏幕边框） ──
    val outerPadding = when (currentState) {
        WidgetState.HIDE -> {
            if (isLeft) PaddingValues(top = 16.dp, bottom = 16.dp, start = 0.dp, end = 16.dp)
            else PaddingValues(top = 16.dp, bottom = 16.dp, start = 16.dp, end = 0.dp)
        }
        else -> PaddingValues(16.dp)
    }

    // ── UI 渲染 ──
    Box(modifier = Modifier.padding(outerPadding)) {
        Box(
            modifier = Modifier
                .shadow(12.dp, containerShape)
                .width(width)
                .height(height)
                .clip(containerShape)
                .background(
                    Brush.linearGradient(
                        colors = if (activeCourse != null) {
                            val parsedColor = Color(android.graphics.Color.parseColor(activeCourse.theme_color))
                            listOf(parsedColor, parsedColor.copy(alpha = 0.8f))
                        } else {
                            listOf(Color(0xFF42A5F5), Color(0xFF1E88E5))
                        }
                    )
                )
                .clickable {
                    currentState = when (currentState) {
                        WidgetState.HIDE -> WidgetState.CAPSULE
                        WidgetState.CAPSULE -> WidgetState.EXPANDED
                        WidgetState.EXPANDED -> WidgetState.CAPSULE
                        WidgetState.WEEKSHOW -> WidgetState.EXPANDED
                    }
                }
                .padding(if (currentState == WidgetState.HIDE) 2.dp else 14.dp)
        ) {
            AnimatedContent(
                targetState = currentState,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "state-transition"
            ) { state ->
                // 只有在课表完全为空的时候才提示导入信息而不是当日课表为空的时候提示
                if (FloatingService.currentScheduleList.isEmpty()){
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("请先在主面板导入JSON课表", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                    }
                } else {
                    when (state) {
                        // ── 隐藏态：根据左右方向区分小点靠向 ──
                        WidgetState.HIDE -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = if (isLeft) Alignment.CenterEnd else Alignment.CenterStart
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 6.dp)
                                        .size(6.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(Color.White.copy(alpha = 0.7f))
                                )
                            }
                        }

                        // ── 胶囊态：渲染动态数据 ──
                        WidgetState.CAPSULE -> {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Badge(containerColor = Color.White.copy(alpha = 0.3f)) {
                                    Text(countdownText, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    activeCourse?.short_name ?: "无课程",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }

                        // ── 展开态：单节课程渐进披露 ──
                        WidgetState.EXPANDED -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Text(
                                    "当前时刻: $todayWeekText $currentVirtualTime",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 11.sp
                                )

                                if (activeCourse == null) {
                                    Text(
                                        "近日无课",
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {

                                    Text(
                                        activeCourse.course_name,
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )

                                    HorizontalDivider(
                                        Modifier.padding(vertical = 8.dp),
                                        color = Color.White.copy(alpha = 0.2f)
                                    )

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.LocationOn,
                                            null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            " 教室: ${activeCourse?.classroom}",
                                            color = Color.White,
                                            fontSize = 13.sp
                                        )
                                    }
                                    Text(
                                        " 讲师: ${activeCourse?.teacher}",
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(start = 14.dp, top = 2.dp)
                                    )
                                    Text(
                                        " 时间: ${activeCourse?.start_time} - ${activeCourse?.end_time}",
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(start = 14.dp, top = 2.dp)
                                    )
                                }
                                Spacer(Modifier.weight(1f))

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(
                                        onClick = { currentState = WidgetState.WEEKSHOW },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.25f)),
                                        modifier = Modifier.weight(1f).height(32.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("完整周课表", fontSize = 11.sp, color = Color.White)
                                    }

                                    Button(
                                        onClick = { currentState = WidgetState.HIDE },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.3f)),
                                        modifier = Modifier.height(32.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Text("模拟隐藏", fontSize = 11.sp, color = Color.White)
                                    }
                                }
                            }
                        }

                        // ── 周视图状态 ──
                        WidgetState.WEEKSHOW -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("TaskFlow 课表管理器", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text("共 ${scheduleList.size} 节", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                                }

                                HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Color.White.copy(alpha = 0.2f))

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    scheduleList.take(4).forEach { course ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                                .padding(6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(course.short_name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                            Text("${course.start_time} @ ${course.classroom}", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
                                        }
                                    }
                                }
                                Spacer(Modifier.weight(1f))
                                Text("提示：轻触任意区域收起", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// 获取系统当前时分，格式HH:mm
private fun getCurrentTime(): String {
    val cal = java.util.Calendar.getInstance()
    val hh = cal.get(java.util.Calendar.HOUR_OF_DAY)
    val mm = cal.get(java.util.Calendar.MINUTE)
    return "%02d:%02d".format(hh, mm)
}

// 获取今天是周几，返回 001=周一，002=周二...
private fun getCurrentWeekDateCode(): String {
    val cal = java.util.Calendar.getInstance()
    val dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK)
    // 系统周日=1，周一=2 ... 周六=7
    return when (dayOfWeek) {
        2 -> "001" // 周一
        3 -> "002" // 周二
        4 -> "003" // 周三
        5 -> "004" // 周四
        6 -> "005" // 周五
        7 -> "006" // 周六
        1 -> "007" // 周日
        else -> "001"
    }
}

// 把 001 → 周一，002→周二… 用于界面显示
private fun getWeekdayText(dateCode: String): String {
    return when (dateCode) {
        "001" -> "周一"
        "002" -> "周二"
        "003" -> "周三"
        "004" -> "周四"
        "005" -> "周五"
        "006" -> "周六"
        "007" -> "周日"
        else -> "周一"
    }
}