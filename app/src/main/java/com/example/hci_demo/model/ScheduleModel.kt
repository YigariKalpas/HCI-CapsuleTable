package com.example.hci_demo.model

import androidx.annotation.Keep

@Keep
data class TaskFlowConfig(
    val version: String,
    val total_tasks: Int,
    val schedule: List<CourseTask>
)

@Keep
data class CourseTask(
    val id: String,
    val weekday: String,
    val course_name: String,
    val short_name: String,      // 胶囊态优先使用缩写
    val teacher: String,
    val classroom: String,       // 展开态路径规划使用
    val start_time: String,      // "08:00"
    val end_time: String,        // "09:35"
    val duration_minutes: Int,
    val theme_color: String,     // 悬浮窗动态背景色 "#853DE0"
    val priority: String         // HIGH / NORMAL / LOW
)