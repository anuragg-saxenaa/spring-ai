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

package org.springframework.ai.kotlin.coroutines.vectorstore

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Configuration

/**
 * Auto-configuration for Kotlin Coroutines VectorStore extensions.
 *
 * Activated when kotlinx.coroutines is on the classpath. Registers no beans —
 * the [kotlinSimilaritySearch] extension functions are available automatically
 * when the module is present.
 *
 * @author Thomas Vitale
 * @since 2.0.0
 */
@Configuration
@ConditionalOnClass(name = "kotlinx.coroutines.Dispatchers")
class KotlinCoroutinesVectorStoreAutoConfiguration {
}
