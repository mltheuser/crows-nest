package com.crowsnest.database.repositories.offer

import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Instant
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.Database

/**
 * PostgreSQL implementation of OfferRepository using Exposed DSL.
 */
class PostgresOfferRepository(private val database: Database) : OfferRepository {

    override suspend fun save(offer: Offer): UUID = suspendTransaction(db = database) {
        OffersTable.insert {
            it[url] = offer.url
            it[title] = offer.title
            it[company] = offer.company
            it[locations] = offer.locations
            it[description] = offer.description
            it[postedAt] = offer.postedAt
            it[scrapedAt] = offer.scrapedAt ?: Clock.System.now()
        } get OffersTable.id
    }

    override suspend fun existsByUrl(url: String): Boolean = suspendTransaction(db = database) {
        OffersTable.selectAll()
            .where { OffersTable.url eq url }
            .count() > 0
    }

    override suspend fun findByTitleAndCompany(title: String, company: String): Offer? =
        suspendTransaction(db = database) {
            OffersTable.selectAll()
                .where { (OffersTable.title eq title) and (OffersTable.company eq company) }
                .map { it.toOffer() }
                .firstOrNull()
        }

    private fun ResultRow.toOffer() = Offer(
        id = this[OffersTable.id],
        url = this[OffersTable.url],
        title = this[OffersTable.title],
        company = this[OffersTable.company],
        locations = this[OffersTable.locations],
        description = this[OffersTable.description],
        postedAt = this[OffersTable.postedAt],
        scrapedAt = this[OffersTable.scrapedAt]
    )
}
