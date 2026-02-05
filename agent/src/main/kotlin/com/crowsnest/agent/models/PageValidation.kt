package com.crowsnest.agent.models

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable

@Serializable
data class PageValidation(
    @property:LLMDescription("Is this page a valid job offer that contains all necessary details?")
    val isValid: Boolean,
    
    @property:LLMDescription("Reasoning for the validity decision (e.g., 'Valid job offer', 'Page is a captcha', 'Job not found')")
    val reason: String
)
