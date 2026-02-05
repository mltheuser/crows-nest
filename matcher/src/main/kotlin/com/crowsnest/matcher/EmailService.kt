package com.crowsnest.matcher

import com.crowsnest.database.Match
import com.crowsnest.database.Offer
import com.crowsnest.database.Seeker

/** Interface for sending match notifications. */
interface EmailService {
    suspend fun sendMatchNotification(seeker: Seeker, offer: Offer, match: Match)
}

/** Mock email service that logs to console. Replace with real Gmail API implementation later. */
class MockEmailService : EmailService {
    override suspend fun sendMatchNotification(seeker: Seeker, offer: Offer, match: Match) {
        println("📧 [MOCK EMAIL] Match notification:")
        println("   To: ${seeker.email ?: "no-email"}")
        println("   Subject: New match found: ${offer.title}")
        println("   Match Score: ${match.llmScore}%")
        println("   Reason: ${match.llmReason}")
        if (match.seekerFitScore != null) {
            println("   Your fit: ${match.seekerFitScore}%")
        }
        println("   Similarity: ${String.format("%.2f", match.similarity)}")
        println("   URL: ${offer.url}")
        println()
    }
}
