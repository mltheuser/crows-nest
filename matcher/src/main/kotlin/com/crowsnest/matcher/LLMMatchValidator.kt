package com.crowsnest.matcher

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.executeStructured
import com.crowsnest.database.Offer
import com.crowsnest.database.Seeker
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** LLM-based match validation result. */
@Serializable
@SerialName("MatchValidation")
@LLMDescription("Result of validating whether an offer matches a seeker's search")
data class MatchValidation(
        @property:LLMDescription(
                "Match quality score from 0 to 100. 60+ means it's a match, 80+ means send notification"
        )
        val score: Int,
        @property:LLMDescription(
                "Brief explanation of why this is or isn't a good match (1-2 sentences)"
        )
        val reason: String,
        @property:LLMDescription(
                "For job offers only: how well the seeker meets the requirements (0-100), or null for other offer types"
        )
        val seekerFitScore: Int? = null
)

/** Validates matches using LLM for final confirmation. */
class LLMMatchValidator(private val promptExecutor: PromptExecutor, private val llmModel: LLModel) {
        suspend fun validate(offer: Offer, seeker: Seeker, similarity: Float): MatchValidation {
                val offerText = buildOfferText(offer)
                val seekerText = buildSeekerText(seeker)

                val result =
                        promptExecutor.executeStructured<MatchValidation>(
                                prompt =
                                        prompt("match-validation") {
                                                system(
                                                        """
                    You are a job matching assistant. Analyze whether the offer matches what the seeker is looking for.
                    
                    Scoring guidelines:
                    - 0-30: Poor match, wrong industry/role/location
                    - 31-59: Partial match, some overlap but significant gaps
                    - 60-79: Good match, relevant offer worth considering
                    - 80-100: Excellent match, highly relevant and should notify user
                    
                    For job offers, also rate how well the seeker's profile meets the job requirements.
                """.trimIndent()
                                                )
                                                user(
                                                        """
                    OFFER:
                    $offerText
                    
                    SEEKER PROFILE:
                    $seekerText
                    
                    VECTOR SIMILARITY: ${String.format("%.2f", similarity)}
                    
                    Evaluate this match and provide a score, reason, and seeker fit score (for jobs).
                """.trimIndent()
                                                )
                                        },
                                model = llmModel,
                                fixingParser = StructureFixingParser(llmModel, retries = 2)
                        )

                return result.getOrNull()?.structure
                        ?: MatchValidation(score = 0, reason = "Failed to validate match")
        }

        private fun buildOfferText(offer: Offer): String = buildString {
                appendLine("Type: ${offer.type.name}")
                appendLine("Title: ${offer.title}")
                if (offer.source.isNotBlank()) appendLine("Company/Source: ${offer.source}")
                if (offer.location.isNotBlank()) appendLine("Location: ${offer.location}")
                if (offer.content.isNotBlank()) {
                        appendLine("Description:")
                        appendLine(offer.content.take(1500))
                }
        }

        private fun buildSeekerText(seeker: Seeker): String = buildString {
                appendLine("Looking for: ${seeker.type.name}")
                appendLine("Profile/Preferences:")
                appendLine(seeker.profile.take(1000))
        }
}
