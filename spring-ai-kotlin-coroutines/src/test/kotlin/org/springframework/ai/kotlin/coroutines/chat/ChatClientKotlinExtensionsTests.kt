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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.ChatClientRequest
import org.springframework.ai.chat.client.ChatClientResponse
import org.springframework.ai.chat.client.DefaultChatClient
import org.springframework.ai.chat.client.DefaultChatClientRequestSpec
import org.springframework.ai.chat.client.advisor.api.Advisor
import org.springframework.ai.chat.client.advisor.api.AdvisorChain
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.assertj.core.api.Assertions.assertThat
import reactor.core.publisher.Flux
import java.util.function.Consumer

/**
 * Unit tests for [ChatClientKotlinExtensions].
 *
 * @author Thomas Vitale
 * @since 2.0.0
 */
class ChatClientKotlinExtensionsTests {

    @Test
    fun `kotlinStream should return a Flow from ChatClientRequestSpec`() = runBlocking<Unit> {
        val mockChatModel = MockChatModel()
        val chatClient = ChatClient.builder(mockChatModel).build()

        val flow: Flow<ChatClientResponse> = chatClient.prompt("test").kotlinStream()

        assertThat(flow).isNotNull()
        val responses = flow.toList()
        assertThat(responses).isNotEmpty()
        assertThat(responses.size).isGreaterThanOrEqualTo(1)
    }

    @Test
    fun `kotlinStreamContent should return a Flow of String content`() = runBlocking<Unit> {
        val mockChatModel = MockChatModel()
        val chatClient = ChatClient.builder(mockChatModel).build()

        val contentFlow: Flow<String> = chatClient.prompt("hello").kotlinStreamContent()

        assertThat(contentFlow).isNotNull()
        val contents = contentFlow.toList()
        assertThat(contents).isNotEmpty()
        contents.forEach { content ->
            assertThat(content).isInstanceOf(String::class.java)
        }
    }

    @Test
    fun `kotlinFlow on Flux should convert to Flow`() = runBlocking<Unit> {
        val flux = Flux.just("a", "b", "c")
        val flow: Flow<String> = flux.kotlinFlow()
        val items = flow.toList()
        assertThat(items).containsExactly("a", "b", "c")
    }

    @Test
    fun `kotlinFlow should handle empty Flux`() = runBlocking<Unit> {
        val flux = Flux.empty<String>()
        val flow: Flow<String> = flux.kotlinFlow()
        val items = flow.toList()
        assertThat(items).isEmpty()
    }

    @Test
    fun `kotlinFlow should propagate errors from Flux`() = runBlocking<Unit> {
        val flux = Flux.error<String>(RuntimeException("test error"))
        val flow: Flow<String> = flux.kotlinFlow()

        assertThrows<RuntimeException> {
            runBlocking { flow.toList() }
        }
    }

    @Test
    fun `collectToList should gather all Flux elements`() = runBlocking<Unit> {
        val flux = Flux.just(1, 2, 3, 4, 5)
        val list: List<Int> = flux.collectToList()
        assertThat(list).containsExactly(1, 2, 3, 4, 5)
    }

    @Test
    fun `collectToList should return empty list for empty Flux`() = runBlocking<Unit> {
        val flux = Flux.empty<Int>()
        val list: List<Int> = flux.collectToList()
        assertThat(list).isEmpty()
    }

    @Test
    fun `kotlinStream should work with advisors`() = runBlocking<Unit> {
        val mockChatModel = MockChatModel()
        val chatClient = ChatClient.builder(mockChatModel).build()

        val responses = chatClient.prompt("with advisor")
            .advisors(arrayOf(NoOpAdvisor))
            .kotlinStream()
            .toList()

        assertThat(responses).isNotEmpty()
    }

    @Test
    fun `kotlinStream should work with multiple messages`() = runBlocking<Unit> {
        val mockChatModel = MockChatModel()
        val chatClient = ChatClient.builder(mockChatModel).build()

        val responses = chatClient.prompt()
            .messages(UserMessage("First message"))
            .messages(UserMessage("Second message"))
            .kotlinStream()
            .toList()

        assertThat(responses).isNotEmpty()
    }

    @Test
    fun `kotlinStream should work with system prompt`() = runBlocking<Unit> {
        val mockChatModel = MockChatModel()
        val chatClient = ChatClient.builder(mockChatModel).build()

        val responses = chatClient.prompt()
            .system("You are a helpful assistant.")
            .user("Hello!")
            .kotlinStream()
            .toList()

        assertThat(responses).isNotEmpty()
    }

    @Test
    fun `kotlinStreamResponse should return Flow of ChatResponse`() = runBlocking<Unit> {
        val mockChatModel = MockChatModel()
        val chatClient = ChatClient.builder(mockChatModel).build()

        val chatResponses: Flow<ChatResponse> = chatClient.prompt("test").kotlinStreamResponse()

        assertThat(chatResponses).isNotNull()
        val responses = chatResponses.toList()
        assertThat(responses).isNotEmpty()
        responses.forEach { response ->
            assertThat(response).isInstanceOf(ChatResponse::class.java)
        }
    }
}

/**
 * Mock ChatModel for testing — returns simulated streaming responses.
 */
class MockChatModel : ChatModel {

    override fun call(prompt: Prompt): ChatResponse {
        return makeResponse("Mock response")
    }

    override fun stream(prompt: Prompt): Flux<ChatResponse> {
        return Flux.defer {
            val words = listOf("Mock", "streaming", "response", "from", "MockChatModel")
            Flux.fromIterable(words)
                .map { word -> makeResponse(word) }
        }
    }

    private fun makeResponse(content: String): ChatResponse =
        ChatResponse.builder()
            .withResult(
                org.springframework.ai.chat.model.GenerateResponse.builder()
                    .output(
                        AssistantMessage.Builder()
                            .text(content)
                            .build()
                    )
                    .build()
            )
            .build()

    override fun call(messages: List<Message>): ChatResponse =
        call(Prompt(messages))

    override fun stream(messages: List<Message>): Flux<ChatResponse> =
        stream(Prompt(messages))
}

/**
 * No-op advisor for testing — passes requests and responses through unchanged.
 */
object NoOpAdvisor : BaseAdvisor {

    override val order: Int get() = 0

    override fun before(
        request: ChatClientRequest,
        chain: AdvisorChain
    ): ChatClientRequest = request

    override fun after(
        response: ChatClientResponse,
        chain: AdvisorChain
    ): ChatClientResponse = response

    override fun around(
        request: ChatClientRequest,
        chain: AdvisorChain
    ): ChatClientResponse = chain.next(request)

    override fun stream(
        request: ChatClientRequest,
        chain: AdvisorChain
    ): Flux<ChatClientResponse> = chain.nextStream(request)
}