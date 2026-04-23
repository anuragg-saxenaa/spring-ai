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

package org.springframework.ai.mcp.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to declare MCP tool metadata for client-side filtering.
 *
 * <p>
 * This annotation can be applied to methods annotated with {@link McpTool} to provide
 * custom metadata that enables filtering tools based on their characteristics. The
 * metadata follows the MCP specification's per-tool {@code meta} field.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>
 * &#64;McpTool(name = "realtimeWeather")
 * &#64;McpToolMeta({"Type=RealTimeAnalysis", "Category=Weather"})
 * public WeatherData getRealtimeWeather(String location) {
 *     // ...
 * }
 *
 * &#64;McpTool(name = "historicalWeather")
 * &#64;McpToolMeta({"Type=HistoricalAnalysis", "Category=Weather"})
 * public WeatherData getHistoricalWeather(String location, Date startDate, Date endDate) {
 *     // ...
 * }
 * </pre>
 *
 * <p>
 * Then, in the MCP client, you can filter tools by metadata:
 * </p>
 *
 * <pre>
 * McpToolFilter filter = (connectionInfo, tool) -> {
 *     Map&lt;String, Object&gt; meta = tool.meta();
 *     if (meta == null) return true;
 *     Object type = meta.get("Type");
 *     return "RealTimeAnalysis".equals(type);
 * };
 * </pre>
 *
 * @author Anurag Saxena
 * @since 1.1.0
 * @see McpTool
 * @see McpToolDefinition
 * @see org.springframework.ai.mcp.McpToolFilter
 */
@Target({ ElementType.METHOD, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpToolMeta {

	/**
	 * Metadata key-value pairs for the tool.
	 *
	 * <p>
	 * Supported formats:
	 * <ul>
	 * <li>{@code "key=value"} - Simple key-value pair</li>
	 * <li>{@code "Category"} - Boolean metadata (presence indicates true)</li>
	 * </ul>
	 *
	 * @return the metadata as an array of strings
	 */
	String[] value() default {};

}
