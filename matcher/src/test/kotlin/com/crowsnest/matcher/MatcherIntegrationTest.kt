package com.crowsnest.matcher

import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import com.crowsnest.database.*
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.*

/**
 * Integration tests for the Matcher module. Requires:
 * - PostgreSQL running (docker-compose up -d)
 * - GEMINI_API_KEY environment variable
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MatcherIntegrationTest {

        private lateinit var database: Database
        private lateinit var offerRepo: PostgresOfferRepository
        private lateinit var seekerRepo: PostgresSeekerRepository
        private lateinit var matchRepo: PostgresMatchRepository
        private lateinit var embeddingService: GeminiEmbeddingService
        private lateinit var matchValidator: LLMMatchValidator
        private lateinit var mockEmailService: TestEmailService
        private lateinit var worker: MatcherWorker

        private val apiKey = System.getenv("GEMINI_API_KEY")

        // Track emails sent for verification
        class TestEmailService : EmailService {
                val sentEmails = mutableListOf<Triple<Seeker, Offer, Match>>()

                override suspend fun sendMatchNotification(
                        seeker: Seeker,
                        offer: Offer,
                        match: Match
                ) {
                        sentEmails.add(Triple(seeker, offer, match))
                        println(
                                "📧 [TEST] Email to ${seeker.email}: ${offer.title} (score=${match.llmScore})"
                        )
                }

                fun clear() = sentEmails.clear()
        }

        @BeforeAll
        fun setup() {
                // Check GEMINI_API_KEY
                if (apiKey == null) {
                        throw IllegalStateException(
                                "GEMINI_API_KEY environment variable is not set. " +
                                        "Please set it before running integration tests."
                        )
                }

                // Check PostgreSQL connection
                try {
                        database =
                                Database.connect(
                                        url = "jdbc:postgresql://localhost:5432/crowsnest",
                                        driver = "org.postgresql.Driver",
                                        user = "dev",
                                        password = "dev"
                                )
                        // Verify connection
                        transaction(database) { exec("SELECT 1") }
                } catch (e: Exception) {
                        throw IllegalStateException(
                                "PostgreSQL is not available at localhost:5432. " +
                                        "Please run 'docker-compose up -d' before running integration tests. " +
                                        "Error: ${e.message}"
                        )
                }

                offerRepo = PostgresOfferRepository(database)
                seekerRepo = PostgresSeekerRepository(database)
                matchRepo = PostgresMatchRepository(database)
                embeddingService = GeminiEmbeddingService(apiKey)

                val promptExecutor = simpleGoogleAIExecutor(apiKey)
                matchValidator = LLMMatchValidator(promptExecutor, GoogleModels.Gemini2_5Flash)
                mockEmailService = TestEmailService()

                worker =
                        MatcherWorker(
                                offerRepo = offerRepo,
                                seekerRepo = seekerRepo,
                                matchRepo = matchRepo,
                                embeddingService = embeddingService,
                                matchValidator = matchValidator,
                                emailService = mockEmailService,
                                config =
                                        MatcherConfig(
                                                similarityThreshold = 0.7f, // Lower for testing
                                                matchSaveThreshold = 50,
                                                notifyThreshold = 70,
                                                embeddingDelayMs = 100,
                                                llmDelayMs = 100
                                        )
                        )
        }

        @AfterAll
        fun teardown() {
                if (::embeddingService.isInitialized) {
                        embeddingService.close()
                }
        }

        @BeforeEach
        fun resetEmails() {
                if (::mockEmailService.isInitialized) {
                        mockEmailService.clear()
                }
        }

        @Test
        fun `embedding service generates valid embeddings`() = runBlocking {
                val text = "Senior Kotlin developer with experience in backend systems"
                val embedding = embeddingService.embed(text)

                assertEquals(3072, embedding.size, "Gemini embeddings should have 3072 dimensions")
                assertTrue(embedding.any { it != 0f }, "Embedding should not be all zeros")
        }

        @Test
        fun `batch embedding works for multiple texts`() = runBlocking {
                val texts =
                        listOf(
                                "Software engineer looking for remote work",
                                "Data scientist with ML experience",
                                "Frontend developer skilled in React"
                        )
                val embeddings = embeddingService.embedBatch(texts)

                assertEquals(3, embeddings.size, "Should return embedding for each text")
                embeddings.forEach { embedding -> assertEquals(3072, embedding.size) }
        }

        @Test
        fun `LLM validator scores good match highly`() = runBlocking {
                val offer =
                        Offer(
                                id = UUID.randomUUID(),
                                type = OfferType.JOB,
                                title = "Senior Kotlin Developer",
                                source = "TechCorp",
                                location = "Remote",
                                content =
                                        "We're looking for an experienced Kotlin developer with backend experience.",
                                url = "https://example.com/job/kotlin"
                        )

                val seeker =
                        Seeker(
                                id = UUID.randomUUID(),
                                type = OfferType.JOB,
                                email = "test@example.com",
                                profile =
                                        "I am a senior developer with 5 years of Kotlin experience, looking for remote backend positions."
                        )

                val validation = matchValidator.validate(offer, seeker, 0.9f)

                println("Match validation: score=${validation.score}, reason=${validation.reason}")
                assertTrue(
                        validation.score >= 60,
                        "Good match should score at least 60, got ${validation.score}"
                )
        }

        @Test
        fun `LLM validator scores poor match low`() = runBlocking {
                val offer =
                        Offer(
                                id = UUID.randomUUID(),
                                type = OfferType.JOB,
                                title = "Senior Neurosurgeon",
                                source = "Hospital",
                                location = "Boston",
                                content =
                                        "Seeking board-certified neurosurgeon with 10+ years experience.",
                                url = "https://example.com/job/surgeon"
                        )

                val seeker =
                        Seeker(
                                id = UUID.randomUUID(),
                                type = OfferType.JOB,
                                email = "test@example.com",
                                profile =
                                        "Frontend web developer looking for React/JavaScript positions."
                        )

                val validation = matchValidator.validate(offer, seeker, 0.3f)

                println("Match validation: score=${validation.score}, reason=${validation.reason}")
                assertTrue(
                        validation.score < 60,
                        "Poor match should score below 60, got ${validation.score}"
                )
        }

        @Test
        fun `full matching cycle processes offer and seeker`() = runBlocking {
                // Create a test seeker
                val seekerId =
                        seekerRepo.save(
                                Seeker(
                                        type = OfferType.JOB,
                                        email =
                                                "match-test-${System.currentTimeMillis()}@example.com",
                                        profile =
                                                "Experienced Kotlin/Java developer seeking backend engineering roles. " +
                                                        "5 years experience with microservices, PostgreSQL, and cloud platforms."
                                )
                        )

                // Create a matching offer
                val offerId =
                        offerRepo.save(
                                Offer(
                                        type = OfferType.JOB,
                                        title = "Backend Engineer - Kotlin/Java",
                                        source = "StartupCo",
                                        location = "Remote",
                                        content =
                                                "Looking for a backend engineer proficient in Kotlin or Java. " +
                                                        "Experience with microservices and PostgreSQL required.",
                                        url = "https://test.com/job/${System.currentTimeMillis()}"
                                )
                        )

                // Run a cycle
                val result = worker.runCycle()

                println("Cycle result: $result")
                assertTrue(
                        result.seekersEmbedded >= 1 || result.offersEmbedded >= 1,
                        "Should have embedded at least one item"
                )
        }

        @Test
        fun `notification phase sends emails for high score matches`() = runBlocking {
                // Create a match with high score directly
                val seekerId =
                        seekerRepo.save(
                                Seeker(
                                        type = OfferType.JOB,
                                        email =
                                                "notify-test-${System.currentTimeMillis()}@example.com",
                                        profile = "Test profile"
                                )
                        )

                val offerId =
                        offerRepo.save(
                                Offer(
                                        type = OfferType.JOB,
                                        title = "Test Job for Notification",
                                        source = "TestCorp",
                                        location = "Remote",
                                        content = "Test job description",
                                        url =
                                                "https://test.com/notify/${System.currentTimeMillis()}"
                                )
                        )

                // Insert a high-score match directly
                matchRepo.save(
                        Match(
                                offerId = offerId,
                                seekerId = seekerId,
                                similarity = 0.9f,
                                llmScore = 85, // Above notify threshold
                                llmReason = "Test match",
                                notified = false
                        )
                )

                // Run cycle - should pick up unnotified match
                val result = worker.runCycle()

                println("Notifications sent: ${result.notificationsSent}")
                assertTrue(
                        result.notificationsSent >= 1,
                        "Should have sent at least one notification"
                )
                assertTrue(
                        mockEmailService.sentEmails.isNotEmpty(),
                        "EmailService should have been called"
                )
        }
}
