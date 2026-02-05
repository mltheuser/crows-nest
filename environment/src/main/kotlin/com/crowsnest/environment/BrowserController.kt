package com.crowsnest.environment

import com.mltheuser.khtmlmarkdown.KHtmlToMarkdown
import dev.kdriver.cdp.InternalCdpApi
import dev.kdriver.cdp.domain.target
import dev.kdriver.core.browser.Browser
import dev.kdriver.core.browser.createBrowser
import dev.kdriver.core.connection.send
import dev.kdriver.core.tab.Tab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.Closeable

/**
 * Controls a headless Chrome browser for web scraping.
 *
 * @param pageLoadWaitMs Time to wait for page content after navigation.
 * @param requestDelayMs Delay before each request (rate limiting).
 * @param browserStartWaitMs Time to wait for browser startup.
 * @param tabOpenTimeoutMs Timeout for waiting for new tab to appear.
 */
@OptIn(ExperimentalCoroutinesApi::class, InternalCdpApi::class)
class BrowserController(
    private val pageLoadWaitMs: Long = 2500L,
    private val requestDelayMs: Long = 2000L,
    private val browserStartWaitMs: Long = 5000L,
    private val tabOpenTimeoutMs: Long = 5000L
) : Closeable {

    private val dispatcher = Dispatchers.IO.limitedParallelism(1)

    private var browser: Browser? = null

    // The "Current" tab pointer. Defaults to mainTab if null.
    // This allows us to "push" a new tab context temporarily.
    private var explicitActiveTab: Tab? = null

    private val activeTab: Tab
        get() = explicitActiveTab ?: browser?.mainTab ?: error("Browser not started")

    /** Starts the browser. Must be called before any other operations. */
    suspend fun start() = withContext(dispatcher) {
        if (browser != null) return@withContext
        browser = createBrowser(
            CoroutineScope(dispatcher + SupervisorJob()),
            headless = false,
            browserArgs = listOf("--start-maximized")
        )
        browser?.start()
        browser?.wait(browserStartWaitMs)
    }

    /** Navigates the active tab to the given URL. */
    suspend fun navigateTo(url: String) = withContext(dispatcher) {
        delay(requestDelayMs)
        activeTab.get(url, newTab = false)
        activeTab.wait(pageLoadWaitMs)
    }

    /** Returns the URL of the active tab. */
    suspend fun getCurrentUrl(): String = withContext(dispatcher) {
        activeTab.url ?: throw IllegalStateException("No active Tab found")
    }

    /**
     * Opens a new tab, switches context to it, runs the block, and closes the tab.
     * After completion, the original tab becomes active again.
     */
    suspend fun <T> withNewTab(url: String, block: suspend () -> T): T = withContext(dispatcher) {
        delay(requestDelayMs)

        // 1. Capture current tab IDs to identify the new one later
        val existingIds = browser?.tabs?.map { it.targetInfo!!.targetId }?.toSet() ?: emptySet()

        // 2. Trigger the new tab opening from the current tab
        activeTab.get(url, newTab = true)

        // 3. Poll until the new tab appears in KDriver's list
        val newTab = waitForNewTab(existingIds)

        // 4. Wait for the NEW tab to load (not the old one)
        newTab.wait(pageLoadWaitMs)

        // 5. Context Switch: Push new tab as the active one
        val previousTab = explicitActiveTab
        explicitActiveTab = newTab

        try {
            // All controller calls (getSnapshot, click) now target 'newTab'
            block()
        } finally {
            // 6. Context Restore: Pop back to previous tab
            explicitActiveTab = previousTab

            // 7. Hard Close using CDP
            runCatching {
                newTab.send { target.closeTarget(newTab.targetInfo!!.targetId) }
            }
        }
    }

    private suspend fun waitForNewTab(oldIds: Set<String>): Tab {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < tabOpenTimeoutMs) {
            val candidate = browser?.tabs?.firstOrNull { it.targetInfo!!.targetId !in oldIds }
            if (candidate != null) return candidate
            delay(100)
        }
        error("Timeout waiting for new tab to open")
    }

    /** Clicks an element matching the CSS selector. */
    suspend fun click(selector: String) = withContext(dispatcher) {
        try {
            activeTab.select(selector).click()
            activeTab.wait(pageLoadWaitMs)
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to click '$selector': ${e.message}")
        }
    }

    /**
     * Returns a markdown snapshot of the current page.
     * @param includeInteractiveElements If true, appends links and buttons sections.
     */
    suspend fun getSnapshot(includeInteractiveElements: Boolean = true): String = withContext(dispatcher) {
        val tab = activeTab
        val rawHtml = tab.getContent()
        val doc = Jsoup.parse(rawHtml, tab.url ?: "")

        makeLinksAbsolute(doc)

        val converter = KHtmlToMarkdown.Builder().build()
        val markdown = converter.convert(doc.html())

        buildString {
            append("## Page: ${tab.title}\n")
            append(markdown)

            if (includeInteractiveElements) {
                append("\n\n")
                append("## Links Found (Absolute):\n")
                val extractedLinks = extractLinks(doc)
                if (extractedLinks.isEmpty()) {
                    append("No links found.\n")
                } else {
                    extractedLinks.forEach { el ->
                        append("- [${el.text}](${el.href})\n")
                    }
                }

                append("\n## Interactive Elements:\n")
                val extractedButtons = extractButtons(doc)
                if (extractedButtons.isEmpty()) {
                    append("No buttons found.\n")
                } else {
                    extractedButtons.forEach { btn ->
                        append("- [Button: ${btn.label}] (click: \"${btn.selector}\")\n")
                    }
                }
            }
        }
    }

    private fun extractLinks(doc: org.jsoup.nodes.Document): List<RawLink> {
        return doc.select("a[href]").mapNotNull { el ->
            val href = el.attr("href")
            if (href.isBlank() || href.startsWith("javascript:") || href.startsWith("#") || href.startsWith("mailto:")) {
                null
            } else {
                val text = el.text().trim()
                if (text.isNotEmpty()) RawLink(text, href) else null
            }
        }
    }

    private fun extractButtons(doc: org.jsoup.nodes.Document): List<RawButton> {
        return doc.select("button").mapNotNull { el ->
            val label = el.attr("aria-label").takeIf { it.isNotBlank() }
                ?: el.text().trim().takeIf { it.isNotBlank() }
                ?: "Unnamed Button"

            val selector = when {
                el.id().isNotBlank() -> "#${el.id()}"
                el.attr("aria-label").isNotBlank() -> "button[aria-label=\"${el.attr("aria-label")}\"]"
                el.className().isNotBlank() -> {
                    val firstClass = el.className().split(" ").firstOrNull { it.isNotBlank() }
                    if (firstClass != null) "button.$firstClass" else null
                }
                else -> null
            }
            selector?.let { RawButton(label, it) }
        }
    }

    private fun makeLinksAbsolute(doc: org.jsoup.nodes.Document) {
        doc.select("a[href]").forEach { el ->
            val abs = el.attr("abs:href")
            if (abs.isNotBlank()) el.attr("href", abs)
        }
    }

    override fun close() {
        runBlocking { runCatching { browser?.stop() } }
    }

    private data class RawLink(val text: String, val href: String)
    private data class RawButton(val label: String, val selector: String)
}