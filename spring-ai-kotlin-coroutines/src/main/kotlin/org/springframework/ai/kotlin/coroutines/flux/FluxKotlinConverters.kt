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

package org.springframework.ai.kotlin.coroutines.flux

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.singleOrNull
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Converters between reactive [Flux]/[Mono] and Kotlin [Flow].
 *
 * These extensions bridge the Reactor reactive world with Kotlin coroutines,
 * enabling seamless interoperability between Spring's reactive APIs and
 * idiomatic Kotlin code.
 *
 * @author Thomas Vitale
 * @since 2.0.0
 */

/**
 * Converts a [Flux] to a [Flow][kotlinx.coroutines.flow.Flow].
 *
 * This uses [kotlinx.coroutines.flow.flow][kotlinx.coroutines.flow.flow] builder internally,
 * subscribing to the [Flux] and emitting each value as a flow element.
 *
 * @receiver the reactive [Flux] to convert
 * @return a [Flow][kotlinx.coroutines.flow.Flow] emitting the same elements
 * @see Flux.kotlinFlow
 */
fun <T : Any> Flux<out T>.asKotlinFlow(): Flow<T> = kotlinx.coroutines.flow.flow {
    collect { value ->
        emit(value)
    }
}

/**
 * Converts a [Mono] to the first element of a [Flow][kotlinx.coroutines.flow.Flow].
 *
 * This extension lets you convert a single-element reactive type into the first
 * element of a coroutine flow, which can then be consumed with standard flow operators
 * or awaited with [first][kotlinx.coroutines.flow.Flow.first].
 *
 * @receiver the reactive [Mono] to convert
 * @return a [Flow][kotlinx.coroutines.flow.Flow] emitting at most one element
 * @see Mono.asKotlinFlow
 */
fun <T : Any> Mono<out T>.asKotlinFlow(): Flow<T> = asKotlinFlow()

/**
 * Suspending function that resolves a [Flux] to a [List] by collecting all elements.
 *
 * @receiver the reactive [Flux] to collect
 * @return a [List] containing all emitted elements
 * @see Flux.collectToList
 */
suspend fun <T : Any> Flux<out T>.collectToList(): List<T> {
    val result = mutableListOf<T>()
    collect { result.add(it) }
    return result
}

/**
 * Suspending function that resolves a [Flux] to a single value by returning the
 * first element, or `null` if the flux is empty.
 *
 * @receiver the reactive [Flux] to resolve
 * @return the first element emitted, or `null`
 */
suspend fun <T : Any> Flux<out T>.firstOrNull(): T? = asKotlinFlow().singleOrNull()

/**
 * Suspending function that resolves a [Mono] to its value, suspending until
 * the value is available or the mono completes empty.
 *
 * @receiver the reactive [Mono] to resolve
 * @return the value emitted by the mono, or `null` if it completes empty
 */
suspend fun <T : Any> Mono<out T>.await(): T? = flatMapMany { Flux.just(it.orElse(null)) }.asKotlinFlow().singleOrNull()

/**
 * Suspending function that resolves a [Mono] to its value, throwing if the mono
 * completes empty.
 *
 * @receiver the reactive [Mono] to resolve
 * @return the value emitted by the mono
 * @throws NoSuchElementException if the mono completes without emitting
 */
suspend fun <T : Any> Mono<out T>.awaitSingle(): T = asKotlinFlow().first()

/**
 * Collects all elements from this [Flow][kotlinx.coroutines.flow.Flow] into a [Flux].
 *
 * This is the inverse of [asKotlinFlow][Flux.asKotlinFlow] — it converts a Kotlin flow
 * into a reactive [Flux] that can be consumed by Reactor-based code.
 *
 * @receiver the [Flow][kotlinx.coroutines.flow.Flow] to convert
 * @return a [Flux] emitting all elements from the flow
 */
fun <T : Any> kotlinx.coroutines.flow.Flow<T>.asFlux(): Flux<T> = Flux.from(this)
