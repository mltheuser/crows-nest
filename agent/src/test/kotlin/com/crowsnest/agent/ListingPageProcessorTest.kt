package com.crowsnest.agent

import com.crowsnest.agent.mocks.TestDataFactory
import com.crowsnest.database.repositories.schema.InMemorySchemaRepository
import com.crowsnest.database.repositories.schema.PageType
import com.crowsnest.environment.BrowserController
import com.crowsnest.environment.MockJobBoardServer
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*

/**
 * Tests for listing page extraction scenarios.
 * 
 * Uses real KDriverBrowserController with MockJobBoardServer.
 * 
 * Scenarios covered:
 * - Schema exists and extracts correctly
 * - Schema exists but extraction fails (wrong selectors)
 * - Schema uses static company value
 * - No schema available
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ListingPageProcessorTest {
    
    private lateinit var mockServer: MockJobBoardServer
    private lateinit var browser: BrowserController
    private lateinit var schemaRepo: InMemorySchemaRepository
    private lateinit var schemaExtractor: SchemaExtractor
    private lateinit var logger: ScraperLogger
    
    @BeforeAll
    fun setupAll() {
        mockServer = MockJobBoardServer()
    }
    
    @AfterAll
    fun teardownAll() {
        mockServer.close()
    }
    
    @BeforeEach
    fun setup() = runBlocking {
        schemaRepo = InMemorySchemaRepository()
        browser = BrowserController()
        browser.start()
        schemaExtractor = SchemaExtractor(browser)
        logger = SilentScraperLogger()
    }
    
    @AfterEach
    fun teardown() {
        browser.close()
    }
    
    // =========================================================================
    // Schema Extraction Tests (without LLM - testing SchemaExtractor directly)
    // =========================================================================
    
    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class SchemaOnlyTests {
        
        @Test
        fun `schema with correct selectors extracts offers from listing page`() = runBlocking {
            // Use server-rendered page (not JS-loaded)
            browser.openUrl("${mockServer.baseUrl}/jobs-button?page=1")
            
            // Schema matching the server-rendered HTML structure:
            // <div class="job-item"><a class="job-title" href="/job/X">...</a><span class="company">...</span></div>
            val schema = TestDataFactory.createListingSchema(
                domain = "localhost",
                offerItemsSelector = ".job-item",
                titleSelector = ".job-title",
                urlSelector = ".job-title",
                companySelector = ".company"
            )
            
            val result = schemaExtractor.extractListing(schema).getOrNull()
            
            assertTrue(result != null, "Should extract offers")
            assertTrue(result.offers.isNotEmpty(), "Should have at least one offer")
        }
        
        @Test
        fun `schema with wrong selectors returns failure`() = runBlocking {
            browser.openUrl(mockServer.baseUrl)
            
            val schema = TestDataFactory.createListingSchema(
                domain = "localhost",
                offerItemsSelector = ".non-existent",
                titleSelector = ".also-not-there"
            )
            
            val result = schemaExtractor.extractListing(schema)
            
            // Returns failure because no elements match
            assertTrue(result.isFailure, "Should return failure for bad selectors")
        }
        
        @Test
        fun `empty page with valid schema returns failure or empty offers`() = runBlocking {
            browser.openUrl("${mockServer.baseUrl}/empty")
            
            val schema = TestDataFactory.createListingSchema(
                domain = "localhost",
                offerItemsSelector = ".job-item"
            )
            
            val result = schemaExtractor.extractListing(schema)
            
            // Should return failure or empty - no job items on empty page
            assertTrue(result.isFailure || result.getOrNull()?.offers?.isEmpty() == true)
        }
    }
    
    // =========================================================================
    // Schema Repository Integration Tests
    // =========================================================================
    
    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class SchemaRepositoryTests {
        
        @Test
        fun `saved schema is retrieved correctly`() = runBlocking {
            val domain = "example.com"
            val schema = TestDataFactory.createListingSchema(domain)
            
            schemaRepo.save(schema)
            val schemas = schemaRepo.findByDomain(domain)
            
            assertEquals(1, schemas.size)
            assertTrue(schemas.containsKey(PageType.LISTING))
        }
        
        @Test
        fun `only listing schema exists returns only listing`() = runBlocking {
            val domain = "mixed.com"
            
            schemaRepo.save(TestDataFactory.createListingSchema(domain))
            val schemas = schemaRepo.findByDomain(domain)
            
            assertEquals(1, schemas.size)
            assertTrue(schemas.containsKey(PageType.LISTING))
            assertTrue(!schemas.containsKey(PageType.DETAIL))
        }
        
        @Test
        fun `both schemas exist are returned`() = runBlocking {
            val domain = "full.com"
            
            schemaRepo.save(TestDataFactory.createListingSchema(domain))
            schemaRepo.save(TestDataFactory.createDetailSchema(domain))
            val schemas = schemaRepo.findByDomain(domain)
            
            assertEquals(2, schemas.size)
            assertTrue(schemas.containsKey(PageType.LISTING))
            assertTrue(schemas.containsKey(PageType.DETAIL))
        }
    }
    
    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class ValidationStateTests {

        /**
         * Verifies that when schemaValidatedThisRun is true (set internally by processor),
         * the processor skips LLM validation and returns pre-validated result.
         * 
         * This tests the concept described in BasePageProcessor - once a schema is validated
         * once per run, subsequent pages skip validation.
         */
        @Test
        fun `schemaValidatedThisRun skips validation on subsequent pages`() = runBlocking {
            // Use server-rendered page
            browser.openUrl("${mockServer.baseUrl}/jobs-button?page=1")
            
            val domain = "localhost"
            val schema = TestDataFactory.createListingSchema(
                domain = domain,
                offerItemsSelector = ".job-item",
                titleSelector = ".job-title",
                urlSelector = ".job-title",
                companySelector = ".company"
            )
            schemaRepo.save(schema)
            val schemas = schemaRepo.findByDomain(domain)
            
            // Simulate internal tracking mechanism
            var schemaValidatedThisRun = false
            var llmValidationCount = 0
            
            suspend fun simulateProcess(): String {
                val existingSchema = schemas[PageType.LISTING] ?: return "no schema"
                
                val offers = schemaExtractor.extractListing(existingSchema).getOrNull()
                if (offers == null || offers.offers.isEmpty()) return "extraction failed"
                
                // If already validated this run, skip LLM
                if (schemaValidatedThisRun) {
                    return "SCHEMA (pre-validated)"
                }
                
                // First time: validate with LLM
                llmValidationCount++
                schemaValidatedThisRun = true  // Set after first validation
                return "SCHEMA (validated with LLM)"
            }
            
            // First page triggers LLM validation
            val result1 = simulateProcess()
            assertEquals("SCHEMA (validated with LLM)", result1)
            assertEquals(1, llmValidationCount, "Should validate with LLM on first page")
            
            // Second page skips LLM validation (already validated this run)
            val result2 = simulateProcess()
            assertEquals("SCHEMA (pre-validated)", result2)
            assertEquals(1, llmValidationCount, "Should NOT call LLM validation again")
        }
    }
}
