package com.crowsnest.agent

import com.crowsnest.agent.mocks.TestDataFactory
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
 * Tests for detail page extraction scenarios.
 * 
 * Uses real KDriverBrowserController with MockJobBoardServer.
 * 
 * Scenarios covered:
 * - Schema exists and extracts correctly
 * - Schema exists but extraction fails 
 * - Schema uses static company value
 * - No schema available
 * - Invalid page detection
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DetailPageProcessorTest {
    
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
        fun `schema with correct selectors extracts details from job 1`() = runBlocking {
            browser.openUrl("${mockServer.baseUrl}/job/1")
            
            // Schema matching the mock server's detail.html structure:
            // <h1>Job #{{id}} Details</h1>  (page header)
            // <h2>{{title}}</h2>  (job title)
            // <p>...at {{company}}...</p>  (no good selector for company, use static)
            val schema = TestDataFactory.createDetailSchema(
                domain = "localhost",
                titleSelector = "h2",  // Job title is in h2, not h1
                companySelector = null,
                staticCompany = "JetBrains"
            )
            
            val result = schemaExtractor.extractDetail(schema).getOrNull()
            
            assertNotNull(result)
            assertTrue(result.title.isNotBlank())
            assertEquals("JetBrains", result.company)
        }
        
        @Test
        fun `schema with correct selectors extracts details from job 2`() = runBlocking {
            browser.openUrl("${mockServer.baseUrl}/job/2")
            
            // Job 2 = "Senior AI Engineer" @ "OpenAI"
            val schema = TestDataFactory.createDetailSchema(
                domain = "localhost",
                titleSelector = "h2",  // Job title is in h2
                companySelector = null,
                staticCompany = "OpenAI"
            )
            
            val result = schemaExtractor.extractDetail(schema).getOrNull()
            
            assertNotNull(result)
            assertTrue(result.title.isNotBlank())
            assertEquals("OpenAI", result.company)
        }
        
        @Test
        fun `schema with wrong selectors returns failure`() = runBlocking {
            browser.openUrl("${mockServer.baseUrl}/job/1")
            
            val schema = TestDataFactory.createDetailSchema(
                domain = "localhost",
                titleSelector = ".non-existent-title"
            )
            
            val result = schemaExtractor.extractDetail(schema)
            
            assertTrue(result.isFailure, "Should return failure when title selector doesn't match")
        }
        
        @Test
        fun `schema uses static company when no selector`() = runBlocking {
            browser.openUrl("${mockServer.baseUrl}/job/1")
            
            val schema = TestDataFactory.createDetailSchema(
                domain = "localhost",
                titleSelector = "h2",  // Job title is in h2
                companySelector = null,
                staticCompany = "StaticCompanyName"
            )
            
            val result = schemaExtractor.extractDetail(schema).getOrNull()
            
            assertNotNull(result)
            assertEquals("StaticCompanyName", result.company)
        }
        @Test
        fun `schema with missing postedAt extracts correctly`() = runBlocking {
            browser.openUrl("${mockServer.baseUrl}/job/1")
            
            val schema = TestDataFactory.createDetailSchema(
                domain = "localhost",
                titleSelector = "h2",
                companySelector = null,
                staticCompany = "Test Company",
                postedAtSelector = null,
            )
            
            val result = schemaExtractor.extractDetail(schema).getOrNull()
            
            assertNotNull(result)
            assertTrue(result.title.isNotBlank())
            assertNull(result.postedAt, "postedAt should be null when not in schema")
            assertTrue(result.isValid)
        }
    }
    
    // =========================================================================
    // Validation Logic Tests
    // =========================================================================
    
    @Nested
    inner class ValidationTests {
        
        @Test
        fun `validation passes when extracted data matches`() {
            val schemaDetails = TestDataFactory.createOfferDetails(
                title = "Software Engineer",
                company = "TestCorp"
            )
            val llmDetails = TestDataFactory.createOfferDetails(
                title = "Software Engineer",
                company = "TestCorp"
            )
            
            val schemaMap = schemaExtractor.detailsToMap(schemaDetails)
            val llmMap = schemaExtractor.detailsToMap(llmDetails)
            
            val result = SimilarityUtils.validateExtraction(
                schemaMap, llmMap,
                exactFields = listOf("company"),
                containsFields = listOf("title"),
                fuzzyFields = emptyList()
            )
            
            assertTrue(result.isValid)
        }
        
        @Test
        fun `validation fails when company differs`() {
            val schemaDetails = TestDataFactory.createOfferDetails(
                title = "Engineer",
                company = "CompanyA"
            )
            val llmDetails = TestDataFactory.createOfferDetails(
                title = "Engineer",
                company = "CompanyB"  // Different!
            )
            
            val schemaMap = schemaExtractor.detailsToMap(schemaDetails)
            val llmMap = schemaExtractor.detailsToMap(llmDetails)
            
            val result = SimilarityUtils.validateExtraction(
                schemaMap, llmMap,
                exactFields = listOf("company"),
                containsFields = listOf("title"),
                fuzzyFields = emptyList()
            )
            
            assertTrue(!result.isValid)
        }
        
        @Test
        fun `validation passes when schema title contains LLM title`() {
            val schemaDetails = TestDataFactory.createOfferDetails(
                title = "Software Engineer - Remote",  // Longer
                company = "TestCorp"
            )
            val llmDetails = TestDataFactory.createOfferDetails(
                title = "Software Engineer",  // Shorter - contained in schema
                company = "TestCorp"
            )
            
            val schemaMap = schemaExtractor.detailsToMap(schemaDetails)
            val llmMap = schemaExtractor.detailsToMap(llmDetails)
            
            val result = SimilarityUtils.validateExtraction(
                schemaMap, llmMap,
                exactFields = listOf("company"),
                containsFields = listOf("title"),
                fuzzyFields = emptyList()
            )
            
            assertTrue(result.isValid)
        }
    }
    
    // =========================================================================
    // Schema Repository for Detail Pages
    // =========================================================================
    
    @Nested
    inner class SchemaRepositoryTests {
        
        @Test
        fun `only detail schema exists returns only detail`() = runBlocking {
            val domain = "detail-only.com"
            
            schemaRepo.save(TestDataFactory.createDetailSchema(domain))
            val schemas = schemaRepo.findByDomain(domain)
            
            assertEquals(1, schemas.size)
            assertTrue(!schemas.containsKey(PageType.LISTING))
            assertTrue(schemas.containsKey(PageType.DETAIL))
        }
        
        @Test
        fun `detail schema is correctly retrieved by type`() = runBlocking {
            val domain = "typed.com"
            
            schemaRepo.save(TestDataFactory.createListingSchema(domain))
            schemaRepo.save(TestDataFactory.createDetailSchema(domain))
            
            val detailSchema = schemaRepo.findByDomainAndType(domain, PageType.DETAIL)
            
            assertNotNull(detailSchema)
            assertEquals(PageType.DETAIL, detailSchema.pageType)
        }
    }
}
