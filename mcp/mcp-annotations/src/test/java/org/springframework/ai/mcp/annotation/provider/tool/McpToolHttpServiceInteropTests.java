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

package org.springframework.ai.mcp.annotation.provider.tool;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.Test;

import org.springframework.ai.mcp.annotation.McpTool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@code @McpTool} + HTTP Service Client interface interoperability. Verifies
 * that {@link SyncMcpToolProvider} can discover {@code @McpTool}-annotated methods
 * declared on interfaces (simulating HTTP Service Client proxies created by
 * {@code @ImportHttpServices}).
 *
 * @author Anurag Saxena
 * @see <a href="https://github.com/spring-projects/spring-ai/issues/5823">Issue #5823</a>
 */
public class McpToolHttpServiceInteropTests {

	@Target({ ElementType.METHOD })
	@Retention(RetentionPolicy.RUNTIME)
	private @interface FakeHttpExchange {

		String value() default "";

	}

	@Target({ ElementType.METHOD })
	@Retention(RetentionPolicy.RUNTIME)
	private @interface FakeGetExchange {

		String value() default "";

	}

	@Test
	void shouldDiscoverMcpToolFromInterfaceMethod() {
		RemoteServiceWithMcpTool toolObject = new RemoteServiceImpl();
		SyncMcpToolProvider provider = new SyncMcpToolProvider(List.of(toolObject));

		List<SyncToolSpecification> toolSpecs = provider.getToolSpecifications();

		assertThat(toolSpecs).hasSize(3);
		assertThat(toolSpecs).extracting(s -> s.tool().name())
			.containsExactlyInAnyOrder("get-elements", "get-element-by-id", "count-elements");
	}

	@Test
	void shouldInvokeToolMethodOnInterfaceAnnotatedMethod() {
		RemoteServiceWithMcpTool toolObject = new RemoteServiceImpl();
		SyncMcpToolProvider provider = new SyncMcpToolProvider(List.of(toolObject));
		List<SyncToolSpecification> toolSpecs = provider.getToolSpecifications();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		SyncToolSpecification getElementsTool = toolSpecs.stream()
			.filter(s -> s.tool().name().equals("get-elements"))
			.findFirst()
			.orElseThrow();

		CallToolResult result = getElementsTool.callHandler()
			.apply(exchange, new CallToolRequest("get-elements", Map.of()));

		assertThat(result).isNotNull();
		assertThat(result.isError()).isFalse();
		assertThat(result.content()).hasSize(1);
		assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
		String text = ((TextContent) result.content().get(0)).text();
		assertThat(text).contains("alpha");
		assertThat(text).contains("beta");
		assertThat(text).contains("gamma");
	}

	@Test
	void shouldPassToolArgumentsToInterfaceMethod() {
		RemoteServiceWithMcpTool toolObject = new RemoteServiceImpl();
		SyncMcpToolProvider provider = new SyncMcpToolProvider(List.of(toolObject));
		List<SyncToolSpecification> toolSpecs = provider.getToolSpecifications();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		SyncToolSpecification tool = toolSpecs.stream()
			.filter(s -> s.tool().name().equals("get-element-by-id"))
			.findFirst()
			.orElseThrow();

		CallToolResult result = tool.callHandler()
			.apply(exchange, new CallToolRequest("get-element-by-id", Map.of("id", "42")));

		assertThat(result).isNotNull();
		assertThat(result.isError()).isFalse();
		String text = ((TextContent) result.content().get(0)).text();
		assertThat(text).isEqualTo("Element[42]");
	}

	@Test
	void shouldReturnPrimitiveValueFromInterfaceMethod() {
		RemoteServiceWithMcpTool toolObject = new RemoteServiceImpl();
		SyncMcpToolProvider provider = new SyncMcpToolProvider(List.of(toolObject));
		List<SyncToolSpecification> toolSpecs = provider.getToolSpecifications();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		SyncToolSpecification tool = toolSpecs.stream()
			.filter(s -> s.tool().name().equals("count-elements"))
			.findFirst()
			.orElseThrow();

		CallToolResult result = tool.callHandler().apply(exchange, new CallToolRequest("count-elements", Map.of()));

		assertThat(result).isNotNull();
		assertThat(result.isError()).isFalse();
		assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("3");
	}

