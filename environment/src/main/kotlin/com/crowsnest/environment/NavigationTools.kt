package com.crowsnest.environment

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool as ToolAnn
import ai.koog.agents.core.tools.reflect.ToolSet

/**
 * Tools available to the Agent for navigating the browser. The logic is delegated to the
 * [BrowserController].
 */
class NavigationTools(private val browser: BrowserController) : ToolSet {

    @ToolAnn
    @LLMDescription(
            "Navigates to a specific URL. Use this to visit job offers or move to the next page."
    )
    suspend fun openUrl(@LLMDescription("The full URL to visit") url: String): String {
        return try {
            browser.openUrl(url)
            "Successfully navigated to [$url]. Page loading..."
        } catch (e: Exception) {
            "Failed to open URL [$url]: ${e.message}"
        }
    }

    @ToolAnn
    @LLMDescription(
            "Clicks an interactive element (like a button) using its CSS selector found in the snapshot."
    )
    suspend fun clickElement(
            @LLMDescription(
                    "The CSS selector of the element (e.g., '#next-btn', 'button[aria-label=\"Next\"]')"
            )
            selector: String
    ): String {
        return try {
            browser.clickElement(selector)
            "Successfully clicked element with selector: $selector"
        } catch (e: Exception) {
            "Failed to click element: ${e.message}"
        }
    }

    @ToolAnn
    @LLMDescription("Stops the browsing session.")
    fun stopBrowsing(): String {
        return "Browsing stopped."
    }
}
