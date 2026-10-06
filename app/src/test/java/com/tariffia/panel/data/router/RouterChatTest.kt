package com.tariffia.panel.data.router

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** JVM tests for the minimal chat request encoding and response parsing. */
class RouterChatTest {

    @Test
    fun encodesNonStreamingRequest() {
        val json = encodeChatRequest("mesh/free", listOf(ChatMessage("user", "hi")))
        assertTrue(json.contains("\"model\":\"mesh/free\""))
        assertTrue(json.contains("\"stream\":false"))
        assertTrue(json.contains("\"role\":\"user\""))
        assertTrue(json.contains("\"content\":\"hi\""))
    }

    @Test
    fun parsesStringAssistantContent() {
        val body = """
            {"id":"x","object":"chat.completion","model":"m",
             "choices":[{"index":0,"message":{"role":"assistant","content":"hello there"},"finish_reason":"stop"}]}
        """.trimIndent()
        assertEquals("hello there", parseChatResponse(body).assistantText())
    }

    @Test
    fun parsesArrayTextPartContent() {
        val body = """
            {"choices":[{"message":{"role":"assistant",
             "content":[{"type":"text","text":"foo"},{"type":"text","text":"bar"}]}}]}
        """.trimIndent()
        assertEquals("foobar", parseChatResponse(body).assistantText())
    }

    @Test
    fun ignoresUnknownFields() {
        val body = """
            {"id":"x","object":"chat.completion","usage":{"total_tokens":5},
             "mesh":{"served_by":"a/b"},"choices":[{"message":{"content":"ok"}}],"extra":true}
        """.trimIndent()
        assertEquals("ok", parseChatResponse(body).assistantText())
    }

    @Test
    fun missingContentIsNull() {
        assertNull(parseChatResponse("""{"choices":[{"message":{"role":"assistant"}}]}""").assistantText())
        assertNull(parseChatResponse("""{"choices":[]}""").assistantText())
        assertNull(parseChatResponse("""{}""").assistantText())
    }

    @Test
    fun malformedBodyThrows() {
        try {
            parseChatResponse("not json")
            fail("expected a serialization failure")
        } catch (expected: Exception) {
            // RouterClient maps this to RouterResult.InvalidResponse.
        }
    }

    @Test
    fun secretLookingFieldsAreNotSurfaced() {
        val secret = "sk-super-secret-value"
        val body = """{"authorization":"$secret","choices":[{"message":{"content":"ok","key":"$secret"}}]}"""
        val parsed = parseChatResponse(body)
        assertFalse(parsed.toString().contains(secret))
        assertEquals("ok", parsed.assistantText())
    }
}
