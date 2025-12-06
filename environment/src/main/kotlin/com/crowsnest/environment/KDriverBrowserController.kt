package com.crowsnest.environment

import com.mltheuser.khtmlmarkdown.KHtmlToMarkdown
import com.mohamedrejeb.ksoup.html.parser.KsoupHtmlHandler
import com.mohamedrejeb.ksoup.html.parser.KsoupHtmlParser
import dev.kdriver.core.browser.Browser
import dev.kdriver.core.browser.createBrowser
import dev.kdriver.core.tab.Tab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class KDriverBrowserController : BrowserController {

    // Standard wait time for Page Loads to allow JS / Async content to settle
    private val PAGE_LOAD_WAIT_MS = 2500L

    // Use a single thread to avoid concurrency issues in KDriver
    private val dispatcher = newSingleThreadContext("BrowserThread")
    private val scope = CoroutineScope(dispatcher + Job())
    private var browser: Browser? = null
    private var activeTab: Tab? = null

    override suspend fun start() {
        if (browser != null) return
        browser = createBrowser(scope, headless = false) // Visible for debugging
        browser?.start()
        browser?.wait(PAGE_LOAD_WAIT_MS)
        // Open a blank tab to start
        activeTab = browser?.get()
    }

    // Fixed delay between requests to avoid rate limiting (2 seconds)
    private val REQUEST_DELAY_MS = 2000L

    override suspend fun openUrl(url: String) {
        val tab = activeTab ?: error("Browser not started")
        kotlinx.coroutines.delay(REQUEST_DELAY_MS) // Anti-rate-limit delay
        tab.get(url)
        tab.wait(PAGE_LOAD_WAIT_MS) // Wait for dynamic content
    }

    override suspend fun clickElement(selector: String) {
        val tab = activeTab ?: error("Browser not started")
        // KDriver select returns a locator, click it.
        try {
            tab.select(selector).click()
            tab.wait(PAGE_LOAD_WAIT_MS)
        } catch (e: Exception) {
            throw IllegalArgumentException(
                    "Failed to click element with selector '$selector': ${e.message}"
            )
        }
    }

    override suspend fun getRawHtml(): String {
        return activeTab?.getContent() ?: ""
    }

    override suspend fun getSnapshot(): String {
        val tab = activeTab ?: error("Browser not started")

        // 1. Get raw HTML string from KDriver
        val htmlContent = tab.getContent()

        // 2. Parse HTML to Markdown using your new library
        val converter = KHtmlToMarkdown.Builder().build()
        val markdownOutput = converter.convert(htmlContent)

        // 3. Extract Links using KSoup separately
        // We run a specialized handler just to populate the list of links
        val linkHandler = LinkExtractionHandler()
        val buttonHandler = ButtonExtractionHandler()

        // Use a composite handler or parse twice (simple approach: parse twice or extend parser)
        // KSoup doesn't support multiple handlers easily in one pass without a composite
        // Let's parse for links
        val linkParser = KsoupHtmlParser(handler = linkHandler)
        linkParser.write(htmlContent)
        linkParser.end()

        // Parse for buttons
        val buttonParser = KsoupHtmlParser(handler = buttonHandler)
        buttonParser.write(htmlContent)
        buttonParser.end()

        val extractedLinks = linkHandler.links
        val extractedButtons = buttonHandler.buttons

        // 4. Construct the Final Output
        val sb = StringBuilder()

        // Part A: The Page Content (Markdown)
        sb.append("## Page: ${tab.title}\n")
        sb.append(markdownOutput)
        sb.append("\n\n")

        // Part B: The Interactive Actions List
        sb.append("## Links Found:\n")

        if (extractedLinks.isEmpty()) {
            sb.append("No links found.\n")
        } else {
            val currentUrl = java.net.URI(tab.url)
            for (el in extractedLinks) {
                // Resolve relative URLs to absolute
                val absoluteUrl =
                        try {
                            currentUrl.resolve(el.href).toString()
                        } catch (e: Exception) {
                            el.href // Fallback if resolution fails
                        }

                // Show as standard Markdown link
                sb.append("- [${el.text}]($absoluteUrl)\n")
            }
        }

        sb.append("\n## Interactive Elements:\n")
        if (extractedButtons.isEmpty()) {
            sb.append("No buttons found.\n")
        } else {
            for (btn in extractedButtons) {
                sb.append("- [Button: ${btn.label}] (click: \"${btn.selector}\")\n")
            }
        }

        return sb.toString()
    }

    override fun close() {
        runBlocking { runCatching { browser?.stop() } }
    }

    // Helper Data Classes

    private data class RawLink(val text: String, val href: String)
    private data class RawButton(val label: String, val selector: String)

    /**
     * A lightweight handler specifically designed to extract <a> tags containing valid hrefs for
     * the Action List.
     */
    private class LinkExtractionHandler : KsoupHtmlHandler {
        val links = mutableListOf<RawLink>()

        private var currentLinkHref: String? = null
        private var currentLinkText = StringBuilder()

        override fun onOpenTag(name: String, attributes: Map<String, String>, isImplied: Boolean) {
            if (name.equals("a", ignoreCase = true)) {
                val href = attributes["href"]
                // Only track valid navigation links
                if (!href.isNullOrBlank() &&
                                !href.startsWith("javascript:") &&
                                !href.startsWith("#")
                ) {
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

    private class ButtonExtractionHandler : KsoupHtmlHandler {
        val buttons = mutableListOf<RawButton>()

        private var currentButtonId: String? = null
        private var currentButtonAriaLabel: String? = null
        private var currentButtonClass: String? = null
        private var currentButtonText = StringBuilder()
        private var inButton = false

        override fun onOpenTag(name: String, attributes: Map<String, String>, isImplied: Boolean) {
            if (name.equals("button", ignoreCase = true)) {
                inButton = true
                currentButtonId = attributes["id"]
                currentButtonAriaLabel = attributes["aria-label"]
                currentButtonClass = attributes["class"]
                currentButtonText.clear()
            }
        }

        override fun onText(text: String) {
            if (inButton) {
                currentButtonText.append(text)
            }
        }

        override fun onCloseTag(name: String, isImplied: Boolean) {
            if (name.equals("button", ignoreCase = true)) {
                // Determine the label (prefer aria-label, then inner text)
                val label =
                        currentButtonAriaLabel?.takeIf { it.isNotBlank() }
                                ?: currentButtonText.toString().trim().takeIf { it.isNotBlank() }
                                        ?: "Unnamed Button"

                // Determine the selector (prefer ID, then aria-label, then class)
                val selector =
                        when {
                            !currentButtonId.isNullOrBlank() -> "#${currentButtonId}"
                            !currentButtonAriaLabel.isNullOrBlank() ->
                                    "button[aria-label=\"${currentButtonAriaLabel}\"]"
                            !currentButtonClass.isNullOrBlank() -> {
                                // Use the first class as the selector
                                val firstClass =
                                        currentButtonClass!!.split(" ").firstOrNull {
                                            it.isNotBlank()
                                        }
                                if (firstClass != null) "button.$firstClass" else null
                            }
                            else -> null
                        }

                if (selector != null) {
                    buttons.add(RawButton(label, selector))
                }

                inButton = false
                currentButtonId = null
                currentButtonAriaLabel = null
                currentButtonClass = null
                currentButtonText.clear()
            }
        }
    }
}
