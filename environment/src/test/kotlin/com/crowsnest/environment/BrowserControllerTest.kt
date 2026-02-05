package com.crowsnest.environment

import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Comprehensive tests for BrowserController.
 *
 * Uses MockJobBoardServer for realistic browser testing with async content loading.
 */
class BrowserControllerTest {

    private lateinit var server: MockJobBoardServer
    private lateinit var browser: BrowserController

    @BeforeEach
    fun setup() = runBlocking {
        server = MockJobBoardServer()
        // Fast delays for testing: page load > 100ms (mock server async delay)
        browser = BrowserController(
            pageLoadWaitMs = 300L,
            requestDelayMs = 50L,
            browserStartWaitMs = 1000L,
            tabOpenTimeoutMs = 2000L
        )
        browser.start()
    }

    @AfterEach
    fun tearDown() {
        browser.close()
        server.close()
    }

    // =========================================================================
    // Tab Management Tests (withNewTab) - Priority
    // =========================================================================

    @Nested
    inner class WithNewTabTests {

        @Test
        fun `withNewTab executes block and returns result`() = runBlocking {
            browser.navigateTo(server.baseUrl)

            val result = browser.withNewTab("${server.baseUrl}/job/1") {
                "executed"
            }

            assertEquals("executed", result)
        }

        @Test
        fun `withNewTab - getSnapshot in block targets new tab content`() = runBlocking {
            browser.navigateTo(server.baseUrl)
            val originalSnapshot = browser.getSnapshot()
            assertContains(originalSnapshot, "Welcome to the Job Board")

            browser.withNewTab("${server.baseUrl}/job/1") {
                val newTabSnapshot = browser.getSnapshot()
                assertContains(newTabSnapshot, "Job Details")
                assertFalse(newTabSnapshot.contains("Welcome to the Job Board"))
            }
        }

        @Test
        fun `withNewTab - original tab content is accessible after block completes`() = runBlocking {
            browser.navigateTo(server.baseUrl)

            browser.withNewTab("${server.baseUrl}/job/1") {
                // Do something on new tab
                browser.getSnapshot()
            }

            // After withNewTab, we should be back on original tab
            val snapshot = browser.getSnapshot()
            assertContains(snapshot, "Welcome to the Job Board")
        }

        @Test
        fun `withNewTab - can navigate on original tab after new tab closes`() = runBlocking {
            browser.navigateTo(server.baseUrl)

            browser.withNewTab("${server.baseUrl}/job/1") {
                browser.getSnapshot()
            }

            // Should be able to navigate normally after withNewTab
            browser.navigateTo("${server.baseUrl}/job/2")
            val snapshot = browser.getSnapshot()
            assertContains(snapshot, "Job Details")
        }

        @Test
        fun `withNewTab - exception in block still returns to original tab`() = runBlocking {
            browser.navigateTo(server.baseUrl)

            assertFailsWith<RuntimeException> {
                browser.withNewTab("${server.baseUrl}/job/1") {
                    throw RuntimeException("Test exception")
                }
            }

            // Should still be back on original tab despite exception
            val snapshot = browser.getSnapshot()
            assertContains(snapshot, "Welcome to the Job Board")
        }

        @Test
        fun `withNewTab - multiple sequential calls work correctly`() = runBlocking {
            browser.navigateTo(server.baseUrl)

            // First withNewTab
            browser.withNewTab("${server.baseUrl}/job/1") {
                assertContains(browser.getSnapshot(), "Job Details")
            }

            // Back on original
            assertContains(browser.getSnapshot(), "Welcome to the Job Board")

            // Second withNewTab
            browser.withNewTab("${server.baseUrl}/empty") {
                assertContains(browser.getSnapshot(), "No Jobs Found")
            }

            // Still back on original
            assertContains(browser.getSnapshot(), "Welcome to the Job Board")
        }

        @Test
        fun `withNewTab - navigateTo inside block affects only new tab`() = runBlocking {
            browser.navigateTo(server.baseUrl)

            browser.withNewTab("${server.baseUrl}/job/1") {
                // Navigate within the new tab
                browser.navigateTo("${server.baseUrl}/empty")
                assertContains(browser.getSnapshot(), "No Jobs Found")
            }

            // Original tab should still be on the landing page
            val snapshot = browser.getSnapshot()
            assertContains(snapshot, "Welcome to the Job Board")
        }
    }

    // =========================================================================
    // Snapshot Tests
    // =========================================================================

