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

package org.springframework.ai.kotlin.coroutines.autoconfigure;

import org.springframework.ai.kotlin.coroutines.KotlinCoroutinesAutoConfiguration;
import org.springframework.ai.kotlin.coroutines.chat.KotlinFlowChatClientAutoConfiguration;
import org.springframework.ai.kotlin.coroutines.vectorstore.KotlinCoroutinesVectorStoreAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Import;

/**
 * {@link org.springframework.boot.autoconfigure.AutoConfiguration Auto-configuration}
 * for Spring AI Kotlin Coroutines support.
 *
 * Enables Flow-based ChatClient extensions, suspend VectorStore APIs, and reactive
 * type converters when the Kotlin coroutines library is present on the classpath.
 *
 * @author Thomas Vitale
 * @since 2.0.0
 */
@AutoConfiguration
@ConditionalOnClass(name = "kotlinx.coroutines.flow.Flow")
@Import(
    KotlinFlowChatClientAutoConfiguration::class,
    KotlinCoroutinesVectorStoreAutoConfiguration::class,
    KotlinCoroutinesAutoConfiguration::class
)
public class SpringAiKotlinCoroutinesAutoConfiguration {
}
