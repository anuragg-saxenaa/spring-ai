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

package org.springframework.ai.kotlin.coroutines.mcp

import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.assertj.core.api.Assertions.assertThat

/**
 * Unit tests for [McpToolKotlinExtensions] and suspend function detection.
 *
 * @author Thomas Vitale
 * @since 2.0.0
 */
class McpToolKotlinExtensionsTests {

    @Test
    fun `isKotlinSuspendFunction detects suspend methods`() {
        // Suspend functions have a Continuation as the last parameter
        val suspendMethod = SuspendToolClass::class.java.methods.first {
            it.name == "searchDocuments" && it.parameterCount == 2
        }
        assertThat(isKotlinSuspendFunction(suspendMethod)).isTrue()
    }

    @Test
    fun `isKotlinSuspendFunction returns false for regular methods`() {
        val regularMethod = SuspendToolClass::class.java.methods.first {
            it.name == "getStatus" && it.parameterCount == 0
        }
        assertThat(isKotlinSuspendFunction(regularMethod)).isFalse()
    }

    @Test
    fun `isKotlinSuspendFunction returns false for reactive Mono methods`() {
        val monoMethod = SuspendToolClass::class.java.methods.first {
            it.name == "getMonoValue"
        }
        assertThat(isKotlinSuspendFunction(monoMethod)).isFalse()
    }

    @Test
    fun `isKotlinMcpToolMethod returns true for suspend functions`() {
        val suspendMethod = SuspendToolClass::class.java.methods.first {
            it.name == "searchDocuments"
        }
        assertThat(isKotlinMcpToolMethod(suspendMethod)).isTrue()
    }

    @Test
    fun `isKotlinMcpToolMethod returns true for regular void methods`() {
        val voidMethod = SuspendToolClass::class.java.methods.first {
            it.name == "logMessage"
        }
        assertThat(isKotlinMcpToolMethod(voidMethod)).isTrue()
    }

    @Test
    fun `filterKotlinMcpToolMethod should include both suspend and non-suspend`() {
        val methods = SuspendToolClass::class.java.methods.filter {
            it.name in listOf("searchDocuments", "getStatus", "logMessage", "getMonoValue")
        }
        val filtered = methods.filter(filterKotlinMcpToolMethod())
        // All methods are valid for tool processing (suspend or regular)
        assertThat(filtered).hasSize(4)
    }

    @Test
    fun `isKotlinSuspendFunction returns false for null method`() {
        assertThat(isKotlinSuspendFunction(JavaRegularClass::class.java.methods.first())).isFalse()
    }

    @Test
    fun `isKotlinSuspendFunction with zero parameters returns false`() {
        val method = SuspendToolClass::class.java.methods.first {
            it.name == "getStatus" && it.parameterCount == 0
        }
        assertThat(isKotlinSuspendFunction(method)).isFalse()
    }
}

/**
 * Kotlin class with suspend functions for testing suspend function detection.
 */
class SuspendToolClass {

    // Suspend function with 2 parameters (plus injected Continuation)
    suspend fun searchDocuments(query: String, topK: Int): List<String> {
        return listOf("doc1", "doc2")
    }

    // Suspend function with no additional parameters (plus injected Continuation)
    suspend fun getStatus(): String {
        return "active"
    }

    // Regular void method
    fun logMessage(message: String) {
        println(message)
    }

    // Reactive Mono method (not a suspend function)
    fun getMonoValue(): reactor.core.publisher.Mono<String> {
        return reactor.core.publisher.Mono.just("mono")
    }

    // Suspend function returning Unit (void)
    suspend fun doAction(): Unit {
        // no-op
    }

    // Suspend function with complex return type
    suspend fun getComplexResult(): Map<String, Any> {
        return mapOf("key" to "value")
    }
}

/**
 * Java class with regular methods for testing non-suspend detection.
 */
class JavaRegularClass {
    fun regularMethod(): String = "not suspend"
    fun anotherMethod(x: Int): String = x.toString()
}