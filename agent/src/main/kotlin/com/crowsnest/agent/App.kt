package com.crowsnest.agent

import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import com.crowsnest.environment.KDriverBrowserController
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
        // val apiKey =
        //         System.getenv("GEMINI_API_KEY")
        //                 ?: error("GEMINI_API_KEY environment variable is not set")

        val apiKey = "AIzaSyDCKkBb5mHckYwi_mvuVaZCENTC44vrglI"

        val promptExecutor = simpleGoogleAIExecutor(apiKey)
        val llmModel = GoogleModels.Gemini2_5Flash

        // Initialize environment
        val browser = KDriverBrowserController()

        // Initialize database
        val db = MockJobDatabase()

        // Create agent
        val agent =
                createJobScraperAgent(
                        promptExecutor = promptExecutor,
                        llmModel = llmModel,
                        browser = browser,
                        db = db
                )

        // Seed URL
        // val seedUrl = "https://job-boards.eu.greenhouse.io/jetbrains"
        val seedUrl = "https://www.google.com/about/careers/applications/jobs/results?sort_by=date"

        println("Starting Job Scraper Agent...")
        val result = agent.run(seedUrl)
        println("Agent finished: $result")

        browser.close()
}
