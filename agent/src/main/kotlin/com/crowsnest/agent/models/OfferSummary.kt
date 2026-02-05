package com.crowsnest.agent.models

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("OfferSummary")
@LLMDescription("Summary of a job offer from the list")
data class OfferSummary(
    @property:LLMDescription("The job title") val title: String,
    @property:LLMDescription("The company name") val company: String,
    @property:LLMDescription("The job location if visible") val location: String = "",
    @property:LLMDescription("The absolute URL to the offer details page") val url: String,
)
