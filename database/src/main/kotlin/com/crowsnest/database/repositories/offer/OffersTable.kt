package com.crowsnest.database.repositories.offer

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

/**
 * Exposed table definition for the offers table.
 * 
 * IMPORTANT: This must be kept in sync with database/src/main/resources/init.sql
 * which is used by Docker for initial schema creation.
 */
object OffersTable : Table("offers") {
    val id = uuid("id").autoGenerate()
    val url = text("url").uniqueIndex()
    val title = text("title")
    val company = text("company")
    val locations = text("locations")
    val description = text("description")
    val postedAt = timestamp("posted_at").nullable()
    val scrapedAt = timestamp("scraped_at").nullable()
    // Note: embedding column is handled separately (pgvector)

    override val primaryKey = PrimaryKey(id)
}
