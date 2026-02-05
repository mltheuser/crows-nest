package com.crowsnest.agent.mocks

import com.crowsnest.agent.models.OfferDetails
import com.crowsnest.agent.models.OfferSummary
import com.crowsnest.agent.models.ListingItems
import com.crowsnest.agent.models.PaginationInfo
import com.crowsnest.database.repositories.schema.PageSchema
import com.crowsnest.database.repositories.schema.PageType

/**
 * Test data factories for creating mock schemas and data.
 */
object TestDataFactory {
    
    /**
     * Create a valid listing page schema.
     */
    fun createListingSchema(
        domain: String = "example.com",
        offerItemsSelector: String = ".job-item",
        titleSelector: String = ".job-title",
        urlSelector: String = "a.job-link",
        companySelector: String? = ".company-name",
        staticCompany: String? = null
    ): PageSchema {
        val selectors = mutableMapOf(
            "offerItems" to offerItemsSelector,
            "offerTitle" to titleSelector,
            "offerUrl" to urlSelector
        )
        companySelector?.let { selectors["offerCompany"] = it }
        
        val staticValues = mutableMapOf<String, String>()
        staticCompany?.let { staticValues["offerCompany"] = it }
        
        return PageSchema(
            domain = domain,
            pageType = PageType.LISTING,
            selectors = selectors,
            staticValues = staticValues
        )
    }
    
    /**
     * Create a valid detail page schema using simple string parameters.
     * This is the preferred API for tests as it's more concise.
     */
    fun createDetailSchema(
        domain: String = "example.com",
        titleSelector: String = "h1.job-title",
        companySelector: String? = ".company-name",
        staticCompany: String? = null,
        locationSelector: String? = null,
        staticLocation: String? = "Remote",  // Default static location since detail pages often lack this
        descriptionSelector: String? = ".description",
        postedAtSelector: String? = null
    ): PageSchema {
        val selectors = mutableMapOf("title" to titleSelector)
        companySelector?.let { selectors["company"] = it }
        locationSelector?.let { selectors["location"] = it }
        descriptionSelector?.let { selectors["description"] = it }
        postedAtSelector?.let { selectors["postedAt"] = it }
        
        val staticValues = mutableMapOf<String, String>()
        staticCompany?.let { staticValues["company"] = it }
        // Add static location only if no location selector is provided
        if (locationSelector == null && staticLocation != null) {
            staticValues["location"] = staticLocation
        }
        
        return PageSchema(
            domain = domain,
            pageType = PageType.DETAIL,
            selectors = selectors,
            staticValues = staticValues
        )
    }
    
    /**
     * Create sample offer summaries for testing.
     */
    fun createOfferSummaries(
        count: Int = 2,
        company: String = "TestCorp"
    ): List<OfferSummary> {
        return (1..count).map { i ->
            OfferSummary(
                title = "Job Title $i",
                company = company,
                location = "Remote",
                url = "https://example.com/job/$i"
            )
        }
    }
    
    /**
     * Create a sample page analysis result.
     */
    fun createListingItems(
        offers: List<OfferSummary> = createOfferSummaries(),
        nextPageUrl: String? = null,
        nextPageButtonSelector: String? = null
    ): ListingItems {
        val pagination = if (nextPageUrl != null || nextPageButtonSelector != null) {
            PaginationInfo(url = nextPageUrl, buttonSelector = nextPageButtonSelector)
        } else null
        
        return ListingItems(
            offers = offers,
            pagination = pagination
        )
    }
    
    /**
     * Create sample offer details for testing.
     */
    fun createOfferDetails(
        title: String = "Software Engineer",
        company: String = "TestCorp",
        location: String = "Remote",
        description: String = "A great job opportunity.",
        isValid: Boolean = true
    ): OfferDetails {
        return OfferDetails(
            isValid = isValid,
            title = title,
            company = company,
            location = location,
            description = description
        )
    }
}
