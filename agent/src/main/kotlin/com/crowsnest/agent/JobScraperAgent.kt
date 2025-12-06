package com.crowsnest.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.functionalStrategy
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.executeStructured
import com.crowsnest.environment.BrowserController
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Retry wrapper for handling transient API errors like 429 (rate limit). Waits for [delayMs] before
 * retrying, up to [maxRetries] times.
 */
suspend fun <T> withRetry(
        maxRetries: Int = 3,
        delayMs: Long = 60_000L, // 1 minute default
        block: suspend () -> T
): T {
        var lastException: Exception? = null
        repeat(maxRetries) { attempt ->
                try {
                        return block()
                } catch (e: IllegalStateException) {
                        // Check if it's a 429 rate limit error
                        if (e.message?.contains("429") == true) {
                                lastException = e
                                println(
                                        "Rate limit hit (429). Waiting ${delayMs / 1000}s before retry ${attempt + 1}/$maxRetries..."
                                )
                                delay(delayMs)
                        } else {
                                throw e // Re-throw non-429 errors immediately
                        }
                }
        }
        throw lastException ?: IllegalStateException("Retry failed with unknown error")
}

@Serializable
@SerialName("PageAnalysis")
@LLMDescription("Analysis of the job board page containing a list of offers")
data class PageAnalysis(
        @property:LLMDescription("List of job offers found on the page")
        val offers: List<OfferSummary>,
        @property:LLMDescription("The URL of the 'Next Page' link, or null if not found")
        val nextPageUrl: String? = null,
        @property:LLMDescription(
                "The CSS selector of the 'Next Page' button, if found (see Interactive Elements section)"
        )
        val nextPageSelector: String? = null
)

@Serializable
@SerialName("OfferSummary")
@LLMDescription("Summary of a job offer from the list")
data class OfferSummary(
        @property:LLMDescription("The job title") val title: String,
        @property:LLMDescription("The company name") val company: String,
        @property:LLMDescription("The full URL to the offer details page") val url: String
)

@Serializable
@SerialName("OfferDetails")
@LLMDescription("Detailed information about a job offer")
data class OfferDetails(
        @property:LLMDescription(
                "Whether this is a valid job page (false if login/captcha/error page)"
        )
        val isValid: Boolean = true,
        @property:LLMDescription(
                "If isValid is false, explain why (e.g., 'Redirected to login page')"
        )
        val validationNote: String? = null,
        @property:LLMDescription("The job title") val title: String,
        @property:LLMDescription("The company name") val company: String,
        @property:LLMDescription("The location of the job") val location: String,
        @property:LLMDescription("Brief description of the job") val description: String,
        @property:LLMDescription("Key requirements for the job") val requirements: String
)

fun createJobScraperAgent(
        promptExecutor: PromptExecutor,
        llmModel: LLModel,
        browser: BrowserController,
        db: JobDatabase
): AIAgent<String, String> {
        return AIAgent(
                promptExecutor = promptExecutor,
                llmModel = llmModel,
                systemPrompt =
                        "You are a helpful assistant that extracts data from web page snapshots.",
                strategy =
                        functionalStrategy { startUrl ->
                                browser.start()
                                println("Opening URL: $startUrl")
                                browser.openUrl(startUrl)

                                while (true) {
                                        val snapshot = browser.getSnapshot()
                                        println("\n" + "=".repeat(80))
                                        println("SNAPSHOT RECEIVED:")
                                        println("=".repeat(80))
                                        println(snapshot)
                                        println("=".repeat(80) + "\n")

                                        // Step 1: Analyze the list page
                                        val analysisResult = withRetry {
                                                promptExecutor.executeStructured<PageAnalysis>(
                                                        prompt =
                                                                prompt("page-analysis") {
                                                                        system(
                                                                                "You are a job scraper."
                                                                        )
                                                                        user(
                                                                                "Analyze this page snapshot and extract job offers and pagination:\n$snapshot"
                                                                        )
                                                                },
                                                        model = llmModel,
                                                        fixingParser =
                                                                StructureFixingParser(
                                                                        llmModel,
                                                                        retries = 3
                                                                )
                                                )
                                        }
                                        val analysis = analysisResult.getOrNull()?.structure
                                        if (analysis == null) {
                                                println("Failed to analyze page, retrying...")
                                                continue // Or break/error handling depending on
                                                // requirement
                                        }

                                        println("\n" + "-".repeat(60))
                                        println("PAGE ANALYSIS RESULT:")
                                        println("-".repeat(60))
                                        println("Offers Found: ${analysis.offers.size}")
                                        for ((index, offer) in analysis.offers.withIndex()) {
                                                println(
                                                        "  [${index + 1}] ${offer.title} @ ${offer.company}"
                                                )
                                                println("      URL: ${offer.url}")
                                        }
                                        println("Next Page URL: ${analysis.nextPageUrl ?: "None"}")
                                        println(
                                                "Next Page Selector: ${analysis.nextPageSelector ?: "None"}"
                                        )
                                        println("-".repeat(60) + "\n")

                                        // Step 2: Visit each offer
                                        for (offer in analysis.offers) {
                                                println("Visiting offer: ${offer.title}")
                                                browser.openUrl(offer.url)

                                                val offerSnapshot = browser.getSnapshot()
                                                val detailsResult = withRetry {
                                                        promptExecutor.executeStructured<
                                                                OfferDetails>(
                                                                prompt =
                                                                        prompt("offer-details") {
                                                                                system(
                                                                                        "You are a job scraper. If the page is a login page, captcha, or error page instead of a job description, set isValid=false."
                                                                                )
                                                                                user(
                                                                                        "Extract details from this job offer snapshot:\n$offerSnapshot"
                                                                                )
                                                                        },
                                                                model = llmModel,
                                                                fixingParser =
                                                                        StructureFixingParser(
                                                                                llmModel,
                                                                                retries = 3
                                                                        )
                                                        )
                                                }
                                                val details = detailsResult.getOrNull()?.structure
                                                if (details == null) {
                                                        println(
                                                                "Failed to extract details for offer: ${offer.title}"
                                                        )
                                                        continue
                                                }

                                                // Skip invalid pages (login, captcha, etc.)
                                                if (!details.isValid) {
                                                        println(
                                                                "⚠️ Scrape blocked for: ${offer.title}"
                                                        )
                                                        println(
                                                                "   Reason: ${details.validationNote ?: "Unknown"}"
                                                        )
                                                        continue
                                                }

                                                println("Saving details for: ${details.title}")
                                                db.saveJob(
                                                        JobOffer(
                                                                title = details.title,
                                                                company = details.company,
                                                                location = details.location,
                                                                description = details.description,
                                                                requirements = details.requirements,
                                                                url = offer.url
                                                        )
                                                )
                                        }

                                        // Step 3: Pagination
                                        if (analysis.nextPageUrl != null) {
                                                println(
                                                        "Navigating to next page by URL: ${analysis.nextPageUrl}"
                                                )
                                                browser.openUrl(analysis.nextPageUrl!!)
                                        } else if (analysis.nextPageSelector != null) {
                                                println(
                                                        "Navigating to next page by Clicking: ${analysis.nextPageSelector}"
                                                )
                                                browser.clickElement(analysis.nextPageSelector!!)
                                        } else {
                                                println("No next page found. Scraping finished.")
                                                break
                                        }
                                }
                                "Scraping completed."
                        }
        )
}
