package com.crowsnest.agent.models

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("Listings")
@LLMDescription("Job offers extracted from a listing page")
data class Listings(
    @property:LLMDescription("List of job offers found on the page")
    val offers: List<OfferSummary>,
    @property:LLMDescription("Pagination controls for navigating to the next page of job offers. Null if no next pagination.")
    val pagination: PaginationInfo? = null
)
