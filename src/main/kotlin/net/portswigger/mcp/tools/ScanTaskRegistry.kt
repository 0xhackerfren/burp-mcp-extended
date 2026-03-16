package net.portswigger.mcp.tools

import burp.api.montoya.scanner.ScanTask
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object ScanTaskRegistry {

    private val tasks = ConcurrentHashMap<String, ScanTask>()

    fun register(task: ScanTask): String {
        val id = UUID.randomUUID().toString().substring(0, 8)
        tasks[id] = task
        return id
    }

    fun get(taskId: String): ScanTask? = tasks[taskId]

    fun remove(taskId: String): ScanTask? = tasks.remove(taskId)

    fun listAll(): Map<String, ScanTask> = tasks.toMap()

    fun clear() {
        tasks.clear()
    }
}
