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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.kotlin.coroutines.chat

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import reactor.core.publisher.Flux

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.ChatClientRequest
import org.springframework.ai.chat.client.ChatClientRequestSpec
import org.springframework.ai.chat.client.ChatClientResponse
import org.springframework.ai.chat.client.DefaultChatClient
import org.springframework.ai.chat.model.ChatResponse

/**
 * Coroutines extensions for [ChatClient].
 *
 * Provides Flow-based alternatives to the reactive Flux APIs, enabling idiomatic
 * Kotlin coroutines usage when working with Spring AI's ChatClient.
 *
 * @author Thomas Vitale
 * @since 2.0.0
 */

/**
 * Returns a [Flow] of [ChatClientResponse] by collecting from the reactive [Flux].
 *
 * This is the Kotlin coroutines equivalent of [ChatClient.ChatClientRequestSpec.stream]
 * and [ChatClient.StreamResponseSpec.chatClientResponse], but returning a
 * [Flow] instead of a [Flux].
 *
 * Example usage:
 * ```kotlin
 * chatClient.prompt("Hello")
 *     .kotlinStream()
 *     .collect { response -> println(response.chatResponse()) }
 * ```
 *
 * @return a [Flow] emitting [ChatClientResponse] items as they arrive
 */
fun ChatClient.ChatClientRequestSpec.kotlinStream(): Flow<ChatClientResponse> {
    return stream().chatClientResponse().kotlinFlow()
}

/**
 * Returns a [Flow] of [ChatResponse] by collecting from the reactive [Flux].
 *
 * This is the Kotlin coroutines equivalent of [ChatClient.StreamResponseSpec.chatResponse],
 * but returning a [Flow] instead of a [Flux].
 *
 * @return a [Flow] emitting [ChatResponse] items as they arrive
 */
fun ChatClient.ChatClientRequestSpec.kotlinStreamResponse(): Flow<ChatResponse> {
    return stream().chatResponse().kotlinFlow()
}

/**
 * Returns a [Flow] of [String] content items by collecting from the reactive [Flux].
 *
 * This is the Kotlin coroutines equivalent of [ChatClient.StreamResponseSpec.content],
 * but returning a [Flow] instead of a [Flux].
 *
 * @return a [Flow] emitting content strings as they arrive
 */
fun ChatClient.ChatClientRequestSpec.kotlinStreamContent(): Flow<String> {
    return stream().content().kotlinFlow()
}

/**
 * Converts this [Flux] to a [Flow][kotlinx.coroutines.flow.Flow].
 *
 * Uses [kotlinx.coroutines.flow.flow][kotlinx.coroutines.flow.flow] builder with
 * [collect][reactor.core.publisher.Flux.collect] to bridge reactive and coroutine worlds.
 *
 * @receiver the reactive [Flux] to convert
 * @return a [Flow][kotlinx.coroutines.flow.Flow] emitting the same elements
 */
fun <T : Any> Flux<out T>.kotlinFlow(): Flow<T> = kotlinx.coroutines.flow.flow {
    collect { value ->
        emit(value)
    }
}

/**
 * Collects all elements from this [Flux] into a [List].
 *
 * This extension is useful when you need to gather all streamed responses
 * before processing, for example in tests or when you need the complete result.
 *
 * @receiver the reactive [Flux] to collect
 * @return a [List] containing all emitted elements
 */
suspend fun <T : Any> Flux<out T>.collectToList(): List<T> {
    val result = mutableListOf<T>()
    collect { result.add(it) }
    return result
}
