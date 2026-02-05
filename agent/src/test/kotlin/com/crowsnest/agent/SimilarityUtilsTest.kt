package com.crowsnest.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SimilarityUtilsTest {

    @Test
    fun `normalize removes extra whitespace and lowercases`() {
        assertEquals("hello world", SimilarityUtils.normalize("  Hello   World  "))
        assertEquals("test", SimilarityUtils.normalize("TEST"))
        assertEquals("a b c", SimilarityUtils.normalize("A  B  C"))
    }

    @Test
    fun `normalizedEquals matches identical content with different formatting`() {
        assertTrue(SimilarityUtils.normalizedEquals("Software Engineer", "software engineer"))
        assertTrue(SimilarityUtils.normalizedEquals("  JetBrains  ", "jetbrains"))
        assertTrue(SimilarityUtils.normalizedEquals("New York, NY", "new york, ny"))
        assertFalse(SimilarityUtils.normalizedEquals("Engineer", "Developer"))
    }

    @Test
    fun `jaccardSimilarity returns 1 for identical strings`() {
        val result = SimilarityUtils.jaccardSimilarity(
            "Software Engineer at Google",
            "Software Engineer at Google"
        )
        assertEquals(1.0, result, 0.001)
    }

    @Test
    fun `jaccardSimilarity returns 0 for completely different strings`() {
        val result = SimilarityUtils.jaccardSimilarity(
            "apple banana cherry",
            "dog elephant fox"
        )
        assertEquals(0.0, result, 0.001)
    }

    @Test
    fun `jaccardSimilarity handles partial overlap`() {
        // Words: "the quick brown fox" and "the lazy brown dog"
        // Common: "the", "brown" (2 words)
        // Union: "the", "quick", "brown", "fox", "lazy", "dog" (6 words)
        // Jaccard = 2/6 = 0.333
        val result = SimilarityUtils.jaccardSimilarity(
            "the quick brown fox",
            "the lazy brown dog"
        )
        assertEquals(0.333, result, 0.01)
    }

    @Test
    fun `isSimilarText returns true for high similarity`() {
        assertTrue(SimilarityUtils.isSimilarText(
            "We are looking for a Software Engineer with 5 years experience",
            "We are looking for a Software Engineer with five years experience"
        ))
    }

    @Test
    fun `isSimilarText returns false for low similarity`() {
        assertFalse(SimilarityUtils.isSimilarText(
            "Software Engineer position",
            "Marketing Manager role with sales experience"
        ))
    }

    @Test
    fun `validateExtraction returns Valid when all fields match`() {
        val schema = mapOf(
            "title" to "Software Engineer",
            "company" to "Google",
            "description" to "We are looking for a talented software engineer to join our team"
        )
        val llm = mapOf(
            "title" to "software engineer",
            "company" to "GOOGLE",
            "description" to "We are looking for a talented software engineer to join our engineering team"
        )
        
        val result = SimilarityUtils.validateExtraction(schema, llm)
        assertTrue(result.isValid, "Expected valid but got: $result")
    }

    @Test
    fun `validateExtraction returns Invalid when exact field differs`() {
        val schema = mapOf(
            "title" to "Software Engineer",
            "company" to "Google"
        )
        val llm = mapOf(
            "title" to "Backend Developer",
            "company" to "Google"
        )
        
        val result = SimilarityUtils.validateExtraction(schema, llm)
        assertFalse(result.isValid)
        assertTrue(result is ValidationResult.Invalid)
        assertTrue((result as ValidationResult.Invalid).failures.any { it.contains("title") })
    }

    @Test
    fun `validateExtraction returns Invalid when fuzzy field below threshold`() {
        val schema = mapOf(
            "title" to "Engineer",
            "description" to "This is about apples and oranges"
        )
        val llm = mapOf(
            "title" to "Engineer",
            "description" to "This is about dogs and cats and birds"
        )
        
        val result = SimilarityUtils.validateExtraction(schema, llm)
        assertFalse(result.isValid)
    }
}
