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

package org.springframework.ai.mcp.annotation.common;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.springframework.ai.mcp.annotation.McpToolMeta;

/**
 * Utility methods for parsing and processing {@link McpToolMeta} annotations.
 *
 * @author Anurag Saxena
 * @since 1.1.0
 */
public final class ToolMetaUtils {

	private ToolMetaUtils() {
	}

	/**
	 * Parses the metadata from an {@link McpToolMeta} annotation.
	 *
	 * <p>
	 * Supported formats:
	 * <ul>
	 * <li>{@code "key=value"} - Parsed as a key-value pair</li>
	 * <li>{@code "Category"} - Parsed as a boolean with value "true"</li>
	 * </ul>
	 *
	 * @param toolMeta the annotation to parse (may be null)
	 * @return an unmodifiable map of parsed metadata key-value pairs, or empty map if
	 * annotation is null or has no values
	 */
	public static Map<String, Object> parseToolMeta(@SuppressWarnings("hidden") McpToolMeta toolMeta) {
		if (toolMeta == null) {
			return Collections.emptyMap();
		}

		String[] values = toolMeta.value();
		if (values == null || values.length == 0) {
			return Collections.emptyMap();
		}

		Map<String, Object> meta = new HashMap<>();
		for (String entry : values) {
			if (entry == null || entry.isBlank()) {
				continue;
			}

			String trimmed = entry.trim();

			// Check if it's a key=value pair
			int equalsIndex = trimmed.indexOf('=');
			if (equalsIndex > 0) {
				String key = trimmed.substring(0, equalsIndex).trim();
				String value = trimmed.substring(equalsIndex + 1).trim();
				if (!key.isEmpty()) {
					meta.put(key, value);
				}
			}
			else {
				// Treat as a boolean flag (present = true)
				meta.put(trimmed, true);
			}
		}

		return Collections.unmodifiableMap(meta);
	}

	/**
	 * Parses metadata from an array of strings in "key=value" or "flag" format.
	 * @param values the metadata strings
	 * @return an unmodifiable map of parsed metadata
	 */
	public static Map<String, Object> parseToolMeta(String[] values) {
		if (values == null || values.length == 0) {
			return Collections.emptyMap();
		}

		Map<String, Object> meta = new HashMap<>();
		for (String entry : values) {
			if (entry == null || entry.isBlank()) {
				continue;
			}

			String trimmed = entry.trim();
			int equalsIndex = trimmed.indexOf('=');
			if (equalsIndex > 0) {
				String key = trimmed.substring(0, equalsIndex).trim();
				String value = trimmed.substring(equalsIndex + 1).trim();
				if (!key.isEmpty()) {
					meta.put(key, value);
				}
			}
			else {
				meta.put(trimmed, true);
			}
		}

		return Collections.unmodifiableMap(meta);
	}

}
