package com.crowsnest.database.repositories.offer

import java.util.UUID

/**
 * Repository interface for offer persistence operations.
 */
interface OfferRepository {
    /**
     * Saves an offer to the database.
     * @return the generated UUID of the saved offer
     */
    suspend fun save(offer: Offer): UUID

    /**
     * Checks if an offer with the given URL already exists.
     */
    suspend fun existsByUrl(url: String): Boolean

    /**
     * Finds an offer by title and company combination.
     * @return the matching offer, or null if not found
     */
    suspend fun findByTitleAndCompany(title: String, company: String): Offer?
}
