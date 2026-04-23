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

package org.springframework.ai.kotlin.coroutines.advisor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClientRequest
import org.springframework.ai.chat.client.ChatClientResponse
import org.springframework.ai.chat.client.advisor.api.AdvisorChain
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor
import org.assertj.core.api.Assertions.assertThat
import reactor.core.publisher.Flux

/**
 * Unit tests for [AdvisorFlowExtensions].
 *
 * @author Thomas Vitale
 * @since 2.0.0
 */
class AdvisorFlowExtensionsTests {

    @Test
    fun `asFlow should convert Flux to Flow`() = runBlocking {
        val flux = Flux.just(
            makeMockResponse("chunk1"),
            makeMockResponse("chunk2"),
            makeMockResponse("chunk3")
        )

        val flow: Flow<ChatClientResponse> = flux.asFlow()
        val responses = flow.toList()

        assertThat(responses).hasSize(3)
        assertThat(responses[0].chatResponse()?.result?.output?.text).isEqualTo("chunk1")
        assertThat(responses[1].chatResponse()?.result?.output?.text).isEqualTo("chunk2")
        assertThat(responses[2].chatResponse()?.result?.output?.text).isEqualTo("chunk3")
    }

    @Test
    fun `asFlow should handle empty Flux`() = runBlocking {
        val flux = Flux.empty<ChatClientResponse>()
        val flow: Flow<ChatClientResponse> = flux.asFlow()
        val responses = flow.toList()
        assertThat(responses).isEmpty()
    }

    @Test
    fun `asFlow with custom capacity should work`() = runBlocking {
        val flux = Flux.just(makeMockResponse("1"), makeMockResponse("2"))
        val flow: Flow<ChatClientResponse> = flux.asFlow(capacity = 32)
        val responses = flow.toList()
        assertThat(responses).hasSize(2)
    }

    @Test
    fun `asFlow should work with erroring Flux`() = runBlocking {
        val flux = Flux.error<ChatClientResponse>(RuntimeException("test error"))
        val flow: Flow<ChatClientResponse> = flux.asFlow()

        // The flow should close with error when collected
        var error: Throwable? = null
        runBlocking {
            try {
                flow.toList()
            }
            catch (e: Throwable) {
                error = e
            }
        }
        assertThat(error).isNotNull()
    }

    @Test
    fun `KotlinFlowAdvisor should expose order and scheduler`() {
        val advisor = TestKotlinFlowAdvisor()
        assertThat(advisor.order).isEqualTo(42)
        assertThat(advisor.scheduler).isNotNull()
    }

    @Test
    fun `stream with BaseAdvisor should return Flow`() = runBlocking {
        val advisor = TestKotlinFlowAdvisor()
        val chain = TestAdvisorChain(Flux.just(makeMockResponse("via chain")))
        val request = makeMockRequest()

        val flow = advisor.stream(request, chain, capacity = 16)
        val responses = flow.toList()

        assertThat(responses).hasSize(1)
        assertThat(responses[0].chatResponse()?.result?.output?.text).isEqualTo("via chain")
    }
}

/**
 * Test implementation of KotlinFlowAdvisor for testing.
 */
class TestKotlinFlowAdvisor : KotlinFlowAdvisor {

    override val order: Int get() = 42

    override val scheduler: reactor.core.scheduler.Scheduler
        get() = reactor.core.scheduler.Schedulers.immediate()

    override suspend fun beforeKotlin(
        request: ChatClientRequest,
        chain: AdvisorChain
    ): ChatClientRequest = request

    override suspend fun afterKotlin(
        response: ChatClientResponse,
        chain: AdvisorChain
    ): ChatClientResponse = response
}

/**
 * Test implementation of AdvisorChain for testing.
 */
class TestAdvisorChain(private val responseFlux: Flux<ChatClientResponse>) : AdvisorChain {

    override fun next(request: ChatClientRequest): ChatClientResponse =
        throw UnsupportedOperationException("Test chain — use nextStream")

    override fun nextStream(request: ChatClientRequest): Flux<ChatClientResponse> =
        responseFlux

    override fun getStreamAdvisors(): List<BaseAdvisor> = emptyList()

    override fun getCallAdvisors(): List<BaseAdvisor> = emptyList()
}

/**
 * Creates a mock ChatClientResponse for testing.
 */
private fun makeMockResponse(text: String): ChatClientResponse =
    ChatClientResponse.builder()
        .chatResponse(
            org.springframework.ai.chat.model.ChatResponse.builder()
                .withResult(
                    org.springframework.ai.chat.model.GenerateResponse.builder()
                        .output(
                            org.springframework.ai.chat.messages.AssistantMessage.Builder()
                                .text(text)
                                .build()
                        )
                        .build()
                )
                .build()
        )
        .build()

/**
 * Creates a mock ChatClientRequest for testing.
 */
private fun makeMockRequest(): ChatClientRequest =
    ChatClientRequest.builder()
        .prompt(
            org.springframework.ai.chat.prompt.Prompt(
                org.springframework.ai.chat.messages.UserMessage("test")
            )
        )
        .build()