	@Test
	void shouldExtractToolDescriptionFromInterfaceAnnotation() {
		RemoteServiceWithMcpTool toolObject = new RemoteServiceImpl();
		SyncMcpToolProvider provider = new SyncMcpToolProvider(List.of(toolObject));
		List<SyncToolSpecification> toolSpecs = provider.getToolSpecifications();

		SyncToolSpecification getElementsTool = toolSpecs.stream()
			.filter(s -> s.tool().name().equals("get-elements"))
			.findFirst()
			.orElseThrow();

		assertThat(getElementsTool.tool().description()).isEqualTo("Lists all available elements.");
	}

	@Test
	void shouldHandleInterfaceWithOnlyMcpToolMethods() {
		interface MinimalService {

			@McpTool(name = "ping", description = "Ping the remote service.")
			@FakeHttpExchange
			String ping();

		}

		class MinimalServiceImpl implements MinimalService {

			@Override
			public String ping() {
				return "pong";
			}

		}

		SyncMcpToolProvider provider = new SyncMcpToolProvider(List.of(new MinimalServiceImpl()));
		List<SyncToolSpecification> toolSpecs = provider.getToolSpecifications();

		assertThat(toolSpecs).hasSize(1);
		assertThat(toolSpecs.get(0).tool().name()).isEqualTo("ping");
	}

	@Test
	void shouldNotDuplicateMethodsWhenSameSignatureExistsOnClassAndInterface() {
		interface IHasTool {

			@McpTool(name = "shared-tool", description = "Shared tool from interface.")
			String sharedTool(String input);

		}

		class CHasTool implements IHasTool {

			@Override
			public String sharedTool(String input) {
				return "class: " + input;
			}

		}

		SyncMcpToolProvider provider = new SyncMcpToolProvider(List.of(new CHasTool()));
		List<SyncToolSpecification> toolSpecs = provider.getToolSpecifications();

		assertThat(toolSpecs).hasSize(1);
		assertThat(toolSpecs.get(0).tool().name()).isEqualTo("shared-tool");
	}

	@Test
	void shouldUseDoGetAllMethodsFromHierarchyForProxyLikeObjects() throws Exception {
		AbstractMcpToolProvider provider = new SyncMcpToolProvider(List.of());
		java.lang.reflect.Method method = AbstractMcpToolProvider.class
			.getDeclaredMethod("doGetAllMethodsFromHierarchy", Object.class);
		method.setAccessible(true);

		RemoteServiceWithMcpTool impl = new RemoteServiceImpl();
		Method[] interfaceMethods = (Method[]) method.invoke(provider, impl);

		boolean foundGetElements = false;
		boolean foundGetElementById = false;
		boolean foundCountElements = false;

		for (Method m : interfaceMethods) {
			if (m.getName().equals("getElements") && m.isAnnotationPresent(McpTool.class)) {
				foundGetElements = true;
			}
			if (m.getName().equals("getElementById") && m.isAnnotationPresent(McpTool.class)) {
				foundGetElementById = true;
			}
			if (m.getName().equals("countElements") && m.isAnnotationPresent(McpTool.class)) {
				foundCountElements = true;
			}
		}

		assertThat(foundGetElements).isTrue();
		assertThat(foundGetElementById).isTrue();
		assertThat(foundCountElements).isTrue();
	}

	// Inner types must come after all test methods (InnerTypeLast checkstyle rule)

	interface RemoteServiceWithMcpTool {

		@McpTool(name = "get-elements", description = "Lists all available elements.")
		@FakeHttpExchange
		@FakeGetExchange("/elements")
		List<String> getElements();

		@McpTool(name = "get-element-by-id", description = "Retrieves a single element by its ID.")
		@FakeHttpExchange
		@FakeGetExchange("/elements/{id}")
		String getElementById(String id);

		@McpTool(name = "count-elements", description = "Returns the total number of elements.")
		@FakeHttpExchange
		@FakeGetExchange("/elements/count")
		int countElements();

	}

	static class RemoteServiceImpl implements RemoteServiceWithMcpTool {

		@Override
		public List<String> getElements() {
			return List.of("alpha", "beta", "gamma");
		}

		@Override
		public String getElementById(String id) {
			return "Element[" + id + "]";
		}

		@Override
		public int countElements() {
			return 3;
		}

	}

}
