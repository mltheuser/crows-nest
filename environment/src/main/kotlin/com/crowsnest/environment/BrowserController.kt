package com.crowsnest.environment

import java.io.Closeable

interface BrowserController : Closeable {
    /** Starts the browser session. */
    suspend fun start()

    /** Navigates to a specific URL. */
    suspend fun openUrl(url: String)

    suspend fun clickElement(selector: String)

    /**
     * Clicks an element based on the ID generated in the last snapshot.
     * @throws IllegalArgumentException if the ID is invalid or not found on the current page.
     */
    // suspend fun clickLink(elementId: String) // Removed

    /** Navigates back in history. */
    // suspend fun navigateBack() // Removed

    /**
     * Returns a simplified Markdown representation of the current page. Visited links are marked as
     * [VISITED] and lose their IDs. Unvisited links are marked as [ID] Text.
     */
    suspend fun getSnapshot(): String

    /** Gets the full HTML content (used for specific extraction tasks if needed). */
    suspend fun getRawHtml(): String

    /** Marks an ID as visited manually (useful if the agent extracts data without clicking). */
    // suspend fun markAsVisited(elementId: String) // Removed
}
