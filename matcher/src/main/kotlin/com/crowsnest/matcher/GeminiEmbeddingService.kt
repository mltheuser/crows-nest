package com.crowsnest.matcher

import com.crowsnest.database.EmbeddingService
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Gemini Embedding API response structures. */
@Serializable
data class EmbedContentRequest(
        val model: String,
        val content: Content,
        val taskType: String = "RETRIEVAL_DOCUMENT"
)

@Serializable data class Content(val parts: List<Part>)

@Serializable data class Part(val text: String)

@Serializable data class EmbedContentResponse(val embedding: Embedding)

@Serializable data class Embedding(val values: List<Float>)

/** Batch embedding request for multiple texts. */
@Serializable data class BatchEmbedRequest(val requests: List<EmbedContentRequest>)

@Serializable data class BatchEmbedResponse(val embeddings: List<Embedding>)

/**
 * Implementation of EmbeddingService using Google Gemini API. Uses gemini-embedding-001 model with
 * 3072 dimensions (full precision).
 */
class GeminiEmbeddingService(private val apiKey: String) : EmbeddingService {

        private val client =
                HttpClient(CIO) {
                        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                }

        private val baseUrl = "https://generativelanguage.googleapis.com/v1beta"
        private val model = "models/gemini-embedding-001"

        override suspend fun embed(text: String): FloatArray {
                val response: EmbedContentResponse =
                        client
                                .post("$baseUrl/$model:embedContent") {
                                        parameter("key", apiKey)
                                        contentType(ContentType.Application.Json)
                                        setBody(
                                                EmbedContentRequest(
                                                        model = model,
                                                        content =
                                                                Content(parts = listOf(Part(text))),
                                                        taskType = "RETRIEVAL_DOCUMENT"
                                                )
                                        )
                                }
                                .body()

                return normalize(response.embedding.values.toFloatArray())
        }

        /** Batch embed multiple texts for efficiency. */
        suspend fun embedBatch(
                texts: List<String>,
                taskType: String = "RETRIEVAL_DOCUMENT"
        ): List<FloatArray> {
                if (texts.isEmpty()) return emptyList()

                val requests =
                        texts.map { text ->
                                EmbedContentRequest(
                                        model = model,
                                        content = Content(parts = listOf(Part(text))),
                                        taskType = taskType
                                )
                        }

                val response: BatchEmbedResponse =
                        client
                                .post("$baseUrl/$model:batchEmbedContents") {
                                        parameter("key", apiKey)
                                        contentType(ContentType.Application.Json)
                                        setBody(BatchEmbedRequest(requests))
                                }
                                .body()

                return response.embeddings.map { normalize(it.values.toFloatArray()) }
        }

        /**
         * Normalize embedding vector for accurate cosine similarity. Required for dimensions < 3072
         * per Gemini docs.
         */
        private fun normalize(embedding: FloatArray): FloatArray {
                var norm = 0f
                for (v in embedding) norm += v * v
                norm = kotlin.math.sqrt(norm)
                return FloatArray(embedding.size) { embedding[it] / norm }
        }

        fun close() {
                client.close()
        }
}
