package com.crowsnest.environment

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.core.tools.annotations.Tool as ToolAnn

/**
 * Tools available to the Agent for navigating the browser.
 * The logic is delegated to the [BrowserController].
 */
class NavigationTools(private val browser: BrowserController) : ToolSet {

    @ToolAnn
    @LLMDescription("Clicks a link identified by its numerical ID (e.g., '1', '25'). Never click [VISITED] links.")
    suspend fun clickLink(
        @LLMDescription("The numeric ID found in the page snapshot") id: String
    ): String {
        return try {
            browser.clickLink(id)
            "Successfully clicked [$id]. Page loading..."
        } catch (e: Exception) {
            "Failed to click link [$id]: ${e.message}. Please pick another ID."
        }
    }

    @ToolAnn
    @LLMDescription("Navigates back to the previous page.")
    suspend fun navigateBack(): String {
        return try {
            browser.navigateBack()
            "Navigated back."
        } catch (e: IllegalStateException) {
            // Friendly error message for the LLM
            "Error: ${e.message}. You are already at the job board list."
        } catch (e: Exception) {
            "Error navigating back: ${e.message}"
        }
    }

    @ToolAnn
    @LLMDescription("Stops the browsing session. Use this when you have found all relevant items or reached the end of the list.")
    fun stopBrowsing(): String {
        return "STOP_SIGNAL"
    }
}