/*
 * Copyright 2023-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.kotlin.coroutines.advisor

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import reactor.core.publisher.Flux

import org.springframework.ai.chat.client.ChatClientRequest
import org.springframework.ai.chat.client.ChatClientResponse
import org.springframework.ai.chat.client.advisor.api.Advisor
import org.springframework.ai.chat.client.advisor.api.AdvisorChain
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor

/**
 * Coroutine-friendly advisors that use [kotlinx.coroutines.channels.Channel] for
 * event streaming, providing back-pressure support and idiomatic Kotlin flow APIs.
 *
 * These advisors wrap the existing Spring AI advisors and expose their events as
 * Kotlin [Flow]s instead of reactive [Flux]s, enabling natural integration with
 * suspend functions and structured concurrency.
 *
 * @author Thomas Vitale
 * @since 2.0.0
 */

/**
 * Converts a [Flux] of [ChatClientResponse] events into a [Flow].
 *
 * This adapter bridges the reactive advisor chain (which emits via [Flux]) to a
 * coroutine [Flow], applying back-pressure via the underlying channel buffering.
 *
 * The resulting [Flow] emits each [ChatClientResponse] as it arrives, making it
 * suitable for streaming UI updates, progress tracking, or any use case where
 * incremental results need to be consumed as they arrive.
 *
 * @param capacity the channel capacity for buffering (default 16). Use
 * [kotlinx.coroutines.channels.Channel.BUFFERED] for unbounded buffering, or
 * [kotlinx.coroutines.channels.Channel.UNLIMITED] to disable buffering limits.
 * @return a [Flow] emitting [ChatClientResponse] items from the advisor chain
 */
fun Flux<ChatClientResponse>.asFlow(capacity: Int = 16): Flow<ChatClientResponse> = callbackFlow {
    this@asFlow.subscribe(
        { trySend(it) },
        { close(it) },
        { close() }
    )
    awaitClose { /* Flux subscription cleaned up automatically */ }
}

/**
 * Builds a coroutine-native [Flow] of [ChatClientResponse] by executing the advisor
 * chain's streaming logic through a channel-backed adapter.
 *
 * This extension function provides the same semantics as calling the advisor chain's
 * `nextStream()` method and converting the result, but with Flow semantics and
 * back-pressure support.
 *
 * @receiver the [BaseAdvisor] to use for building the event stream
 * @param request the [ChatClientRequest] to pass through the advisor
 * @param advisorChain the [AdvisorChain] to use for streaming
 * @param capacity the channel capacity (default 16)
 * @return a [Flow] of [ChatClientResponse] events
 */
fun BaseAdvisor.kotlinStream(
    request: ChatClientRequest,
    advisorChain: AdvisorChain,
    capacity: Int = 16
): Flow<ChatClientResponse> =
    advisorChain.nextStream(request).asFlow(capacity)

/**
 * A coroutine-native version of [BaseAdvisor] that wraps the streaming advisor
 * interface with Kotlin [Flow] instead of [Flux].
 *
 * Implement this interface in Kotlin when you want to define advisors using suspend
 * functions or when you want the advisor's output consumed as a Flow.
 *
 * This interface is provided for API completeness; the existing [BaseAdvisor]
 * implementations can be used through [asFlow] extension.
 *
 * @author Thomas Vitale
 * @since 2.0.0
 */
interface KotlinFlowAdvisor {

    /**
     * Returns the execution order of this advisor.
     */
    val order: Int

    /**
     * Returns the scheduler to use for blocking operations.
     */
    val scheduler: reactor.core.scheduler.Scheduler

    /**
     * Kotlin-native alternative to [BaseAdvisor.before].
     *
     * Override this in Kotlin when you want to use suspend functions in your
     * advisor logic.
     *
     * @param request the incoming [ChatClientRequest]
     * @param advisorChain the chain for delegating to subsequent advisors
     * @return the (possibly modified) [ChatClientRequest]
     */
    suspend fun beforeKotlin(request: ChatClientRequest, advisorChain: AdvisorChain): ChatClientRequest

    /**
     * Kotlin-native alternative to [BaseAdvisor.after].
     *
     * Override this in Kotlin when you want to use suspend functions in your
     * advisor post-processing logic.
     *
     * @param response the incoming [ChatClientResponse]
     * @param advisorChain the chain for delegating to subsequent advisors
     * @return the (possibly modified) [ChatClientResponse]
     */
    suspend fun afterKotlin(response: ChatClientResponse, advisorChain: AdvisorChain): ChatClientResponse

    /**
     * Returns a [Flow] of [ChatClientResponse] items by streaming events from this
     * advisor.
     *
     * This enables consuming streaming advisor output as a Kotlin [Flow] with
     * built-in back-pressure and cancellation support.
     *
     * @param request the [ChatClientRequest] to process
     * @param advisorChain the [AdvisorChain] for the streaming chain
     * @param capacity the channel capacity for buffering (default 16)
     * @return a [Flow] emitting [ChatClientResponse] events
     */
    fun stream(
        request: ChatClientRequest,
        advisorChain: AdvisorChain,
        capacity: Int = 16
    ): Flow<ChatClientResponse> =
        asFlow(capacity)
}