    @Nested
    inner class SnapshotTests {

        @Test
        fun `getSnapshot renders page content correctly`() = runBlocking {
            browser.navigateTo("${server.baseUrl}/job/1")

            val snapshot = browser.getSnapshot()

            assertContains(snapshot, "Job Details")
            assertContains(snapshot, "Junior Kotlin Developer")
            assertContains(snapshot, "JetBrains")
        }

        @Test
        fun `getSnapshot includes links section by default`() = runBlocking {
            browser.navigateTo(server.baseUrl)

            val snapshot = browser.getSnapshot()

            assertContains(snapshot, "## Links Found (Absolute):")
            assertContains(snapshot, "/job/1")
        }

        @Test
        fun `getSnapshot includes button selectors`() = runBlocking {
            browser.navigateTo("${server.baseUrl}/jobs-button")

            val snapshot = browser.getSnapshot()

            assertContains(snapshot, "## Interactive Elements:")
            assertContains(snapshot, "Next page")
            assertContains(snapshot, "#next-btn")
            assertContains(snapshot, "Previous page")
            assertContains(snapshot, "button[aria-label=\"Previous page\"]")
        }

        @Test
        fun `getSnapshot with includeInteractiveElements=false excludes links and buttons`() = runBlocking {
            browser.navigateTo("${server.baseUrl}/jobs-button")

            val snapshot = browser.getSnapshot(includeInteractiveElements = false)

            assertFalse(snapshot.contains("## Links Found"))
            assertFalse(snapshot.contains("## Interactive Elements"))
            // But still has the page content
            assertContains(snapshot, "Job Listings (Button Pagination)")
        }

        @Test
        fun `getSnapshot handles page with no buttons`() = runBlocking {
            browser.navigateTo("${server.baseUrl}/empty")

            val snapshot = browser.getSnapshot()

            assertContains(snapshot, "No buttons found.")
        }

        @Test
        fun `getSnapshot makes links absolute`() = runBlocking {
            browser.navigateTo(server.baseUrl)

            val snapshot = browser.getSnapshot()

            // Links should contain the full URL, not just relative path
            assertContains(snapshot, server.baseUrl)
        }
    }

    // =========================================================================
    // Navigation Tests
    // =========================================================================

    @Nested
    inner class NavigationTests {

        @Test
        fun `navigateTo loads the page`() = runBlocking {
            browser.navigateTo(server.baseUrl)

            val snapshot = browser.getSnapshot()

            assertContains(snapshot, "Welcome to the Job Board")
        }

        @Test
        fun `navigateTo updates page content`() = runBlocking {
            browser.navigateTo(server.baseUrl)
            assertContains(browser.getSnapshot(), "Welcome to the Job Board")

            browser.navigateTo("${server.baseUrl}/job/1")

            val snapshot = browser.getSnapshot()
            assertContains(snapshot, "Job Details")
            assertFalse(snapshot.contains("Welcome to the Job Board"))
        }

        @Test
        fun `navigateTo can be called multiple times`() = runBlocking {
            browser.navigateTo(server.baseUrl)
            browser.navigateTo("${server.baseUrl}/job/1")
            browser.navigateTo("${server.baseUrl}/empty")

            val snapshot = browser.getSnapshot()
            assertContains(snapshot, "No Jobs Found")
        }

        @Test
        fun `getCurrentUrl returns current page url`() = runBlocking {
            browser.navigateTo("${server.baseUrl}/")
            assertEquals("${server.baseUrl}/", browser.getCurrentUrl())

            browser.navigateTo("${server.baseUrl}/job/1")
            assertEquals("${server.baseUrl}/job/1", browser.getCurrentUrl())
        }
        

    }

    // =========================================================================
    // Click Tests
    // =========================================================================

    @Nested
    inner class ClickTests {

        @Test
        fun `click triggers element`() = runBlocking {
            browser.navigateTo("${server.baseUrl}/jobs-button?page=1")
            assertContains(browser.getSnapshot(), "Junior Kotlin Developer")

            browser.click("#next-btn")

            val snapshot = browser.getSnapshot()
            assertContains(snapshot, "Senior AI Engineer")
        }

        @Test
        fun `click throws on invalid selector`() = runBlocking {
            browser.navigateTo(server.baseUrl)

            val exception = assertFailsWith<IllegalArgumentException> {
                browser.click("#nonexistent-element")
            }

            assertContains(exception.message ?: "", "Failed to click")
        }

        @Test
        fun `click works with aria-label selector`() = runBlocking {
            browser.navigateTo("${server.baseUrl}/jobs-button")

            // Should not throw - button exists
            browser.click("button[aria-label=\"Next page\"]")
        }
    }

    // =========================================================================
    // Pagination Integration Tests
    // =========================================================================

    @Nested
    inner class PaginationTests {

        @Test
        fun `button pagination - can navigate through pages`() = runBlocking {
            browser.navigateTo("${server.baseUrl}/jobs-button?page=1")
            assertContains(browser.getSnapshot(), "Junior Kotlin Developer")

            browser.click("#next-btn")

            assertContains(browser.getSnapshot(), "Senior AI Engineer")
        }

        @Test
        fun `can return to listing after viewing detail in new tab`() = runBlocking {
            // This simulates the agent workflow: list -> detail (new tab) -> close -> continue listing
            browser.navigateTo("${server.baseUrl}/jobs-button?page=1")
            assertContains(browser.getSnapshot(), "Junior Kotlin Developer")

            // Visit detail in new tab
            browser.withNewTab("${server.baseUrl}/job/1") {
                assertContains(browser.getSnapshot(), "Job Details")
            }

            // Back on listing, pagination should still work
            browser.click("#next-btn")
            assertContains(browser.getSnapshot(), "Senior AI Engineer")
        }
    }
}
