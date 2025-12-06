package com.crowsnest.environment

import kotlin.test.assertContains
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BrowserControllerTest {

    private lateinit var server: MockJobBoardServer
    private lateinit var controller: KDriverBrowserController
    private lateinit var tools: NavigationTools

    @BeforeEach
    fun setup() = runBlocking {
        server = MockJobBoardServer()
        controller = KDriverBrowserController()
        controller.start()
        tools = NavigationTools(controller)
    }

    @AfterEach
    fun tearDown() {
        controller.close()
        server.close()
    }

    @Test
    fun `test snapshot content rendering`() = runBlocking {
        // Arrange
        controller.openUrl("${server.baseUrl}/job/1") // Go directly to a detail page

        // Act
        val snapshot = controller.getSnapshot()
        println("Detail Snapshot:\n$snapshot")

        // Assert
        // 1. Check for Markdown formatting
        assertContains(snapshot, "# Job #1 Details") // H1 conversion

        // 2. Check for Body Text
        assertContains(snapshot, "This is a great description for job 1")

        // 3. Check for Links Found section
        assertContains(snapshot, "## Links Found:")
        // The mock server details page usually has a "Back" link
        // assertContains(snapshot, "- [Back to Board](${server.baseUrl})") // Assuming mock server
        // structure
    }

    @Test
    fun `test async content loading and snapshot generation`() = runBlocking {
        controller.openUrl(server.baseUrl)

        val snapshot = controller.getSnapshot()

        // Assert Headers
        assertContains(snapshot, "# Welcome to the Job Board")

        // Assert List Items
        assertContains(snapshot, "Junior Kotlin Developer")
        assertContains(snapshot, "Senior AI Engineer")

        // Assert Links with URLs
        // Note: The MockJobBoardServer produces <a href="/job/1">Junior Kotlin Developer -
        // JetBrains</a>

        assertContains(snapshot, "- [Junior Kotlin Developer - JetBrains]")
        assertContains(snapshot, "(${server.baseUrl}/job/1)")

        assertContains(snapshot, "- [Senior AI Engineer - OpenAI]")
        assertContains(snapshot, "(${server.baseUrl}/job/2)")
    }

    @Test
    fun `test navigation tool interaction`() = runBlocking {
        controller.openUrl(server.baseUrl)

        // Act: Use openUrl tool to go to job 1
        val jobUrl = "${server.baseUrl}/job/1"
        val resultMsg = tools.openUrl(jobUrl)

        assertContains(resultMsg, "Successfully navigated to [$jobUrl]")

        // Act
        val detailSnapshot = controller.getSnapshot()

        assertContains(detailSnapshot, "Job #1 Details")
        assertContains(detailSnapshot, "This is a great description")
    }

    @Test
    fun `test button pagination`() = runBlocking {
        // 1. Open Button Pagination Page
        controller.openUrl("${server.baseUrl}/button-pagination")

        // 2. Get Snapshot
        val snapshot = controller.getSnapshot()

        // 3. Assert it contains buttons in the interactive elements section
        // The "Next page" button has both an ID and an aria-label, so ID takes priority
        assertContains(snapshot, "Next page") // aria-label is used as the label
        assertContains(snapshot, "#next-btn") // ID-based selector takes priority

        // The "Previous page" button has only aria-label (no ID)
        assertContains(snapshot, "Previous page")
        assertContains(snapshot, "button[aria-label=\"Previous page\"]")
    }
}
