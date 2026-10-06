package com.tariffia.panel.data.router

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Minimal OpenAI-compatible chat wire types for `POST /v1/chat/completions`.
 *
 * Non-streaming only (`stream = false`); the Panel never computes tokens or cost.
 * Unknown fields are ignored via the shared [routerJson] configuration. No secret is
 * ever part of these shapes.
 */
@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
)

@Serializable
internal data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean,
)

@Serializable
internal data class ChatResponse(
    val model: String = "",
    val choices: List<ChatChoice> = emptyList(),
)

@Serializable
internal data class ChatChoice(
    val index: Int = 0,
    val message: ChatResponseMessage? = null,
    val finish_reason: String? = null,
)

/**
 * The assistant message. `content` is normally a string, but the OpenAI shape also allows
 * an array of content parts; it is decoded as a raw [JsonElement] and flattened by
 * [assistantText].
 */
@Serializable
internal data class ChatResponseMessage(
    val role: String = "assistant",
    val content: JsonElement? = null,
)

/** Encodes a non-streaming chat request body (explicit `stream:false`). */
internal fun encodeChatRequest(model: String, messages: List<ChatMessage>): String =
    routerJson.encodeToString(ChatRequest(model = model, messages = messages, stream = false))

/**
 * Parses a `POST /v1/chat/completions` response body.
 *
 * @throws kotlinx.serialization.SerializationException when the body is not valid.
 */
internal fun parseChatResponse(body: String): ChatResponse =
    routerJson.decodeFromString<ChatResponse>(body)

/**
 * The assistant text from the first choice, or null when there is none. Handles both a
 * plain string and an array of `{ type: "text", text: ... }` parts. Never invents content.
 */
internal fun ChatResponse.assistantText(): String? =
    choices.firstOrNull()?.message?.content?.let(::contentToText)

private fun contentToText(content: JsonElement): String? = when (content) {
    is JsonPrimitive -> content.contentOrNull
    is JsonArray -> content
        .mapNotNull { part -> (part as? JsonObject)?.get("text")?.let { (it as? JsonPrimitive)?.contentOrNull } }
        .joinToString("")
        .ifBlank { null }
    else -> null
}
