package com.crowsnest.matcher

import com.crowsnest.database.*
import kotlinx.coroutines.delay

/**
 * Worker that processes new offers and seekers through three phases:
 * 1. EMBED: Batch embed seekers (HIGH priority) then offers
 * 2. MATCH: Find similar items and validate with LLM
 * 3. NOTIFY: Send emails for high-score matches
 */
class MatcherWorker(
        private val offerRepo: OfferRepository,
        private val seekerRepo: SeekerRepository,
        private val matchRepo: MatchRepository,
        private val embeddingService: GeminiEmbeddingService,
        private val matchValidator: LLMMatchValidator,
        private val emailService: EmailService = MockEmailService(),
        private val config: MatcherConfig = MatcherConfig()
) {
    data class CycleResult(
            val seekersEmbedded: Int = 0,
            val offersEmbedded: Int = 0,
            val matchesCreated: Int = 0,
            val notificationsSent: Int = 0
    )

    /** Run a single matching cycle with all three phases. */
    suspend fun runCycle(): CycleResult {
        val embedResult = embedPhase()
        val matchResult = matchPhase()
        val notifyResult = notifyPhase()

        return CycleResult(
                seekersEmbedded = embedResult.seekersEmbedded,
                offersEmbedded = embedResult.offersEmbedded,
                matchesCreated = matchResult.matchesCreated,
                notificationsSent = notifyResult.notificationsSent
        )
    }

    /** Run continuously with a delay between cycles. */
    suspend fun runContinuously(intervalMs: Long = 60_000) {
        println("Starting continuous matching (interval: ${intervalMs}ms)")
        while (true) {
            try {
                val result = runCycle()
                println(
                        "Cycle complete: ${result.seekersEmbedded} seekers, ${result.offersEmbedded} offers embedded, ${result.matchesCreated} matches, ${result.notificationsSent} notified"
                )
            } catch (e: Exception) {
                System.err.println("Error in matching cycle: ${e.message}")
            }
            delay(intervalMs)
        }
    }

    // ========== PHASE 1: EMBEDDING ==========

    private data class EmbedPhaseResult(val seekersEmbedded: Int, val offersEmbedded: Int)

    private suspend fun embedPhase(): EmbedPhaseResult {
        // HIGH priority: Seekers first
        val seekersToEmbed = seekerRepo.getWithoutEmbedding()
        val seekersEmbedded =
                if (seekersToEmbed.isNotEmpty()) {
                    println("Embedding ${seekersToEmbed.size} seekers (HIGH priority)")
                    embedSeekers(seekersToEmbed)
                } else 0

        // NORMAL priority: Offers
        val offersToEmbed = offerRepo.getWithoutEmbedding()
        val offersEmbedded =
                if (offersToEmbed.isNotEmpty()) {
                    println("Embedding ${offersToEmbed.size} offers")
                    embedOffers(offersToEmbed)
                } else 0

        return EmbedPhaseResult(seekersEmbedded, offersEmbedded)
    }

    private suspend fun embedSeekers(seekers: List<Seeker>): Int {
        var embedded = 0
        for (batch in seekers.chunked(config.batchSize)) {
            val texts = batch.map { it.profile }
            val embeddings = embeddingService.embedBatch(texts, "RETRIEVAL_QUERY")

            batch.zip(embeddings).forEach { (seeker, embedding) ->
                seeker.id?.let { id ->
                    seekerRepo.updateEmbedding(id, embedding)
                    embedded++

                    // After embedding a seeker, find matching offers
                    findMatchingOffers(seeker.copy(embedding = embedding))
                }
            }

            // Rate limiting
            delay(config.embeddingDelayMs)
        }
        return embedded
    }

    private suspend fun embedOffers(offers: List<Offer>): Int {
        var embedded = 0
        for (batch in offers.chunked(config.batchSize)) {
            val texts = batch.map { buildOfferText(it) }
            val embeddings = embeddingService.embedBatch(texts, "RETRIEVAL_DOCUMENT")

            batch.zip(embeddings).forEach { (offer, embedding) ->
                offer.id?.let { id ->
                    offerRepo.updateEmbedding(id, embedding)
                    embedded++

                    // After embedding an offer, find matching seekers
                    findMatchingSeekers(offer.copy(embedding = embedding))
                }
            }

            // Rate limiting
            delay(config.embeddingDelayMs)
        }
        return embedded
    }

    // ========== PHASE 2: MATCHING ==========

    private data class MatchPhaseResult(val matchesCreated: Int)

    private suspend fun matchPhase(): MatchPhaseResult {
        // Matching happens inline during embedding phase for now
        // Could be separated into a queue-based system later
        return MatchPhaseResult(0)
    }

    /** Find seekers that match this offer and create match records. */
    private suspend fun findMatchingSeekers(offer: Offer) {
        val embedding = offer.embedding ?: return
        val offerId = offer.id ?: return

        val candidates =
                seekerRepo.findSimilarByThreshold(embedding, offer.type, config.similarityThreshold)

        println("  Found ${candidates.size} candidate seekers for '${offer.title}'")

        for ((seeker, similarity) in candidates) {
            val seekerId = seeker.id ?: continue

            // Skip if match already exists
            if (matchRepo.exists(offerId, seekerId)) continue

            // LLM validation
            val validation = matchValidator.validate(offer, seeker, similarity)
            println(
                    "    -> ${seeker.email ?: "anon"}: score=${validation.score}, fit=${validation.seekerFitScore}"
            )

            // Save if score >= 60
            if (validation.score >= config.matchSaveThreshold) {
                matchRepo.save(
                        Match(
                                offerId = offerId,
                                seekerId = seekerId,
                                similarity = similarity,
                                llmScore = validation.score,
                                llmReason = validation.reason,
                                seekerFitScore = validation.seekerFitScore
                        )
                )
            }

            // Rate limit LLM calls
            delay(config.llmDelayMs)
        }
    }

    /** Find offers that match this seeker and create match records. */
    private suspend fun findMatchingOffers(seeker: Seeker) {
        val embedding = seeker.embedding ?: return
        val seekerId = seeker.id ?: return

        val candidates =
                offerRepo.findSimilarByThreshold(embedding, seeker.type, config.similarityThreshold)

        println(
                "  Found ${candidates.size} candidate offers for seeker '${seeker.email ?: seekerId}'"
        )

        for ((offer, similarity) in candidates) {
            val offerId = offer.id ?: continue

            // Skip if match already exists
            if (matchRepo.exists(offerId, seekerId)) continue

            // LLM validation
            val validation = matchValidator.validate(offer, seeker, similarity)
            println("    -> ${offer.title}: score=${validation.score}")

            // Save if score >= 60
            if (validation.score >= config.matchSaveThreshold) {
                matchRepo.save(
                        Match(
                                offerId = offerId,
                                seekerId = seekerId,
                                similarity = similarity,
                                llmScore = validation.score,
                                llmReason = validation.reason,
                                seekerFitScore = validation.seekerFitScore
                        )
                )
            }

            // Rate limit LLM calls
            delay(config.llmDelayMs)
        }
    }

    // ========== PHASE 3: NOTIFICATION ==========

    private data class NotifyPhaseResult(val notificationsSent: Int)

    private suspend fun notifyPhase(): NotifyPhaseResult {
        val unnotified = matchRepo.getUnnotifiedHighScore(config.notifyThreshold)
        var sent = 0

        for (match in unnotified) {
            val offer = offerRepo.findById(match.offerId) ?: continue
            val seeker = seekerRepo.findById(match.seekerId) ?: continue

            emailService.sendMatchNotification(seeker, offer, match)
            matchRepo.markNotified(match.id!!)
            sent++
        }

        return NotifyPhaseResult(sent)
    }

    // ========== HELPERS ==========

    private fun buildOfferText(offer: Offer): String = buildString {
        append(offer.title)
        if (offer.source.isNotBlank()) append(" at ${offer.source}")
        if (offer.location.isNotBlank()) append(" in ${offer.location}")
        if (offer.content.isNotBlank()) {
            append("\n\n")
            append(offer.content.take(2000))
        }
    }
}

/** Configuration for the matcher worker. */
data class MatcherConfig(
        val similarityThreshold: Float = 0.85f,
        val matchSaveThreshold: Int = 60,
        val notifyThreshold: Int = 80,
        val batchSize: Int = 16,
        val embeddingDelayMs: Long = 2000, // ~32/min
        val llmDelayMs: Long = 6000 // ~10/min
)
