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

package org.springframework.ai.mcp.annotation.integration;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.spring.SyncMcpAnnotationProviders;
import org.springframework.ai.mcp.annotation.spring.StatelessSyncMcpAnnotationProviders;
import org.springframework.ai.mcp.annotation.provider.prompt.SyncStatelessMcpPromptProvider;
import org.springframework.ai.mcp.annotation.provider.resource.SyncStatelessMcpResourceProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test to verify that MCP providers don't emit WARN logs when only tools are
 * registered (no resources or prompts).
 *
 * See: <a href="https://github.com/spring-projects/spring-ai/issues/4694">Issue #4694</a>
 */
public class NoWarnsWhenOnlyToolsRegisteredTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(ToolsOnlyConfig.class);

	@Test
	void noWarnLogsWhenOnlyToolsRegistered() {
		ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
		listAppender.start();

		// Get the logger for the provider classes
		ch.qos.logback.classic.Logger statelessPromptProviderLogger = (ch.qos.logback.classic.Logger) LoggerFactory
			.getLogger(SyncStatelessMcpPromptProvider.class);
		ch.qos.logback.classic.Logger statelessResourceProviderLogger = (ch.qos.logback.classic.Logger) LoggerFactory
			.getLogger(SyncStatelessMcpResourceProvider.class);

		// Add appender to capture logs
		statelessPromptProviderLogger.addAppender(listAppender);
		statelessResourceProviderLogger.addAppender(listAppender);

		// Set log levels to capture WARN
		statelessPromptProviderLogger.setLevel(Level.WARN);
		statelessResourceProviderLogger.setLevel(Level.WARN);

		// Get provider and trigger the log warning
		SyncStatelessMcpPromptProvider promptProvider = new SyncStatelessMcpPromptProvider(
				List.of(new ToolsOnlyComponent()));
		promptProvider.getPromptSpecifications();

		SyncStatelessMcpResourceProvider resourceProvider = new SyncStatelessMcpResourceProvider(
				List.of(new ToolsOnlyComponent()));
		resourceProvider.getResourceSpecifications();
		resourceProvider.getResourceTemplateSpecifications();

		// Verify no WARN logs
		List<ILoggingEvent> warnEvents = listAppender.list;
		for (ILoggingEvent event : warnEvents) {
			assertThat(event.getLevel()).isNotEqualTo(Level.WARN);
		}

		assertThat(warnEvents).isEmpty();
	}

	@Configuration
	static class ToolsOnlyConfig {

	}

	static class ToolsOnlyComponent {

		@McpTool(name = "testTool", description = "A test tool with no resources or prompts")
		public String testTool(@McpToolParam(description = "Input parameter") String input) {
			return "result: " + input;
		}

	}

}