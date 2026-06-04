package com.example.hci_demo.logic

import android.util.Log
import com.example.hci_demo.model.CourseTask
import com.example.hci_demo.model.TaskFlowConfig
import com.google.gson.Gson

object TaskFlowParser {
    private val gson = Gson()

    /**
     * 封装解析纯文本 JSON 的核心业务
     */
    fun parseJsonToSchedule(jsonString: String): List<CourseTask>? {
        return try {
            val config = gson.fromJson(jsonString, TaskFlowConfig::class.java)
            Log.d("TaskFlowParser", "成功解析课表，共包含 ${config.total_tasks} 节课")
            config.schedule
        } catch (e: Exception) {
            Log.e("TaskFlowParser", "JSON 语法解析失败: ${e.localizedMessage}")
            null
        }
    }
}