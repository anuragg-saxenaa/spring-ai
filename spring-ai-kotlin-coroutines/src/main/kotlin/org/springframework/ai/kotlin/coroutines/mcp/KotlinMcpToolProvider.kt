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
import java.util.Comparator
import java.util.function.BiFunction
import java.util.stream.Stream

import io.modelcontextprotocol.server.McpAsyncServerExchange
import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification
import io.modelcontextprotocol.spec.McpSchema
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations
import io.modelcontextprotocol.util.Utils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono

import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolMeta
import org.springframework.ai.mcp.annotation.common.MetaUtils
import org.springframework.ai.mcp.annotation.common.ToolMetaUtils
import org.springframework.ai.mcp.annotation.method.tool.ReturnMode
import org.springframework.ai.mcp.annotation.provider.tool.AbstractMcpToolProvider
import org.springframework.ai.mcp.annotation.method.tool.utils.McpJsonSchemaGenerator

/**
 * A tool provider that auto-detects and wraps Kotlin `@McpTool` suspend functions.
 *
 * This provider extends [AbstractMcpToolProvider] to add first-class support for
 * Kotlin suspend functions annotated with `@McpTool`. When a method is detected as
 * a suspend function (via [isKotlinSuspendFunction]), it is wrapped in a
 * [KotlinMcpToolMethodCallback] that bridges the reactive MCP callback interface
 * with the coroutine world via `runBlocking`.
 *
 * All other methods (reactive and imperative) are handled by the standard Spring AI
 * MCP infrastructure.
 *
 * Example usage:
 * ```kotlin
 * class WeatherTools {
 *     @McpTool(name = "getWeather", description = "Get weather for a location")
 *     suspend fun getWeather(location: String): String {
 *         return weatherService.fetch(location)
 *     }
 * }
 *
 * @Component
 * class MyMcpServer {
 *     private val toolProvider = KotlinMcpToolProvider(listOf(WeatherTools()))
 *
 *     fun getToolSpecs(): List<AsyncToolSpecification> = toolProvider.kotlinToolSpecifications
 * }
 * ```
 *
 * @author Thomas Vitale
 * @since 2.0.0
 */
class KotlinMcpToolProvider(toolObjects: List<Any>) : AbstractMcpToolProvider(toolObjects) {

    companion object {
        private val logger = LoggerFactory.getLogger(KotlinMcpToolProvider::class.java)
    }

    /**
     * Returns tool specifications for all `@McpTool`-annotated methods, including
     * Kotlin suspend functions.
     *
     * This is equivalent to [getToolSpecifications][AbstractMcpToolProvider.getToolSpecifications]
     * but with additional handling for Kotlin suspend functions. Suspend functions
     * that return a value are detected and wrapped in [KotlinMcpToolMethodCallback].
     * Suspend functions returning `Unit` (void) are also supported.
     *
     * @return list of [AsyncToolSpecification] for all detected tool methods
     */
    val kotlinToolSpecifications: List<AsyncToolSpecification>
        get() {
            val toolSpecs = this.toolObjects.stream()
                .map { toolObject ->
                    Stream.of(this.doGetClassMethods(toolObject))
                        .filter { method -> method.isAnnotationPresent(McpTool::class.java) }
                        .filter(McpPredicates.filterNonReactiveReturnTypeMethod())
                        .sorted(Comparator.comparing(Method::getName))
                        .map { mcpToolMethod ->
                            val toolJavaAnnotation = this.doGetMcpToolAnnotation(mcpToolMethod)
                            val toolName = if (Utils.hasText(toolJavaAnnotation.name())) {
                                toolJavaAnnotation.name()
                            }
                            else {
                                mcpToolMethod.name
                            }
                            val toolDescription = toolJavaAnnotation.description()
                            val inputSchema = McpJsonSchemaGenerator.generateForMethodInput(mcpToolMethod)

                            val toolMetaAnnotation = mcpToolMethod.getAnnotation(McpToolMeta::class.java)
                            val toolMeta = ToolMetaUtils.parseToolMeta(toolMetaAnnotation)
                            val meta = mergeMeta(MetaUtils.getMeta(toolJavaAnnotation.metaProvider()), toolMeta)

                            val toolBuilder = McpSchema.Tool.builder()
                                .name(toolName)
                                .description(toolDescription)
                                .inputSchema(this@JsonMapper, inputSchema)
                                .meta(meta)

                            var title = toolJavaAnnotation.title()

                            // Tool annotations
                            if (toolJavaAnnotation.annotations() != null) {
                                val toolAnnotations = toolJavaAnnotation.annotations()
                                toolBuilder.annotations(
                                    ToolAnnotations(
                                        toolAnnotations.title(),
                                        toolAnnotations.readOnlyHint(),
                                        toolAnnotations.destructiveHint(),
                                        toolAnnotations.idempotentHint(),
                                        toolAnnotations.openWorldHint(),
                                        null
                                    )
                                )
                                if (!Utils.hasText(title)) {
                                    title = toolAnnotations.title()
                                }
                            }

                            if (!Utils.hasText(title)) {
                                title = toolName
                            }
                            toolBuilder.title(title)

                            val tool = toolBuilder.build()

                            val isSuspendVoid = isKotlinSuspendFunction(mcpToolMethod) &&
                                (mcpToolMethod.returnType == Unit::class.java || mcpToolMethod.returnType == void.java)

                            val returnMode = if (isSuspendVoid) ReturnMode.VOID else ReturnMode.TEXT

                            // Select the appropriate callback based on method type
                            val callback: BiFunction<McpAsyncServerExchange, CallToolRequest, Mono<CallToolResult>> =
                                if (isKotlinSuspendFunction(mcpToolMethod)) {
                                    // Wrap suspend function in KotlinMcpToolMethodCallback
                                    KotlinMcpToolMethodCallback(
                                        returnMode,
                                        mcpToolMethod,
                                        toolObject,
                                        this@JsonMapper
                                    )
                                }
                                else {
                                    // Standard reactive callback for Mono/Flux methods
                                    org.springframework.ai.mcp.annotation.method.tool.AsyncMcpToolMethodCallback(
                                        returnMode,
                                        mcpToolMethod,
                                        toolObject,
                                        this@JsonMapper
                                    )
                                }

                            AsyncToolSpecification.builder()
                                .tool(tool)
                                .callHandler(callback)
                                .build()
                        }
                        .toList()
                }
                .flatMap { it.stream() }
                .toList()

            if (toolSpecs.isEmpty()) {
                logger.warn("No tool methods found in the provided tool objects: {}", this.toolObjects)
            }

            return toolSpecs
        }

    /**
     * Merges two metadata maps, with values from the second map taking precedence.
     */
    private fun mergeMeta(
        base: java.util.Map<String, Any>?,
        override: java.util.Map<String, Any>?
    ): java.util.Map<String, Any>? {
        if (base == null && override == null) {
            return null
        }
        if (base == null) {
            return override
        }
        if (override == null) {
            return base
        }
        val merged = java.util.HashMap(base)
        merged.putAll(override)
        return java.util.Collections.unmodifiableMap(merged)
    }

    private val jsonMapper: io.modelcontextprotocol.json.McpJsonMapper
        get() = this@JsonMapper
}
