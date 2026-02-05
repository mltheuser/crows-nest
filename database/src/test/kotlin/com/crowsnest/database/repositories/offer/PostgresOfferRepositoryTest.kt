package com.crowsnest.database.repositories.offer

import com.crowsnest.database.DatabaseFactory
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Integration tests for PostgresOfferRepository.
 * 
 * Prerequisites:
 * - PostgreSQL container must be running: `docker-compose up -d`
 * - init.sql must be applied (happens automatically on container start)
 */
class PostgresOfferRepositoryTest {

    private val repository = DatabaseFactory.createOfferRepository()

    @AfterEach
    fun cleanup() {
        // Clean up test data after each test
        transaction {
            exec("DELETE FROM offers")
        }
    }

    private fun createTestOffer(
        url: String = "https://example.com/job/${System.nanoTime()}",
        title: String = "Software Engineer",
        company: String = "Test Company",
        locations: String = "Remote",
        description: String = "Test job description"
    ) = Offer(
        url = url,
        title = title,
        company = company,
        locations = locations,
        description = description,
        postedAt = null,
        scrapedAt = Clock.System.now()
    )

    @Test
    fun `save persists offer and returns UUID`() = runBlocking {
        val offer = createTestOffer()

        val id = repository.save(offer)

        assertNotNull(id)
    }

    @Test
    fun `existsByUrl returns true for existing URL`() = runBlocking {
        val offer = createTestOffer(url = "https://example.com/existing-job")
        repository.save(offer)

        val exists = repository.existsByUrl("https://example.com/existing-job")

        assertTrue(exists)
    }

    @Test
    fun `existsByUrl returns false for non-existing URL`() = runBlocking {
        val exists = repository.existsByUrl("https://nonexistent.com/job")

        assertFalse(exists)
    }

    @Test
    fun `findByTitleAndCompany returns matching offer`() = runBlocking {
        val offer = createTestOffer(
            title = "Unique Title",
            company = "Unique Company"
        )
        repository.save(offer)

        val found = repository.findByTitleAndCompany("Unique Title", "Unique Company")

        assertNotNull(found)
        assertEquals("Unique Title", found.title)
        assertEquals("Unique Company", found.company)
    }

    @Test
    fun `findByTitleAndCompany returns null when no match`() = runBlocking {
        val found = repository.findByTitleAndCompany("NonExistent", "NoCompany")

        assertNull(found)
    }

    @Test
    fun `save fails on duplicate URL`() = runBlocking {
        val url = "https://example.com/duplicate-test"
        val offer1 = createTestOffer(url = url)
        val offer2 = createTestOffer(url = url, title = "Different Title")

        repository.save(offer1)

        assertThrows<Exception> {
            runBlocking { repository.save(offer2) }
        }
    }
}
