# Environment Module

Browser automation and web scraping infrastructure for the CrowsNest agent.

## Purpose

This module provides the agent with the ability to interact with web pages. It wraps browser automation in a clean interface that can be exposed as tools to the LLM agent and used for schema-based extraction.

## Components

### `BrowserController`
Interface for browser automation with the following capabilities:

| Method | Description |
|--------|-------------|
| `start()` | Initialize browser session |
| `openUrl(url)` | Navigate to a URL |
| `clickElement(selector)` | Click elements using CSS selectors |
| `getSnapshot()` | Get Markdown representation for LLM |
| `getHtmlSummary()` | Get condensed HTML for schema learning |
| `extractBySelector(selector)` | Extract text content by CSS selector |
| `extractAttributeBySelector(selector, attr)` | Extract attribute values |
| `getRawHtml()` | Get full HTML content |

### `KDriverBrowserController`
Implementation using [KDriver](https://github.com/nickaboot/kdriver) for headless browser automation:
- Chromium-based headless browsing
- HTML to Markdown conversion using KHtmlToMarkdown
- **HTML Summary**: Condensed view for LLM selector learning
- **CSS Extraction**: Direct DOM querying for schema-based extraction
- Extracts and formats links and buttons with CSS selectors
- Resolves relative URLs to absolute

### `NavigationTools`
LLM-ready toolset for the Koog agent framework:
- `openUrl` - Navigate to URLs
- `clickElement` - Click elements by CSS selector
- `stopBrowsing` - End browsing session

## HTML Summary

The `getHtmlSummary()` method creates a condensed HTML view for schema learning:
- Removes scripts, styles, comments
- Preserves class, id, href attributes
- Samples first 2 of repeating elements
- Truncates long text (>100 chars)

This helps the LLM generate accurate CSS selectors without overwhelming context.

## Testing

Tests use a mock HTTP server to simulate web pages:

```bash
./gradlew :environment:test
```

### Test Structure
- `BrowserControllerTest.kt` - Integration tests for browser automation
- `MockJobBoardServer.kt` - Embedded Ktor server serving test HTML pages

### Test Cases
| Test | Description |
|------|-------------|
| `test navigation tool interaction` | Basic page navigation |
| `test button pagination` | Click-based pagination |
| `test getHtmlSummary` | HTML condensing for schema learning |
| `test extractBySelector` | CSS selector extraction |
| `test extractAttributeBySelector` | Attribute value extraction |

### Test Resources
Located in `src/test/resources/www/`:
- Static HTML pages for testing navigation
- JavaScript for testing dynamic content loading
