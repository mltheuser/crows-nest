package com.crowsnest.agent

import com.crowsnest.agent.mocks.TestDataFactory
import com.crowsnest.agent.models.OfferDetails
import com.crowsnest.agent.models.OfferSummary
import com.crowsnest.database.repositories.schema.InMemorySchemaRepository
import com.crowsnest.database.repositories.schema.PageType
import com.crowsnest.environment.BrowserController
import com.crowsnest.environment.MockJobBoardServer
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*

/**
 * Unit tests for SchemaExtractor.
 * 
 * Uses real KDriverBrowserController with MockJobBoardServer for realistic testing.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SchemaExtractorTest {
    
    private lateinit var mockServer: MockJobBoardServer
    private lateinit var browser: BrowserController
    private lateinit var extractor: SchemaExtractor
    
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
        browser = BrowserController()
        browser.start()
        extractor = SchemaExtractor(browser)
    }
    
    @AfterEach
    fun teardown() {
        browser.close()
    }
    
    // =========================================================================
    // Listing Extraction Tests
    // =========================================================================
    
    @Test
    fun `extractListing with valid schema returns offers`() = runBlocking {
        // Navigate to the server-rendered listing page (not JS-loaded)
        browser.openUrl("${mockServer.baseUrl}/jobs-button?page=1")
        
        // Create a schema that matches the server-rendered HTML structure:
        // <div class="job-item"><a class="job-title" href="/job/X">...</a><span class="company">...</span></div>
        val schema = TestDataFactory.createListingSchema(
            domain = "localhost",
            offerItemsSelector = ".job-item",
            titleSelector = ".job-title",
            urlSelector = ".job-title",  // URL is in the anchor with job-title class
            companySelector = ".company"  // Company is in a span with company class
        )
        
        val result = extractor.extractListing(schema).getOrNull()
        
        assertNotNull(result)
        assertTrue(result.offers.isNotEmpty(), "Should extract some offers")
    }
    
    @Test
    fun `extractListing with missing offerItems selector returns failure`() = runBlocking {
        browser.openUrl(mockServer.baseUrl)
        
        val schema = TestDataFactory.createListingSchema().copy(
            selectors = mapOf("offerTitle" to ".title", "offerUrl" to "a")
        )
        
        val result = extractor.extractListing(schema)
        
        assertTrue(result.isFailure)
    }
    
    @Test
    fun `extractListing with missing offerTitle selector returns failure`() = runBlocking {
        browser.openUrl(mockServer.baseUrl)
        
        val schema = TestDataFactory.createListingSchema().copy(
            selectors = mapOf("offerItems" to ".item", "offerUrl" to "a")
        )
        
        val result = extractor.extractListing(schema)
        
        assertTrue(result.isFailure)
    }
    
    @Test
    fun `extractListing with missing offerUrl selector returns failure`() = runBlocking {
        browser.openUrl(mockServer.baseUrl)
        
        val schema = TestDataFactory.createListingSchema().copy(
            selectors = mapOf("offerItems" to ".item", "offerTitle" to ".title")
        )
        
        val result = extractor.extractListing(schema)
        
        assertTrue(result.isFailure)
    }
    
    @Test
    fun `extractListing uses static company when no selector`() = runBlocking {
        // Use server-rendered page
        browser.openUrl("${mockServer.baseUrl}/jobs-button?page=1")
        
        // The mock server has job items - use static company
        val schema = TestDataFactory.createListingSchema(
            domain = "localhost",
            offerItemsSelector = ".job-item",
            titleSelector = ".job-title",
            urlSelector = ".job-title",
            companySelector = null,
            staticCompany = "StaticCorp"
        )
        
        val result = extractor.extractListing(schema).getOrNull()
        
        assertNotNull(result)
        if (result.offers.isNotEmpty()) {
            assertEquals("StaticCorp", result.offers[0].company)
        }
    }
    
    // =========================================================================
    // Detail Extraction Tests
    // =========================================================================
    
    @Test
    fun `extractDetail with valid schema returns details`() = runBlocking {
        // Navigate to detail page
        browser.openUrl("${mockServer.baseUrl}/job/1")
        
        // Create schema matching mock server's detail page
        // <h2> contains job title, <h1> is just page header
        val schema = TestDataFactory.createDetailSchema(
            domain = "localhost",
            titleSelector = "h2",  // Job title is in h2, not h1
            companySelector = null,
            staticCompany = "JetBrains"  // From mock server data
        )
        
        val result = extractor.extractDetail(schema).getOrNull()
        
        assertNotNull(result)
        assertTrue(result.title.isNotBlank())
    }
    
    @Test
    fun `extractDetail with missing title returns failure`() = runBlocking {
        browser.openUrl("${mockServer.baseUrl}/job/1")
        
        // Schema with selector that won't match anything
        val schema = TestDataFactory.createDetailSchema(
            titleSelector = ".non-existent-selector"
        )
        
        val result = extractor.extractDetail(schema)
        
        assertTrue(result.isFailure)
    }
    
    @Test
    fun `extractDetail uses static company when no selector`() = runBlocking {
        browser.openUrl("${mockServer.baseUrl}/job/1")
        
        val schema = TestDataFactory.createDetailSchema(
            titleSelector = "h2",  // Job title is in h2
            companySelector = null,
            staticCompany = "TestCorpStatic"
        )
        
        val result = extractor.extractDetail(schema).getOrNull()
        
        assertNotNull(result)
        assertEquals("TestCorpStatic", result.company)
    }
    
    // =========================================================================
    // Conversion Tests
    // =========================================================================
    
    @Test
    fun `detailsToMap converts all fields`() {
        val details = TestDataFactory.createOfferDetails()
        
        val map = extractor.detailsToMap(details)
        
        assertTrue(map.containsKey("title"))
        assertTrue(map.containsKey("company"))
        assertTrue(map.containsKey("location"))
        assertTrue(map.containsKey("description"))
    }
    
    @Test
    fun `detailsToMap excludes blank fields`() {
        val details = OfferDetails(
            title = "Engineer",
            company = "Acme",
            location = "",  // blank
            description = "Great job"
        )
        
        val map = extractor.detailsToMap(details)
        
        assertTrue(!map.containsKey("location"))
    }
    
    @Test
    fun `offerSummaryToMap converts all fields`() {
        val offer = OfferSummary(
            title = "Engineer",
            company = "Acme",
            location = "NYC",
            url = "https://example.com/job/1"
        )
        
        val map = extractor.offerSummaryToMap(offer)
        
        assertEquals("Engineer", map["title"])
        assertEquals("Acme", map["company"])
        assertEquals("NYC", map["location"])
        assertEquals("https://example.com/job/1", map["url"])
    }
    
    // =========================================================================
    // Edge Cases
    // =========================================================================
    
    @Test
    fun `extractListing throws for wrong page type`() = runBlocking {
        browser.openUrl(mockServer.baseUrl)
        
        val detailSchema = TestDataFactory.createDetailSchema()
        
        try {
            extractor.extractListing(detailSchema)
            assertTrue(false, "Should have thrown")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("LISTING") == true)
        }
    }
    
    @Test
    fun `extractDetail throws for wrong page type`() = runBlocking {
        browser.openUrl("${mockServer.baseUrl}/job/1")
        
        val listingSchema = TestDataFactory.createListingSchema()
        
        try {
            extractor.extractDetail(listingSchema)
            assertTrue(false, "Should have thrown")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("DETAIL") == true)
        }
    }
}
