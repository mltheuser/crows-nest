package com.crowsnest.agent.models

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable

@Serializable
data class PaginationInfo(
    @property:LLMDescription("Direct absolute URL to next page, if available")
    val url: String? = null,
    @property:LLMDescription("CSS selector for next page button, if URL not available")
    val buttonSelector: String? = null
)
