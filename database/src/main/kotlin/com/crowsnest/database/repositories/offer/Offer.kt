package com.crowsnest.database.repositories.offer

import java.util.UUID
import kotlin.time.Instant

/**
 * Domain model for a job offer, matching the database schema in init.sql.
 */
data class Offer(
    val id: UUID? = null,
    val url: String,
    val title: String,
    val company: String,
    val locations: String,
    val description: String,
    val postedAt: Instant? = null,
    val scrapedAt: Instant? = null
)

/**
 * Parses a date string in YYYY-MM-DD format to an Instant at midnight UTC.
 */
fun String.toInstant(): Instant = Instant.parse("${this}T00:00:00Z")
