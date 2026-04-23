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

package org.springframework.ai.mcp;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * Extended tool definition for MCP tools that includes metadata for client-side tool
 * filtering.
 *
 * <p>
 * This class wraps Spring AI's {@link org.springframework.ai.tool.definition.ToolDefinition}
 * and adds an optional {@code meta} field that can be used by MCP clients to filter tools
 * before sending them to the LLM. This addresses the token consumption and LLM confusion
 * issues when a project has many MCP tools.
 * </p>
 *
 * <p>
 * The metadata field follows the MCP specification's per-tool {@code meta} field, enabling:
 * <ul>
 * <li>Categorization by tool type (e.g., "RealTimeAnalysis", "HistoricalAnalysis")</li>
 * <li>Custom filtering criteria for different LLM contexts</li>
 * <li>Reduced token consumption by filtering unnecessary tools</li>
 * </ul>
 * </p>
 *
 * @author Anurag Saxena
 * @since 1.1.0
 * @see McpToolFilter
 * @see org.springframework.ai.tool.definition.ToolDefinition
 */
public class McpToolDefinition {

	private final org.springframework.ai.tool.definition.ToolDefinition baseDefinition;

	private final Map<String, Object> meta;

	/**
	 * Creates a new McpToolDefinition with the given base definition and metadata.
	 * @param baseDefinition the underlying Spring AI tool definition
	 * @param meta the MCP metadata for filtering (may be null)
	 */
	public McpToolDefinition(org.springframework.ai.tool.definition.ToolDefinition baseDefinition,
			@Nullable Map<String, Object> meta) {
		this.baseDefinition = baseDefinition;
		this.meta = meta != null ? Collections.unmodifiableMap(new HashMap<>(meta)) : Collections.emptyMap();
	}

	/**
	 * Creates a new McpToolDefinition with empty metadata.
	 * @param baseDefinition the underlying Spring AI tool definition
	 */
	public McpToolDefinition(org.springframework.ai.tool.definition.ToolDefinition baseDefinition) {
		this(baseDefinition, Collections.emptyMap());
	}

	/**
	 * Returns the tool name.
	 * @return the tool name
	 */
	public String name() {
		return this.baseDefinition.name();
	}

	/**
	 * Returns the tool description.
	 * @return the tool description
	 */
	public String description() {
		return this.baseDefinition.description();
	}

	/**
	 * Returns the input schema as a JSON string.
	 * @return the input schema
	 */
	public String inputSchema() {
		return this.baseDefinition.inputSchema();
	}

	/**
	 * Returns the MCP metadata for this tool, used for client-side filtering.
	 * @return an unmodifiable map of metadata key-value pairs
	 */
	public Map<String, Object> meta() {
		return this.meta;
	}

	/**
	 * Checks if this tool has any metadata.
	 * @return true if metadata is present
	 */
	public boolean hasMeta() {
		return !this.meta.isEmpty();
	}

	/**
	 * Gets a metadata value by key.
	 * @param key the metadata key
	 * @return the metadata value, or null if not present
	 */
	@Nullable
	public Object getMeta(String key) {
		return this.meta.get(key);
	}

	/**
	 * Returns the underlying Spring AI tool definition.
	 * @return the base tool definition
	 */
	public org.springframework.ai.tool.definition.ToolDefinition getBaseDefinition() {
		return this.baseDefinition;
	}

	/**
	 * Creates a new McpToolDefinition from an existing tool definition with no metadata.
	 * @param toolDefinition the base tool definition
	 * @return a new McpToolDefinition
	 */
	public static McpToolDefinition from(org.springframework.ai.tool.definition.ToolDefinition toolDefinition) {
		return new McpToolDefinition(toolDefinition);
	}

	/**
	 * Creates a new McpToolDefinition from an existing tool definition with the given
	 * metadata.
	 * @param toolDefinition the base tool definition
	 * @param meta the MCP metadata
	 * @return a new McpToolDefinition
	 */
	public static McpToolDefinition from(org.springframework.ai.tool.definition.ToolDefinition toolDefinition,
			Map<String, Object> meta) {
		return new McpToolDefinition(toolDefinition, meta);
	}

}
