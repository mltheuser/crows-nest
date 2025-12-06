package com.crowsnest.agent

import kotlinx.serialization.Serializable

@Serializable
data class JobOffer(
    val title: String,
    val company: String,
    val location: String? = null,
    val description: String? = null,
    val requirements: String? = null,
    val url: String? = null
)

interface JobDatabase {
    suspend fun isJobScraped(title: String, company: String): Boolean
    suspend fun saveJob(job: JobOffer)
}

class MockJobDatabase : JobDatabase {
    private val scrapedJobs = mutableSetOf<Pair<String, String>>()
    private val savedJobs = mutableListOf<JobOffer>()

    override suspend fun isJobScraped(title: String, company: String): Boolean {
        // For demo purposes, always return false to simulate new jobs, 
        // or check against internal set if we want to test deduplication logic within a run.
        // The prompt says "mock function ... that always returns false".
        return false 
    }

    override suspend fun saveJob(job: JobOffer) {
        println("Saving job to DB: $job")
        scrapedJobs.add(job.title to job.company)
        savedJobs.add(job)
    }
}
