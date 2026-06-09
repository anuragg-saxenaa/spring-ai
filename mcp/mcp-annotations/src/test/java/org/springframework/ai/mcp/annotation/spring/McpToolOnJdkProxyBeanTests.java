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

package org.springframework.ai.mcp.annotation.spring;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.method.tool.utils.McpJsonSchemaGenerator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for issue #5823: {@link McpTool}-annotated methods declared on
 * {@link Proxy}-backed beans (such as those produced by Spring's HttpServiceClient for
 * {@code @HttpExchange} interfaces) must be discovered as MCP tool specifications.
 *
 * <p>
 * Before the fix, {@link AnnotationProviderUtil#beanMethods(Object)} reflected on the
 * proxy class only and missed the user-declared interface methods. The annotation lookup
 * was therefore never triggered, and the methods were silently dropped.
 */
class McpToolOnJdkProxyBeanTests {

	@Test
	void beanMethodsOnJdkProxyShouldIncludeInterfaceMethods() {
		Object proxy = Proxy.newProxyInstance(McpToolOnInterfaceProbe.class.getClassLoader(),
				new Class<?>[] { McpToolOnInterfaceProbe.class }, (p, method, args) -> null);

		Method[] methods = AnnotationProviderUtil.beanMethods(proxy);

		assertThat(methods).extracting(Method::getName)
			.as("interface method getElements should be visible through the JDK proxy")
			.contains("getElements");
	}

	@Test
	void beanMethodsOnJdkProxyShouldPreserveInterfaceAnnotations() {
		Object proxy = Proxy.newProxyInstance(McpToolOnInterfaceProbe.class.getClassLoader(),
				new Class<?>[] { McpToolOnInterfaceProbe.class }, (p, method, args) -> null);

		Method[] methods = AnnotationProviderUtil.beanMethods(proxy);

		Method getElements = null;
		for (Method m : methods) {
			if ("getElements".equals(m.getName()) && m.getParameterCount() == 0
					&& m.getDeclaringClass() == McpToolOnInterfaceProbe.class) {
				getElements = m;
				break;
			}
		}

		assertThat(getElements).as("interface-declared getElements Method object must be surfaced").isNotNull();
		assertThat(getElements.isAnnotationPresent(McpTool.class))
			.as("interface method must carry its @McpTool annotation")
			.isTrue();
		assertThat(getElements.getAnnotation(McpTool.class).name()).isEqualTo("get-elements");
	}

	@Test
	void syncToolProviderShouldBuildSpecificationForToolOnJdkProxyInterface() {
		Object proxy = Proxy.newProxyInstance(McpToolOnInterfaceProbe.class.getClassLoader(),
				new Class<?>[] { McpToolOnInterfaceProbe.class }, (p, method, args) -> "ok");

		// Use the same factory the auto-configuration uses. SyncMcpAnnotationProviders
		// wraps the tool list in SpringAiSyncToolProvider, which delegates
		// doGetClassMethods to AnnotationProviderUtil.beanMethods. That is the exact
		// code path that was broken for JDK proxies in issue #5823.
		List<?> specs = SyncMcpAnnotationProviders.toolSpecifications(List.of(proxy));

		assertThat(specs).as("SyncMcpAnnotationProviders must surface the @McpTool tool on the proxied interface")
			.hasSize(1);
	}

	@Test
	void jsonSchemaIsGenerableForToolOnJdkProxyInterface() {
		Object proxy = Proxy.newProxyInstance(McpToolOnInterfaceProbe.class.getClassLoader(),
				new Class<?>[] { McpToolOnInterfaceProbe.class }, (p, method, args) -> null);

		Method[] methods = AnnotationProviderUtil.beanMethods(proxy);
		Method getElements = null;
		for (Method m : methods) {
			if ("getElements".equals(m.getName()) && m.getDeclaringClass() == McpToolOnInterfaceProbe.class) {
				getElements = m;
				break;
			}
		}
		assertThat(getElements).isNotNull();

		// Smoke test: schema generation must not throw for an interface method obtained
		// through a JDK proxy.
		String schema = McpJsonSchemaGenerator.generateForMethodInput(getElements);
		assertThat(schema).isNotBlank();
	}

	public interface McpToolOnInterfaceProbe {

		@McpTool(name = "get-elements", description = "Lists all available elements.")
		String getElements();

	}

	// Workaround to avoid an unused-import warning when McpTool annotation is in the
	// same module. (Compile-only; this meta-annotation is not retained at runtime in
	// test scope.)
	@Retention(RetentionPolicy.SOURCE)
	@Target(ElementType.ANNOTATION_TYPE)
	@interface SourceRetained {

	}

}
