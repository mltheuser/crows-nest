package com.crowsnest.agent

import ai.koog.agents.core.agent.AIAgentService
import ai.koog.agents.core.agent.FunctionalAIAgent
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import com.crowsnest.database.DatabaseFactory
import com.crowsnest.environment.BrowserController
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ScrapingConfig(val seedUrls: List<String>)

private fun loadConfig(): ScrapingConfig {
    val resourcePath = "/scraping_config.json"
    val inputStream = object {}.javaClass.getResourceAsStream(resourcePath)
        ?: error("Config file not found at classpath:$resourcePath")
    val json = Json { ignoreUnknownKeys = true }
    return json.decodeFromString<ScrapingConfig>(inputStream.bufferedReader().readText())
}

fun main() = runBlocking {
    val apiKey = System.getenv("GEMINI_API_KEY") 
        ?: error("GEMINI_API_KEY environment variable is not set")
    
    val config = loadConfig()

    val offerRepo = DatabaseFactory.createOfferRepository()
    val browser = BrowserController()
    val promptExecutor = simpleGoogleAIExecutor(apiKey)
    val llmModel = GoogleModels.Gemini2_5FlashLite

    val agent = createJobScraperAgent(
        promptExecutor = promptExecutor,
        llmModel = llmModel,
        browser = browser,
        offerRepo = offerRepo,
    )

    val agentService = AIAgentService.fromAgent(agent)

    runScraper(agentService, config.seedUrls)
    
    browser.close()
}

private suspend fun runScraper(agentService: AIAgentService<String, String, *>, urls: List<String>) {
    println("Starting Job Scraper with ${urls.size} seed URL(s)...")

    for ((index, seedUrl) in urls.withIndex()) {
        println("\n${"=".repeat(80)}")
        println("Processing seed URL [${index + 1}/${urls.size}]: $seedUrl")
        println("=".repeat(80))

        try {
            val result = agentService.createAgentAndRun(seedUrl)
            println("✓ Completed seed URL: $result")
        } catch (e: Exception) {
            // Unexpected error
            System.err.println("✗ Error for $seedUrl: ${e.message}")
            e.printStackTrace()
            println("  Moving to next seed URL...")
        }
    }
    
    println("\n${"=".repeat(80)}")
    println("Agent finished processing all seed URLs")
    println("=".repeat(80))
}

