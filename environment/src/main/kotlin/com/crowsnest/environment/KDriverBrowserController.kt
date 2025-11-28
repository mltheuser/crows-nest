package com.crowsnest.environment

import com.mltheuser.khtmlmarkdown.KHtmlToMarkdown
import com.mohamedrejeb.ksoup.html.parser.KsoupHtmlHandler
import com.mohamedrejeb.ksoup.html.parser.KsoupHtmlParser
import dev.kdriver.core.browser.Browser
import dev.kdriver.core.browser.createBrowser
import dev.kdriver.core.tab.Tab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking

class KDriverBrowserController : BrowserController {

    // Standard wait time for Page Loads to allow JS / Async content to settle
    private val PAGE_LOAD_WAIT_MS = 2500L

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var browser: Browser? = null
    private var activeTab: Tab? = null

    // Track visited URLs to hide them in snapshots
    private val visitedUrls = mutableSetOf<String>()

    // Maps the numeric Agent ID (e.g., "1") to the actual DOM Selector or Data
    private val elementCache = mutableMapOf<String, InteractableElement>()

    override suspend fun start() {
        if (browser != null) return
        browser = createBrowser(scope, headless = false) // Visible for debugging
        browser?.start()
        browser?.wait(PAGE_LOAD_WAIT_MS)
        // Open a blank tab to start
        activeTab = browser?.get()
    }

    override suspend fun openUrl(url: String) {
        val tab = activeTab ?: error("Browser not started")
        tab.get(url)
        tab.wait(PAGE_LOAD_WAIT_MS) // Wait for dynamic content
    }

    override suspend fun clickLink(elementId: String) {
        val tab = activeTab ?: error("Browser not started")

        val target = elementCache[elementId]
            ?: throw IllegalArgumentException("Element ID [$elementId] not found in current snapshot.")

        // Mark as visited immediately
        if (target.href != null) {
            visitedUrls.add(target.href)
        }

        println("[Browser] Clicking ID $elementId: ${target.text}")

        // kdriver select and click
        try {
            val element = tab.select(target.selector)
            element.click()
            tab.wait(PAGE_LOAD_WAIT_MS) // Wait for navigation/loading
        } catch (e: Exception) {
            throw IllegalStateException("Failed to click element [$elementId]. It might be obstructed or gone.", e)
        }
    }

    override suspend fun navigateBack() {
        val tab = activeTab ?: error("Browser not started")

        // 1. Attempt to go back
        tab.back()
        tab.wait(PAGE_LOAD_WAIT_MS)

        // 2. Check if we fell off the edge of the world (about:blank)
        if (tab.url == "about:blank") {
            // 3. Revert the action
            tab.forward()
            tab.wait(PAGE_LOAD_WAIT_MS)
            throw IllegalStateException("Cannot navigate back further. You are at the start of the browsing session.")
        }
    }

    override suspend fun getRawHtml(): String {
        return activeTab?.getContent() ?: ""
    }

    override suspend fun markAsVisited(elementId: String) {
        val target = elementCache[elementId] ?: return
        if (target.href != null) visitedUrls.add(target.href)
    }

    override suspend fun getSnapshot(): String {
        val tab = activeTab ?: error("Browser not started")
        elementCache.clear()

        // 1. Get raw HTML string from KDriver
        val htmlContent = tab.getContent()

        // 2. Parse HTML to Markdown using your new library
        val converter = KHtmlToMarkdown.Builder().build()
        val markdownOutput = converter.convert(htmlContent)

        // 3. Extract Links using KSoup separately
        // We run a specialized handler just to populate the elementCache list
        val linkHandler = LinkExtractionHandler()
        val ksoupParser = KsoupHtmlParser(handler = linkHandler)
        ksoupParser.write(htmlContent)
        ksoupParser.end()

        val extractedLinks = linkHandler.links

        // 4. Construct the Final Output
        val sb = StringBuilder()

        // Part A: The Page Content (Markdown)
        sb.append("## Page: ${tab.title}\n")
        sb.append(markdownOutput)
        sb.append("\n\n")

        // Part B: The Interactive Actions List
        sb.append("## Available Actions:\n")

        var idCounter = 1

        if (extractedLinks.isEmpty()) {
            sb.append("No interactable links found.")
        } else {
            for (el in extractedLinks) {
                val id = idCounter.toString()
                idCounter++ // Always increment to maintain stable numbering

                val isVisited = visitedUrls.contains(el.href)

                if (isVisited) {
                    // Visited links are shown but NOT added to cache (cannot be clicked again)
                    sb.append("- [VISITED] ${el.text}\n")
                } else {
                    // Construct robust CSS selector for KDriver
                    val safeHref = el.href.replace("\"", "\\\"")
                    val selector = "a[href=\"$safeHref\"]"

                    elementCache[id] = InteractableElement(el.text, el.href, selector)
                    sb.append("- [$id] Click Link: ${el.text}\n")
                }
            }
        }

        return sb.toString()
    }

    override fun close() {
        runBlocking {
            runCatching {
                browser?.stop()
            }
        }
    }

    // Helper Data Classes
    private data class InteractableElement(val text: String, val href: String?, val selector: String)

    private data class RawLink(val text: String, val href: String)

    /**
     * A lightweight handler specifically designed to extract <a> tags
     * containing valid hrefs for the Action List.
     */
    private class LinkExtractionHandler : KsoupHtmlHandler {
        val links = mutableListOf<RawLink>()

        private var currentLinkHref: String? = null
        private var currentLinkText = StringBuilder()

        override fun onOpenTag(name: String, attributes: Map<String, String>, isImplied: Boolean) {
            if (name.equals("a", ignoreCase = true)) {
                val href = attributes["href"]
                // Only track valid navigation links
                if (!href.isNullOrBlank() && !href.startsWith("javascript:") && !href.startsWith("#")) {
                    currentLinkHref = href
                    currentLinkText.clear()
                }
            }
        }

        override fun onText(text: String) {
            // If we are currently inside an open <a> tag with a valid href, capture the text
            if (currentLinkHref != null) {
                currentLinkText.append(text)
            }
        }

        override fun onCloseTag(name: String, isImplied: Boolean) {
            // When the <a> tag closes, save the link
            if (name.equals("a", ignoreCase = true) && currentLinkHref != null) {
                val linkText = currentLinkText.toString().trim().replace("\\s+".toRegex(), " ")
                if (linkText.isNotEmpty()) {
                    links.add(RawLink(linkText, currentLinkHref!!))
                }
                // Reset for next link
                currentLinkHref = null
                currentLinkText.clear()
            }
        }
    }
}