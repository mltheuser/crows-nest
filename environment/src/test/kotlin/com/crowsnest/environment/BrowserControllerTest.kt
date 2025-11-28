package com.crowsnest.environment

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

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

        // 2. Check for Body Text (This was failing before)
        assertContains(snapshot, "This is a great description for job 1")

        // 3. Check for Actions
        assertContains(snapshot, "## Available Actions:")
        assertContains(snapshot, "[1] Click Link: Back to Board")
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

        // Assert Actions
        assertContains(snapshot, "[1] Click Link: Junior Kotlin Developer")
        assertContains(snapshot, "[2] Click Link: Senior AI Engineer")
    }

    @Test
    fun `test navigation tool interaction`() = runBlocking {
        controller.openUrl(server.baseUrl)
        controller.getSnapshot() // Populate cache

        // Act
        val resultMsg = tools.clickLink("1")
        assertContains(resultMsg, "Successfully clicked [1]")

        // Act
        val detailSnapshot = controller.getSnapshot()

        // Assert we see the details in the text
        assertContains(detailSnapshot, "Job #1 Details")
        assertContains(detailSnapshot, "This is a great description")
    }

    @Test
    fun `test visited link masking logic`() = runBlocking {
        controller.openUrl(server.baseUrl)

        // 1. Initial State
        var snapshot = controller.getSnapshot()
        assertContains(snapshot, "[1] Click Link: Junior Kotlin Developer")

        // 2. Click Link [1]
        tools.clickLink("1")

        // 3. Go Back
        tools.navigateBack()

        // 4. Snapshot again
        snapshot = controller.getSnapshot()
        println("Snapshot after return:\n$snapshot")

        // Assert: Junior Dev is VISITED
        assertContains(snapshot, "[VISITED] Junior Kotlin Developer")
        // It should NOT have a clickable ID
        assertFalse(snapshot.contains("[1] Click Link: Junior Kotlin Developer"))

        // Assert: Senior Dev takes the next available ID
        assertContains(snapshot, "[2] Click Link: Senior AI Engineer")
    }

    @Test
    fun `test navigation back protection`() = runBlocking {
        // 1. Open Landing Page
        controller.openUrl(server.baseUrl)
        controller.getSnapshot() // Ensure loaded

        // 2. Try to Navigate Back immediately (this would go to about:blank)
        val result = tools.navigateBack()

        // Assert: Tool should return the friendly error message
        assertContains(result, "Cannot navigate back further")

        // 3. Verify we are safely back on the Landing Page
        val snapshot = controller.getSnapshot()
        assertContains(snapshot, "Welcome to the Job Board")
        assertContains(snapshot, "Junior Kotlin Developer")
    }
}