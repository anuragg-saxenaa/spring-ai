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

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.util.Assert;

import org.springframework.ai.mcp.annotation.McpTool;

/**
 * @author Christian Tzolov
 * @author Alexandros Pappas
 * @author Vadzim Shurmialiou
 * @author Craig Walls
 */
public abstract class AbstractMcpToolProvider {

	protected final List<Object> toolObjects;

	protected McpJsonMapper jsonMapper = McpJsonDefaults.getMapper();

	public AbstractMcpToolProvider(List<Object> toolObjects) {
		Assert.notNull(toolObjects, "toolObjects cannot be null");
		this.toolObjects = toolObjects;
	}

	protected Method[] doGetClassMethods(Object bean) {
		return bean.getClass().getDeclaredMethods();
	}

	/**
	 * Returns all methods from the given object's class hierarchy, including interface
	 * default methods that are annotated with {@link McpTool}. This is critical for HTTP
	 * Service Client proxies created by {@code @ImportHttpServices} where the proxy
	 * implements an interface with {@link McpTool}-annotated methods.
	 * @param bean the object to inspect
	 * @return all relevant methods including interface-declared ones
	 */
	protected Method[] doGetAllMethodsFromHierarchy(Object bean) {
		Class<?> beanClass = bean.getClass();

		// Start with class declared methods
		Stream<Method> classMethods = Arrays.stream(beanClass.getDeclaredMethods());

		// Add methods from all interfaces implemented (recursively)
		Stream<Method> interfaceMethods = collectInterfaceMethods(beanClass);

		return Stream.concat(classMethods, interfaceMethods).toArray(Method[]::new);
	}

	private Stream<Method> collectInterfaceMethods(Class<?> clazz) {
		Stream<Method> methods = Stream.empty();
		for (Class<?> iface : clazz.getInterfaces()) {
			methods = Stream.concat(methods, Arrays.stream(iface.getDeclaredMethods()));
			// Recursively get methods from super-interfaces
			methods = Stream.concat(methods, collectInterfaceMethods(iface));
		}
		// Also check superclass
		if (clazz.getSuperclass() != null && clazz.getSuperclass() != Object.class) {
			methods = Stream.concat(methods, collectInterfaceMethods(clazz.getSuperclass()));
		}
		return methods;
	}

	protected McpTool doGetMcpToolAnnotation(Method method) {
		return method.getAnnotation(McpTool.class);
	}

	protected Class<? extends Throwable> doGetToolCallException() {
		return Exception.class;
	}

	public void setJsonMapper(McpJsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	public McpJsonMapper getJsonMapper() {
		return this.jsonMapper;
	}

	// @SuppressWarnings("unchecked")
	// protected Map<String, Object> parseMeta(String metaJson) {
	// if (!Utils.hasText(metaJson)) {
	// return null;
	// }
	// return JsonParser.fromJson(metaJson, Map.class);
	// }

}
