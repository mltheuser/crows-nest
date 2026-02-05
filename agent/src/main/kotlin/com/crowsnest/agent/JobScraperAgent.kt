package com.crowsnest.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.FunctionalAIAgent
import ai.koog.agents.core.agent.functionalStrategy
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.executeStructured
import com.crowsnest.agent.models.Listings
import com.crowsnest.agent.models.OfferDetails
import com.crowsnest.agent.models.OfferSummary
import com.crowsnest.agent.models.PageValidation
import com.crowsnest.agent.models.PaginationInfo
import com.crowsnest.database.repositories.offer.Offer
import com.crowsnest.database.repositories.offer.OfferRepository
import com.crowsnest.database.repositories.offer.toInstant
import com.crowsnest.environment.BrowserController
import kotlin.time.Clock

// ============================================================================
// AGENT FACTORY
// ============================================================================

fun createJobScraperAgent(
    promptExecutor: PromptExecutor,
    llmModel: LLModel,
    browser: BrowserController,
    offerRepo: OfferRepository,
): FunctionalAIAgent<String, String> {

    suspend fun extractListings(): Listings {
        val snapshot = browser.getSnapshot()

        val result = promptExecutor.executeStructured<Listings>(
            prompt = prompt("listing-page-extraction") {
                system("""
                        You are a web scraper agent that extracts structured 
                        data from job boards listing page.
                        
                        REQUIRED FIELDS for each job:
                        - title: Job title (exact text from page)
                        - company: Company name (exact text)
                        - location: Job location (exact text)
                        - url: Full URL to detail page
                        
                        PAGINATION:
                        - If a direct URL to the next page is visible, provide it in `pagination.url`
                        - If only a button/link with JavaScript is available, provide its CSS selector in `pagination.buttonSelector`
                        - Prefer URL over buttonSelector when both are available
                    """.trimIndent())
                user("$snapshot\n---\nInstruction: Extract job listings from this page as described in the system prompt.")
            },
            model = llmModel,
            fixingParser = StructureFixingParser(llmModel, retries = 2)
        )
        return result.fold(
            onSuccess = { it.structure },
            onFailure = { error ->
                throw Exception("Failed to extract listings.", error)
            }
        )
    }

    suspend fun assertValidPage() {
        val snapshot = browser.getSnapshot(includeInteractiveElements = false)
        val result = promptExecutor.executeStructured<PageValidation>(
            prompt = prompt("page-validation") {
                system("""
                    You are a job scraper agent. Verify if the current page is a valid job offer page.
                    
                    VALID JOB OFFER criteria:
                    - Contains a clear job title, company name, and job description.
                    - Is NOT a captcha, login wall, or "page not found" error.
                    - Is NOT a list of jobs (search results).
                    
                    Return isValid=true ONLY if it is a single, accessible job offer.
                """.trimIndent())
                user("$snapshot\n---\nInstruction: Analyze this page validity.")
            },
            model = llmModel,
            fixingParser = StructureFixingParser(llmModel, retries = 2)
        )
        
        val validation = result.fold(
            onSuccess = { it.structure },
            onFailure = { throw Exception("Failed to validate page structure.", it) }
        )

        if (!validation.isValid) {
            throw IllegalStateException("Page validation failed: ${validation.reason}")
        }
    }

    suspend fun extractOfferDetails(): OfferDetails {
        val snapshot = browser.getSnapshot(includeInteractiveElements = false)
        val currentDate = java.time.LocalDate.now()
        val dayOfWeek = currentDate.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
        val dateString = currentDate.toString()

        val result = promptExecutor.executeStructured<OfferDetails>(
            prompt = prompt("offer-details-page-extraction") {
                system("""
                    You are a job scraper agent. Today is $dayOfWeek, $dateString.
                    
                    REQUIRED FIELDS:
                    - title: Job title
                    - company: Company name
                    - location: Job location
                    - description: Full job description text
                    
                    OPTIONAL FIELDS:
                    - postedAt: Posted date in YYYY-MM-DD format.
                      Examples:
                      - "3 weeks ago" on $dayOfWeek $dateString → "${currentDate.minusWeeks(3)}"
                      - "yesterday" → "${currentDate.minusDays(1)}"
                      - "March 15, 2024" → "2024-03-15"
                """.trimIndent())
                user("$snapshot\n---\nInstruction: Extract job offer details from this page as described in the system prompt.")
            },
            model = llmModel,
            fixingParser = StructureFixingParser(llmModel, retries = 3)
        )
        return result.fold(
            onSuccess = { it.structure },
            onFailure = { error ->
                throw Exception("Failed to extract offer details.", error)
            }
        )
    }

    suspend fun isDuplicate(listing: OfferSummary): Boolean {
        // Primary check: URL uniqueness
        if (offerRepo.existsByUrl(listing.url)) return true
        // Secondary check: title + company combination
        return offerRepo.findByTitleAndCompany(listing.title, listing.company) != null
    }

    suspend fun saveOffer(offerDetails: OfferDetails, url: String) {
        val offer = Offer(
            url = url,
            title = offerDetails.title,
            company = offerDetails.company,
            locations = offerDetails.location,
            description = offerDetails.description,
            postedAt = offerDetails.postedAt?.toInstant(),
            scrapedAt = Clock.System.now()
        )
        offerRepo.save(offer)
        println("Saved offer: ${offer.title}")
    }


    suspend fun tryNavigateNext(info: PaginationInfo?): Boolean {
        if (info == null) return false
        val nextUrl = info.url?.takeIf { it.isNotBlank() }
        val nextBtn = info.buttonSelector?.takeIf { it.isNotBlank() }

        return when {
            nextUrl != null -> {
                browser.navigateTo(nextUrl)
                true
            }
            nextBtn != null -> {
                browser.click(nextBtn)
                true
            }
            else -> false
        }
    }

    return AIAgent(
        promptExecutor = promptExecutor,
        llmModel = llmModel,
        systemPrompt = "You are a web scraper agent that extracts structured data from job board pages.",
        strategy = functionalStrategy { startUrl ->
            browser.start()
            browser.navigateTo(startUrl)

            val visitedUrls = mutableSetOf<String>()

            while (true) {
                if (!browser.verifyUrlUniqueness(visitedUrls)) break

                val listings = extractListings()

                if (listings.offers.isEmpty()) {
                    println("⚠️ No listings found on page. Stopping.")
                    break
                }

                var newOffersCount = 0
                for (listing in listings.offers) {
                    if (isDuplicate(listing)) {
                        println("${listing.title}: Is already in database. Skipping...")
                        continue
                    }

                    newOffersCount++
                    browser.withNewTab(listing.url) {
                        assertValidPage()
                        
                        val offerDetails = extractOfferDetails()
                        saveOffer(offerDetails, listing.url)
                    }
                }

                if (newOffersCount == 0) {
                    println("⚠️ All listings on this page are duplicates. Stopping.")
                    break
                }

                if (!tryNavigateNext(listings.pagination)) {
                    println("No more pages (pagination info not found). Stopping.")
                    break
                }
            }

            println("=".repeat(60))
            println("🏁 SCRAPING COMPLETE")
            println("=".repeat(60))
            "Scraping completed."
        }
    )
}

private suspend fun BrowserController.verifyUrlUniqueness(visitedUrls: MutableSet<String>): Boolean {
    val currentUrl = getCurrentUrl()
    if (!visitedUrls.add(currentUrl)) {
        println("⚠️ Loop detected: Already visited $currentUrl. Stopping.")
        return false
    }
    return true
}



