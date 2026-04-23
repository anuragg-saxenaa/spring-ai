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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.SimpleVectorStore
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser
import org.assertj.core.api.Assertions.assertThat
import reactor.core.publisher.Flux

/**
 * Unit tests for [VectorStoreKotlinExtensions].
 *
 * @author Thomas Vitale
 * @since 2.0.0
 */
class VectorStoreKotlinExtensionsTests {

    private val mockEmbeddingModel = MockEmbeddingModel()
    private val vectorStore = SimpleVectorStore.builder(mockEmbeddingModel).build()

    @Test
    fun `kotlinSimilaritySearch should return List from VectorStore`() = runBlocking {
        // Add some documents
        vectorStore.add(
            listOf(
                Document.builder()
                    .id("1")
                    .text("Spring AI is a framework for AI applications")
                    .metadata(mapOf("source" to "docs"))
                    .build(),
                Document.builder()
                    .id("2")
                    .text("Kotlin coroutines provide async programming")
                    .metadata(mapOf("source" to "docs"))
                    .build()
            )
        )

        val results = vectorStore.kotlinSimilaritySearch("Spring framework")
        assertThat(results).isNotNull()
        assertThat(results).isNotEmpty()
        assertThat(results.size).isLessThanOrEqualTo(4)
    }

    @Test
    fun `kotlinSimilaritySearch with query string should work`() = runBlocking {
        val results = vectorStore.kotlinSimilaritySearch("What is Spring AI?")
        assertThat(results).isInstanceOf(List::class.java)
    }

    @Test
    fun `kotlinSimilaritySearch with SearchRequest should apply filters`() = runBlocking {
        vectorStore.add(
            listOf(
                Document.builder()
                    .id("doc-1")
                    .text("Important document about Kotlin")
                    .metadata(mapOf("category" to "programming"))
                    .build(),
                Document.builder()
                    .id("doc-2")
                    .text("Random document")
                    .metadata(mapOf("category" to "other"))
                    .build()
            )
        )

        val request = SearchRequest.builder()
            .query("Kotlin programming")
            .topK(5)
            .filterExpression(
                FilterExpressionTextParser().parse("category == 'programming'")
            )
            .build()

        val results = vectorStore.kotlinSimilaritySearch(request)
        assertThat(results).isNotNull()
        results.forEach { doc ->
            assertThat(doc.metadata["category"]).isEqualTo("programming")
        }
    }

    @Test
    fun `kotlinSimilaritySearch should handle empty results`() = runBlocking {
        val results = vectorStore.kotlinSimilaritySearch("nonexistent query xyz123")
        assertThat(results).isInstanceOf(List::class.java)
        // Results depend on the vector store implementation
    }

    @Test
    fun `kotlinSimilaritySearch should work with similarity threshold`() = runBlocking {
        val request = SearchRequest.builder()
            .query("Spring framework")
            .similarityThreshold(0.99f)
            .build()

        val results = vectorStore.kotlinSimilaritySearch(request)
        assertThat(results).isNotNull()
    }
}

/**
 * Mock embedding model for testing — returns a fixed-size float array.
 */
class MockEmbeddingModel : org.springframework.ai.embedding.EmbeddingModel {
    override fun embed(docs: List<org.springframework.ai.document.Document>): List<List<Double>> =
        docs.map { embedQuery(it.text) }

    override fun embed(query: String): List<Double> =
        listOf(0.1, 0.2, 0.3, 0.4, 0.5)

    override fun embed(texts: List<String>): List<List<Double>> =
        texts.map { embedQuery(it) }
}