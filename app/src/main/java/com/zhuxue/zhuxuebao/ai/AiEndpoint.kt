package com.zhuxue.zhuxuebao.ai

import java.net.URI
import java.net.URISyntaxException

/**
 * 统一校验并拼出 chat/completions 地址。
 * 默认只允许智谱与 DeepSeek 的官方 https 域名，避免把 Bearer Key 发到任意主机；
 * 本地联调（debug 构建）可额外放行回环地址。
 */
object AiEndpoint {

    private val OFFICIAL_DOMAINS = listOf("bigmodel.cn", "deepseek.com")
    private val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "10.0.2.2")

    /** 联网真题依赖智谱的 web_search 工具，其他服务商没有对应的工具协议。 */
    private val WEB_SEARCH_DOMAINS = listOf("bigmodel.cn")

    fun resolve(baseUrl: String, allowLoopback: Boolean = false): String {
        val raw = baseUrl.trim()
        if (raw.isEmpty()) throw IllegalArgumentException("服务地址不能为空")
        val uri = try {
            URI(raw)
        } catch (error: URISyntaxException) {
            throw IllegalArgumentException("服务地址格式不正确")
        }
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            throw IllegalArgumentException("服务地址必须使用 https")
        }
        if (uri.userInfo != null) throw IllegalArgumentException("服务地址不能包含账号密码")
        if (uri.rawQuery != null || uri.rawFragment != null) {
            throw IllegalArgumentException("服务地址不能带查询参数")
        }
        val host = uri.host?.lowercase()
            ?: throw IllegalArgumentException("服务地址格式不正确")
        val official = OFFICIAL_DOMAINS.any { host == it || host.endsWith(".$it") }
        if (!official && !(allowLoopback && host in LOOPBACK_HOSTS)) {
            throw IllegalArgumentException(
                "服务地址只支持智谱（open.bigmodel.cn）或 DeepSeek（api.deepseek.com）官方域名"
            )
        }
        val base = raw.trimEnd('/')
        val path = uri.path.orEmpty().trimEnd('/')
        return if (path.endsWith("/chat/completions")) base else "$base/chat/completions"
    }

    fun supportsWebSearch(baseUrl: String): Boolean {
        val host = runCatching { URI(baseUrl.trim()).host?.lowercase() }.getOrNull() ?: return false
        return WEB_SEARCH_DOMAINS.any { host == it || host.endsWith(".$it") }
    }
}
