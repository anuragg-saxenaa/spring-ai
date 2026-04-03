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

package org.springframework.ai.mcp.server.common.autoconfigure;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.ai.mcp.AsyncMcpToolCallback;
import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;

/**
 * @author Daniel Garnier-Moiroux
 */
final class ToolCallbackUtils {

	private ToolCallbackUtils() {
	}

	static List<ToolCallback> aggregateToolCallbacks(ObjectProvider<List<ToolCallback>> toolCalls,
			List<ToolCallback> toolCallbackList, ObjectProvider<List<ToolCallbackProvider>> tcbProviderList,
			ObjectProvider<ToolCallbackProvider> tcbProviders, boolean includeMcpTools) {
		var allToolCallbacks = Stream.concat(toolCalls.stream().flatMap(List::stream), toolCallbackList.stream())
			.filter(toolCallback -> includeMcpTools || !isMcpToolCallback(toolCallback));

		var allCallbackProviders = Stream.concat(tcbProviderList.stream().flatMap(List::stream), tcbProviders.stream());
		var filteredProviders = allCallbackProviders.filter(provider -> includeMcpTools || !isMcpToolProvider(provider))
			.distinct();
		var toolCallbacksFromProviders = filteredProviders.map(pr -> List.of(pr.getToolCallbacks()))
			.flatMap(List::stream)
			.filter(Objects::nonNull);

		return Stream.concat(allToolCallbacks, toolCallbacksFromProviders).toList();
	}

	static boolean isMcpToolCallback(ToolCallback toolCallback) {
		return (toolCallback instanceof SyncMcpToolCallback) || (toolCallback instanceof AsyncMcpToolCallback);
	}

	static boolean isMcpToolProvider(ToolCallbackProvider tcbp) {
		return (tcbp instanceof org.springframework.ai.mcp.SyncMcpToolCallbackProvider)
				|| (tcbp instanceof org.springframework.ai.mcp.AsyncMcpToolCallbackProvider);
	}

}
