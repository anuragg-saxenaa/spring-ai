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
import kotlinx.coroutines.withContext

import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.ai.vectorstore.VectorStoreRetriever

/**
 * Coroutines extensions for [VectorStore] and [VectorStoreRetriever].
 *
 * Provides suspend-function alternatives to the blocking retrieval APIs,
 * enabling non-blocking vector similarity searches from coroutine contexts.
 *
 * @author Thomas Vitale
 * @since 2.0.0
 */

/**
 * Performs a similarity search on this [VectorStore] in a suspending manner,
 * offloading the blocking I/O to [Dispatchers.IO][kotlinx.coroutines.Dispatchers.IO].
 *
 * This is the Kotlin coroutines equivalent of [VectorStoreRetriever.similaritySearch]
 * and [VectorStoreRetriever.similaritySearch], but as a suspending function
 * that can be called directly from any suspend context (e.g., a controller,
 * service, or another suspend function).
 *
 * Example usage:
 * ```kotlin
 * suspend fun findSimilar(documents: List<Document>) {
 *     val results: List<Document> = vectorStore.kotlinSimilaritySearch(
 *         SearchRequest.builder()
 *             .query("What is Spring AI?")
 *             .topK(5)
 *             .build()
 *     )
 *     documents.forEach { doc ->
 *         println(doc.text)
 *     }
 * }
 * ```
 *
 * @receiver the [VectorStore] to search
 * @param request the [SearchRequest] with query and search parameters
 * @return a [List] of [Document]s matching the search criteria
 */
suspend fun VectorStore.kotlinSimilaritySearch(request: SearchRequest): List<Document> =
    withContext(Dispatchers.IO) {
        this@kotlinSimilaritySearch.similaritySearch(request)
    }

/**
 * Performs a similarity search on this [VectorStore] in a suspending manner,
 * offloading the blocking I/O to [Dispatchers.IO][kotlinx.coroutines.Dispatchers.IO].
 *
 * Convenience overload that accepts a query string and uses default search parameters.
 *
 * @receiver the [VectorStore] to search
 * @param query the text to use for embedding similarity comparison
 * @return a [List] of [Document]s matching the search criteria
 */
suspend fun VectorStore.kotlinSimilaritySearch(query: String): List<Document> =
    withContext(Dispatchers.IO) {
        this@kotlinSimilaritySearch.similaritySearch(query)
    }

/**
 * Performs a similarity search on this [VectorStoreRetriever] in a suspending manner,
 * offloading the blocking I/O to [Dispatchers.IO][kotlinx.coroutines.Dispatchers.IO].
 *
 * @receiver the [VectorStoreRetriever] to search
 * @param request the [SearchRequest] with query and search parameters
 * @return a [List] of [Document]s matching the search criteria
 */
suspend fun VectorStoreRetriever.kotlinSimilaritySearch(request: SearchRequest): List<Document> =
    withContext(Dispatchers.IO) {
        this@kotlinSimilaritySearch.similaritySearch(request)
    }

/**
 * Performs a similarity search on this [VectorStoreRetriever] in a suspending manner,
 * offloading the blocking I/O to [Dispatchers.IO][kotlinx.coroutines.Dispatchers.IO].
 *
 * Convenience overload that accepts a query string and uses default search parameters.
 *
 * @receiver the [VectorStoreRetriever] to search
 * @param query the text to use for embedding similarity comparison
 * @return a [List] of [Document]s matching the search criteria
 */
suspend fun VectorStoreRetriever.kotlinSimilaritySearch(query: String): List<Document> =
    withContext(Dispatchers.IO) {
        this@kotlinSimilaritySearch.similaritySearch(query)
    }
