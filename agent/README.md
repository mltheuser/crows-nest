# Agent Module

LLM-powered job scraping agent using the Koog agent framework with **schema learning**.

## Purpose

This module implements an AI agent that navigates job board websites, extracts job listings, and saves them to the database. It uses Gemini as the LLM and learns CSS selectors to minimize API calls.

## Schema Learning

The agent learns page structures to reduce LLM calls from O(n) to O(1) per domain:

1. **First visit**: Extracts content via LLM AND learns CSS selectors
2. **Validation**: Compares schema extraction vs LLM extraction
3. **Subsequent visits**: Uses only CSS selectors (no LLM calls)
4. **Re-learning**: If validation fails, re-learns the schema

```
First Run:  LLM call per page  → Learn schema
Next Runs:  Schema only        → 0 LLM calls (validated once per domain)
```

## Components

### `JobScraperAgent`
The main agent logic using Koog's functional strategy:
- `createJobScraperAgent()` - Factory requiring `SchemaRepository`
- `PageAnalysis` - Structured extraction of job listings
- `OfferDetails` - Full job details (description, requirements, postedAt)
- Automatic pagination handling (links and button-based)
- Rate limit retry logic for API errors

### `SchemaLearner`
Uses LLM to generate CSS selectors from HTML structure:
- `learnListingSchema()` - Learns selectors for job list pages
- `learnDetailSchema()` - Learns selectors for job detail pages
- Prompts LLM with condensed HTML summary

### `SchemaExtractor`
Applies learned CSS selectors to extract data:
- `extractListing()` - Extracts job list using selectors
- `extractDetail()` - Extracts job details using selectors
- No LLM calls - pure DOM parsing

### `SimilarityUtils`
Validation utilities for comparing extractions:
- `validateExtraction()` - Compares schema vs LLM output
- Exact matching for title, company, URL
- Fuzzy matching (80% Jaccard) for descriptions

### `JobDatabase`
Interface for persisting scraped offers:
- `OfferRepositoryJobDatabase` - Adapter to the database module
- `checkDuplicate()` - Two-tier deduplication

## Running

```bash
./gradlew :agent:run
```

Requires:
- `GEMINI_API_KEY` in `.env` file
- Database running (`docker-compose up -d`)

## Testing

```bash
./gradlew :agent:test
```

### Test Classes
| Test Class | Description |
|------------|-------------|
| `JobScraperAgentIntegrationTest` | Full agent run with mock server |
| `DuplicateDetectionTest` | URL and similarity-based dedup |
| `SchemaExtractionTest` | Schema repository and validation |
| `SimilarityUtilsTest` | Fuzzy matching utilities |

## Architecture

```
┌─────────────┐     ┌─────────────────┐     ┌──────────────┐
│   Gemini    │────▶│ JobScraperAgent │────▶│   Database   │
│    LLM      │     │   (this module) │     │    Module    │
└─────────────┘     └────────┬────────┘     └──────────────┘
                             │
                    ┌────────▼────────┐
                    │  SchemaLearner  │
                    │ SchemaExtractor │
                    └────────┬────────┘
                             │
                    ┌────────▼────────┐
                    │   Environment   │
                    │  (browser ctrl) │
                    └─────────────────┘
```
