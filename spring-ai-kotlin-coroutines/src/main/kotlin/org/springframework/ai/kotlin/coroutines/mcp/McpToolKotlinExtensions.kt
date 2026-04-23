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

import io.modelcontextprotocol.server.McpAsyncServerExchange
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import kotlinx.coroutines.runBlocking
import reactor.core.publisher.Mono

import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.common.McpPredicates
import org.springframework.ai.mcp.annotation.method.tool.AbstractAsyncMcpToolMethodCallback
import org.springframework.ai.mcp.annotation.method.tool.ReturnMode

/**
 * Utilities for working with `@McpTool` methods in Kotlin, including auto-detection
 * and wrapping of Kotlin suspend functions for use with Spring AI MCP.
 *
 * Kotlin suspend functions cannot be directly invoked via Java reflection because they
 * have an additional `$completion` parameter injected by the Kotlin compiler at the
 * bytecode level. This package provides utilities to detect, wrap, and invoke suspend
 * functions so they can be used seamlessly with Spring AI MCP's `@McpTool` annotation.
 *
 * Example usage:
 * ```kotlin
 * class MyKotlinTools {
 *     @McpTool(name = "search", description = "Search for documents")
 *     suspend fun searchDocuments(query: String): List<String> {
 *         // This is a Kotlin suspend function — auto-detected and wrapped
 *         return searchService.find(query)
 *     }
 * }
 *
 * // Register with MCP
 * val provider = KotlinMcpToolProvider(listOf(MyKotlinTools()))
 * ```
 *
 * @author Thomas Vitale
 * @since 2.0.0
 */

/**
 * Checks whether the given Java method is actually a Kotlin suspend function.
 *
 * Suspend functions in Kotlin compile down to methods with a `Continuation` parameter.
 * This utility detects that pattern by checking whether the last parameter is of type
 * `kotlin.coroutines.Continuation`.
 *
 * Note: This only detects suspend functions that have been called from Java. When
 * introspecting Kotlin bytecode at runtime, the method signature includes an additional
 * synthetic parameter.
 *
 * @param method the Java method to check
 * @return `true` if the method is a Kotlin suspend function
 */
fun isKotlinSuspendFunction(method: Method): Boolean {
    val parameterTypes = method.parameterTypes
    if (parameterTypes.isEmpty()) {
        return false
    }
    val lastParam = parameterTypes[parameterTypes.lastIndex]
    return lastParam.name == "kotlin.coroutines.Continuation"
}

/**
 * Checks whether a method is a valid `@McpTool` method for Kotlin.
 *
 * Valid Kotlin `@MtoTool` methods are either:
 * - Kotlin suspend functions (detected via [isKotlinSuspendFunction])
 * - Regular methods returning `Mono`, `Flux`, or any non-reactive type
 * (handled by the standard Spring AI MCP infrastructure)
 *
 * This predicate matches both categories, enabling suspend functions to be processed
 * alongside standard reactive return types.
 *
 * @param method the method to check
 * @return `true` if the method is a valid Kotlin `@McpTool` method
 */
fun isKotlinMcpToolMethod(method: Method): Boolean =
    isKotlinSuspendFunction(method) || !McpPredicates.isReactiveReturnType.test(method)

/**
 * Filters methods to retain only those valid for Kotlin `@McpTool` processing,
 * including suspend functions.
 *
 * This is the Kotlin-equivalent of [McpPredicates.filterNonReactiveReturnTypeMethod]
 * but with explicit support for Kotlin suspend functions.
 *
 * @return a [kotlin.jvm.functions.Function1] predicate matching valid Kotlin tool methods
 */
fun filterKotlinMcpToolMethod(): (Method) -> Boolean = { method ->
    if (isKotlinSuspendFunction(method)) {
        true
    }
    else if (McpPredicates.isReactiveReturnType.test(method)) {
        true
    }
    else {
        true
    }
}

/**
 * A callback for invoking Kotlin suspend functions from the MCP tool infrastructure.
 *
 * This extends [AbstractAsyncMcpToolMethodCallback] to handle the special case of
 * Kotlin suspend functions, which compile to methods with a `Continuation` parameter.
 * When invoked via reflection, the continuation is injected by the Kotlin coroutines
 * runtime.
 *
 * The callback wraps the suspend function call in [runBlocking] to bridge the async
 * callback context (which works with `Mono<CallToolResult>`) with the suspending
 * function world.
 *
 * @author Thomas Vitale
 * @since 2.0.0
 */
class KotlinMcpToolMethodCallback(
    returnMode: ReturnMode,
    toolMethod: Method,
    toolObject: Any,
    toolCallExceptionClass: Class<out Exception>
) : AbstractAsyncMcpToolMethodCallback<McpAsyncServerExchange, Any>(returnMode, toolMethod, toolObject, toolCallExceptionClass) {

    init {
        require(isKotlinSuspendFunction(toolMethod)) {
            "Method ${toolMethod.name} is not a Kotlin suspend function. Use KotlinMcpToolMethodCallback only for suspend functions."
        }
    }

    /**
     * Invokes the suspend function by running it in [runBlocking].
     *
     * The method is called directly; Kotlin's coroutines runtime injects the
     * continuation parameter transparently during reflection-based invocation.
     * We wrap it in `runBlocking` so that the result can be returned as a `Mono`
     * from the reactive callback context.
     *
     * @param args the arguments to pass to the method (excluding the continuation,
     * which is injected by Kotlin)
     * @return the result of the method invocation, wrapped in a `Mono`
     */
    override fun callMethod(args: Array<out Any?>): Any {
        // Filter out the continuation argument if present
        val realArgs = args.filterNot { it == null && it?.javaClass?.name == "kotlin.coroutines.Continuation" }.toTypedArray()

        return runBlocking {
            @Suppress("UNCHECKED_CAST")
            invokeSuspendFunction(toolMethod, toolObject, realArgs)
        }
    }

    /**
     * Invokes a Kotlin suspend function via reflection.
     *
     * This bridges the gap between Java reflection (which sees a method with an
     * extra `Continuation` parameter) and Kotlin coroutines (which inject this
     * parameter transparently when calling through reflection).
     *
     * @param method the suspend method to invoke
     * @param target the target object
     * @param args the arguments (excluding the continuation)
     * @return the result of the suspend function
     */
    @Suppress("UNCHECKED_CAST")
    private suspend fun invokeSuspendFunction(method: Method, target: Any, args: Array<out Any?>): Any {
        return kotlinx.coroutines.suspendCoroutine { continuation ->
            try {
                // Kotlin injects the continuation transparently when called via reflection.
                // We create an unchecked continuation to let Kotlin handle the dispatch.
                val result = method.invoke(target, *args)
                continuation.resumeWith(Result.success(result))
            }
            catch (e: Throwable) {
                continuation.resumeWith(Result.failure(e))
            }
        }
    }

    override fun convertToCallToolResult(result: Any?): Mono<CallToolResult> =
        super.convertToCallToolResult(result)
}
