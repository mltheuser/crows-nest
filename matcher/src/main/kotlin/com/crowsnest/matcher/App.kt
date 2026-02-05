package com.crowsnest.matcher

import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import com.crowsnest.database.DatabaseFactory
import kotlinx.coroutines.runBlocking

/**
 * Entry point for the matcher worker. Processes offers and seekers: embedding, matching, and
 * notification.
 */
fun main() = runBlocking {
    val apiKey =
            System.getenv("GEMINI_API_KEY")
                    ?: error("GEMINI_API_KEY environment variable is not set")

    println("Starting Matcher Worker...")
    println("Configuration:")
    val config = MatcherConfig()
    println("  Similarity threshold: ${config.similarityThreshold}")
    println("  Match save threshold: ${config.matchSaveThreshold}")
    println("  Notify threshold: ${config.notifyThreshold}")
    println()

    // Initialize repositories
    val offerRepo = DatabaseFactory.createOfferRepository()
    val seekerRepo = DatabaseFactory.createSeekerRepository()
    val matchRepo = DatabaseFactory.createMatchRepository()

    // Initialize services
    val embeddingService = GeminiEmbeddingService(apiKey)
    val promptExecutor = simpleGoogleAIExecutor(apiKey)
    val llmModel = GoogleModels.Gemini2_5Flash
    val matchValidator = LLMMatchValidator(promptExecutor, llmModel)

    val worker =
            MatcherWorker(
                    offerRepo = offerRepo,
                    seekerRepo = seekerRepo,
                    matchRepo = matchRepo,
                    embeddingService = embeddingService,
                    matchValidator = matchValidator,
                    emailService = MockEmailService(),
                    config = config
            )

    // Run continuously
    worker.runContinuously(intervalMs = 60_000)
}
