package net.portswigger.mcp.tools

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object CrawlTaskRegistry {

    private val tasks = ConcurrentHashMap<String, AuthenticatedCrawler>()

    fun register(crawler: AuthenticatedCrawler): String {
        val id = "crawl-" + UUID.randomUUID().toString().substring(0, 8)
        tasks[id] = crawler
        return id
    }

    fun get(taskId: String): AuthenticatedCrawler? = tasks[taskId]

    fun remove(taskId: String): AuthenticatedCrawler? = tasks.remove(taskId)

    fun listAll(): Map<String, AuthenticatedCrawler> = tasks.toMap()
}
