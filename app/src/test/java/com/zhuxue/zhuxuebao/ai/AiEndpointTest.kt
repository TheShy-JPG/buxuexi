package com.zhuxue.zhuxuebao.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiEndpointTest {

    @Test
    fun `default base url resolves to chat completions`() {
        assertEquals(
            "https://open.bigmodel.cn/api/paas/v4/chat/completions",
            AiEndpoint.resolve("https://open.bigmodel.cn/api/paas/v4")
        )
    }

    @Test
    fun `trailing slash and full endpoint are kept stable`() {
        assertEquals(
            "https://open.bigmodel.cn/api/paas/v4/chat/completions",
            AiEndpoint.resolve("https://open.bigmodel.cn/api/paas/v4/")
        )
        assertEquals(
            "https://open.bigmodel.cn/api/paas/v4/chat/completions",
            AiEndpoint.resolve("https://open.bigmodel.cn/api/paas/v4/chat/completions")
        )
    }

    @Test
    fun `http is rejected`() {
        val error = runCatching { AiEndpoint.resolve("http://open.bigmodel.cn/api/paas/v4") }
            .exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `lookalike host is rejected`() {
        val error = runCatching {
            AiEndpoint.resolve("https://open.bigmodel.cn.evil.example/api")
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `other host is rejected`() {
        val error = runCatching { AiEndpoint.resolve("https://example.com/v1") }
            .exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `loopback is allowed only when explicitly enabled`() {
        val error = runCatching { AiEndpoint.resolve("https://localhost:8443/v1") }
            .exceptionOrNull()
        assertTrue(error is IllegalArgumentException)

        assertEquals(
            "https://localhost:8443/v1/chat/completions",
            AiEndpoint.resolve("https://localhost:8443/v1", allowLoopback = true)
        )
    }

    @Test
    fun `deepseek official host is allowed`() {
        assertEquals(
            "https://api.deepseek.com/v1/chat/completions",
            AiEndpoint.resolve("https://api.deepseek.com/v1")
        )
        assertEquals(
            "https://api.deepseek.com/chat/completions",
            AiEndpoint.resolve("https://api.deepseek.com")
        )
    }

    @Test
    fun `deepseek lookalike host is rejected`() {
        val error = runCatching {
            AiEndpoint.resolve("https://api.deepseek.com.evil.example/v1")
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `only zhipu supports web search`() {
        assertTrue(AiEndpoint.supportsWebSearch("https://open.bigmodel.cn/api/paas/v4"))
        assertTrue(!AiEndpoint.supportsWebSearch("https://api.deepseek.com/v1"))
        assertTrue(!AiEndpoint.supportsWebSearch("https://localhost:8443/v1"))
    }

    @Test
    fun `credentials and query are rejected`() {
        assertTrue(
            runCatching { AiEndpoint.resolve("https://user:pass@open.bigmodel.cn/api") }
                .exceptionOrNull() is IllegalArgumentException
        )
        assertTrue(
            runCatching { AiEndpoint.resolve("https://open.bigmodel.cn/api?target=x") }
                .exceptionOrNull() is IllegalArgumentException
        )
    }
}
