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

package org.springframework.ai.kotlin.coroutines.chat

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration

/**
 * Auto-configuration for Kotlin Flow-based ChatClient extensions.
 *
 * This configuration is activated when:
 * - Kotlin coroutines Flow is on the classpath
 * - Spring AI ChatClient is on the classpath
 * - The property `spring.ai.kotlin.coroutines.enabled` is not set to `false`
 *
 * It registers no beans directly — the Kotlin extensions are imported via
 * the [org.springframework.ai.kotlin.coroutines.chat] package's extension functions,
 * which are available automatically when the module is on the classpath.
 *
 * @author Thomas Vitale
 * @since 2.0.0
 */
@Configuration
@ConditionalOnClass(name = "kotlinx.coroutines.flow.Flow")
@ConditionalOnProperty(
    name = ["spring.ai.kotlin.coroutines.enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class KotlinFlowChatClientAutoConfiguration {
}
