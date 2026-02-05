package com.crowsnest.agent.models

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("OfferDetails")
@LLMDescription("Detailed information about a job offer")
data class OfferDetails(
    @property:LLMDescription("The job title") val title: String,
    @property:LLMDescription("The company name") val company: String,
    @property:LLMDescription("The location of the job") val location: String,
    @property:LLMDescription("Full description of the job including requirements") val description: String,
    @property:LLMDescription(
        "When was this job posted? Convert relative dates to YYYY-MM-DD format. " +
        "Example: '2 weeks ago' on Monday 2024-01-15 → 2024-01-01 (Monday 2 weeks prior). " +
        "Return null if unknown."
    )
    val postedAt: String? = null
)
