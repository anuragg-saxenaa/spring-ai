/*
 * Spring AI Kotlin Coroutines Module
 *
 * Provides first-class Kotlin coroutines support for Spring AI, including:
 * - Flow-based ChatClient streaming (`kotlinStream()`)
 * - Suspend-function VectorStore search (`kotlinSimilaritySearch()`)
 * - Flux/Mono to Flow converters (`asKotlinFlow()`)
 * - Coroutine-friendly advisors via `kotlinx.coroutines.channels.Channel`
 * - `@McpTool` suspend function support (auto-detected and wrapped)
 *
 * ## Coroutine Conventions
 *
 * All suspending functions in this module follow these conventions:
 *
 * ### Blocking I/O
 * Blocking operations (like vector similarity search over HTTP) are automatically
 * offloaded to [Dispatchers.IO][kotlinx.coroutines.Dispatchers.IO] so they never
 * block the calling coroutine's dispatcher. You can call these functions from any
 * context without risking deadlock:
 *
 * ```kotlin
 * suspend fun search(query: String) {
 *     val results = vectorStore.kotlinSimilaritySearch(query) // runs on Dispatchers.IO
 * }
 * ```
 *
 * ### Flow vs Flux
 * Prefer [kotlinx.coroutines.flow.Flow][kotlinx.coroutines.flow.Flow] over
 * [reactor.core.publisher.Flux][reactor.core.publisher.Flux] when writing
 * new Kotlin code. Convert back to Flux only at the boundary:
 *
 * ```kotlin
 * // Idiomatic Kotlin: use Flow throughout
 * chatClient.prompt("Hello")
 *     .kotlinStream()
 *     .collect { response -> process(response) }
 *
 * // Only convert to Flux at the integration boundary
 * val flux: Flux<ChatResponse> = chatClient.prompt("Hello")
 *     .stream()
 *     .chatResponse()
 * ```
 *
 * ### Cancellation
 * [Flow][kotlinx.coroutines.flow.Flow] cancellation is cooperative. Use
 * [kotlinx.coroutines.flow.catch][kotlinx.coroutines.flow.catch] and
 * [kotlinx.coroutines.flow.onCompletion][kotlinx.coroutines.flow.onCompletion]
 * to handle errors gracefully without leaking resources.
 *
 * ### Error Handling
 * Wrap flows in [kotlinx.coroutines.flow.catch][kotlinx.coroutines.flow.catch]:
 *
 * ```kotlin
 * chatClient.prompt("Hello")
 *     .kotlinStream()
 *     .catch { e -> emit(fallbackResponse(e)) }
 *     .collect { ... }
 * ```
 *
 * @author Thomas Vitale
 * @since 2.0.0
 */
@NonNullApi
package org.springframework.ai.kotlin.coroutines;

import org.jspecify.annotations.NonNullApi;
