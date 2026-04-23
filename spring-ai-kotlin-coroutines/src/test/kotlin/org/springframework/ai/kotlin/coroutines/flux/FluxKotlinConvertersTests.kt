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
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.assertj.core.api.Assertions.assertThat
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Unit tests for [FluxKotlinConverters].
 *
 * @author Thomas Vitale
 * @since 2.0.0
 */
class FluxKotlinConvertersTests {

    @Test
    fun `asKotlinFlow should convert Flux to Flow`() = runBlocking<Unit> {
        val flux = Flux.just("hello", "world", "test")
        val flow: Flow<String> = flux.asKotlinFlow()
        val result = flow.toList()
        assertThat(result).containsExactly("hello", "world", "test")
    }

    @Test
    fun `asKotlinFlow should work with Mono`() = runBlocking<Unit> {
        val mono = Mono.just("single value")
        val flow: Flow<String> = mono.asKotlinFlow()
        val result = flow.first()
        assertThat(result).isEqualTo("single value")
    }

    @Test
    fun `asKotlinFlow should handle empty Flux`() = runBlocking<Unit> {
        val flux = Flux.empty<String>()
        val flow: Flow<String> = flux.asKotlinFlow()
        val result = flow.toList()
        assertThat(result).isEmpty()
    }

    @Test
    fun `asKotlinFlow should propagate errors`() = runBlocking<Unit> {
        val flux = Flux.error<String>(IllegalStateException("boom"))
        val flow: Flow<String> = flux.asKotlinFlow()

        assertThrows<IllegalStateException> {
            runBlocking { flow.toList() }
        }
    }

    @Test
    fun `collectToList should gather all elements`() = runBlocking<Unit> {
        val flux = Flux.range(1, 100)
        val list: List<Int> = flux.collectToList()
        assertThat(list).hasSize(100)
        assertThat(list.first()).isEqualTo(1)
        assertThat(list.last()).isEqualTo(100)
    }

    @Test
    fun `collectToList should return empty list for empty Flux`() = runBlocking<Unit> {
        val flux = Flux.empty<Int>()
        val list: List<Int> = flux.collectToList()
        assertThat(list).isEmpty()
    }

    @Test
    fun `firstOrNull should return first element`() = runBlocking<Unit> {
        val flux = Flux.just("first", "second", "third")
        val first: String? = flux.firstOrNull()
        assertThat(first).isEqualTo("first")
    }

    @Test
    fun `firstOrNull should return null for empty Flux`() = runBlocking<Unit> {
        val flux = Flux.empty<String>()
        val first: String? = flux.firstOrNull()
        assertThat(first).isNull()
    }

    @Test
    fun `await should resolve Mono to value`() = runBlocking<Unit> {
        val mono = Mono.just("resolved value")
        val value: String? = mono.await()
        assertThat(value).isEqualTo("resolved value")
    }

    @Test
    fun `await should return null for empty Mono`() = runBlocking<Unit> {
        val mono = Mono.empty<String>()
        val value: String? = mono.await()
        assertThat(value).isNull()
    }

    @Test
    fun `awaitSingle should return value or throw`() = runBlocking<Unit> {
        val mono = Mono.just("single")
        val value: String = mono.awaitSingle()
        assertThat(value).isEqualTo("single")
    }

    @Test
    fun `awaitSingle should throw for empty Mono`() = runBlocking<Unit> {
        val mono = Mono.empty<String>()
        assertThrows<NoSuchElementException> {
            runBlocking { mono.awaitSingle() }
        }
    }

    @Test
    fun `asFlux should convert Flow to Flux`() = runBlocking<Unit> {
        val flow = kotlinx.coroutines.flow.flowOf("a", "b", "c")
        val flux = flow.asFlux()
        val result = flux.collectList().block()
        assertThat(result).containsExactly("a", "b", "c")
    }

    @Test
    fun `asFlux should work with empty Flow`() = runBlocking<Unit> {
        val flow = kotlinx.coroutines.flow.flowOf<String>()
        val flux = flow.asFlux()
        val result = flux.collectList().block()
        assertThat(result).isEmpty()
    }

    @Test
    fun `asFlux should propagate Flow errors to Flux`() = runBlocking<Unit> {
        val flow = kotlinx.coroutines.flow.flow {
            throw IllegalStateException("flow error")
        }
        val flux = flow.asFlux()

        assertThrows<IllegalStateException> {
            flux.blockFirst()
        }
    }

    @Test
    fun `asKotlinFlow should handle large Flux`() = runBlocking<Unit> {
        val flux = Flux.range(1, 10_000)
        val flow: Flow<Int> = flux.asKotlinFlow()
        val result = flow.toList()
        assertThat(result).hasSize(10_000)
        assertThat(result.first()).isEqualTo(1)
        assertThat(result.last()).isEqualTo(10_000)
    }

    @Test
    fun `asKotlinFlow with various data types`() = runBlocking<Unit> {
        // Integer Flux
        val intFlux = Flux.just(1, 2, 3)
        assertThat(intFlux.asKotlinFlow().toList()).containsExactly(1, 2, 3)

        // Custom object Flux
        val objFlux = Flux.just(Pair("key", "value"), Pair("a", "b"))
        val list = objFlux.asKotlinFlow().toList()
        assertThat(list).hasSize(2)
        assertThat(list[0].first).isEqualTo("key")
    }
}

/**
 * Custom data class for testing Flow conversion with complex types.
 */
private data class CustomData(val name: String, val value: Int)