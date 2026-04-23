package org.springframework.ai.kotlin.coroutines.sample

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.catch
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.ChatClientRequestSpec
import org.springframework.ai.kotlin.coroutines.chat.kotlinStream
import org.springframework.ai.kotlin.coroutines.chat.kotlinStreamContent
import org.springframework.ai.kotlin.coroutines.flux.asKotlinFlow
import org.springframework.ai.kotlin.coroutines.vectorstore.kotlinSimilaritySearch
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Sample Spring Boot application demonstrating Spring AI Kotlin Coroutines support.
 *
 * This application shows idiomatic Kotlin usage of:
 * - Flow-based streaming with ChatClient.kotlinStream()
 * - Suspend-function VectorStore search with kotlinSimilaritySearch()
 * - Flux-to-Flow conversion with asKotlinFlow()
 *
 * Run with: SPRING_AI_OPENAI_API_KEY=your-key ./mvnw spring-boot:run
 *
 * @author Thomas Vitale
 * @since 2.0.0
 */
@SpringBootApplication
class KotlinCoroutinesSampleApplication

/**
 * Configuration class demonstrating Kotlin DSL for Spring AI beans.
 */
@Configuration
class AiConfiguration {

    @Bean
    fun chatController(chatClient: ChatClient): ChatController =
        ChatController(chatClient)

    @Bean
    fun vectorStoreService(vectorStore: org.springframework.ai.vectorstore.VectorStore): VectorStoreService =
        VectorStoreService(vectorStore)
}

/**
 * REST controller demonstrating Kotlin DSL for ChatClient streaming.
 *
 * Usage:
 *   GET /chat/stream?message=Hello -> Server-sent events stream of content chunks
 *   GET /chat?message=Hello        -> Single aggregated response
 */
@RestController
class ChatController(private val chatClient: ChatClient) {

    /**
     * Streams chat responses as a Kotlin Flow, using kotlinStreamContent().
     *
     * This demonstrates the idiomatic Kotlin way to consume streaming responses —
     * instead of Flux, you get a kotlinx.coroutines.flow.Flow that integrates
     * naturally with Spring WebFlux's reactive pipeline.
     *
     * @param message the user message
     * @return a Flow of content strings as they arrive from the model
     */
    @GetMapping("/chat/stream")
    fun streamChat(@RequestParam(defaultValue = "Explain Kotlin coroutines in one sentence") message: String): Flow<String> =
        chatClient.prompt(message).kotlinStreamContent()
            .onEach { chunk -> println("  [chunk] $chunk") }
            .catch { e -> emit("Error: ${e.message}") }

    /**
     * Non-streaming chat with suspend function — waits for full response.
     *
     * @param message the user message
     * @return the complete response content
     */
    @GetMapping("/chat")
    suspend fun chat(@RequestParam(defaultValue = "What is Spring AI?") message: String): String =
        chatClient.prompt(message)
            .call()
            .content() ?: "No response"
}

/**
 * Service demonstrating VectorStore kotlinSimilaritySearch().
 *
 * Uses the suspend-function variant to perform non-blocking vector similarity
 * searches from a coroutine context — no Flux/Mono wrapping needed.
 */
@Service
class VectorStoreService(private val vectorStore: org.springframework.ai.vectorstore.VectorStore) {

    /**
     * Performs similarity search with a simple query string.
     * Uses Dispatchers.IO internally to avoid blocking the caller.
     */
    suspend fun search(query: String): List<Document> =
        vectorStore.kotlinSimilaritySearch(query)

    /**
     * Performs similarity search with a full SearchRequest.
     * Enables filtering, top-k limits, and similarity threshold configuration.
     */
    suspend fun searchWithRequest(request: SearchRequest): List<Document> =
        vectorStore.kotlinSimilaritySearch(request)

    /**
     * Performs similarity search with top-k limit.
     */
    suspend fun searchTopK(query: String, topK: Int): List<Document> =
        vectorStore.kotlinSimilaritySearch(
            SearchRequest.builder()
                .query(query)
                .topK(topK)
                .build()
        )
}

/**
 * REST controller for vector store operations.
 */
@RestController
class VectorStoreController(private val vectorStoreService: VectorStoreService) {

    @GetMapping("/vector/search")
    suspend fun search(@RequestParam(defaultValue = "Spring AI features") query: String): List<Map<String, Any>> {
        val docs = vectorStoreService.search(query)
        return docs.map { doc ->
            mapOf(
                "id" to doc.id,
                "text" to doc.text,
                "metadata" to doc.metadata
            )
        }
    }

    @GetMapping("/vector/search/topk")
    suspend fun searchTopK(
        @RequestParam query: String,
        @RequestParam(defaultValue = "5") topK: Int
    ): List<Map<String, Any>> {
        val docs = vectorStoreService.searchTopK(query, topK)
        return docs.map { doc ->
            mapOf(
                "id" to doc.id,
                "text" to doc.text,
                "score" to doc.metadata["score"]
            )
        }
    }
}