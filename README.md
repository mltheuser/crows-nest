# CrowsNest

CrowsNest is a modular framework designed for building AI-powered browser agents. It provides a robust environment abstraction that allows Large Language Models (LLMs) to interact with web pages through a simplified, tool-based interface.

## Project Purpose

The primary goal of CrowsNest is to decouple the **Agent** (the reasoning engine) from the **Environment** (the browser and web page). By providing a clean `BrowserController` interface and a set of LLM-ready `NavigationTools`, CrowsNest enables developers to build agents that can:

*   Navigate web pages (open URLs, go back, pagination).
*   Interact with elements using a stable ID system.
*   Perceive the web page through simplified text snapshots, optimized for LLM token usage.

## Architecture

The project is organized as a multi-module Gradle project:

### 1. `:environment` Module
This module contains the core infrastructure for browser interaction. It is designed to be the "body" of the agent.

*   **`BrowserController`**: The central interface defining low-level browser operations (`openUrl`, `clickLink`, `getSnapshot`).
*   **`NavigationTools`**: A wrapper around the controller that exposes these operations as `@Tool` annotated functions, ready to be consumed by the Koog agent framework.
*   **`KDriverBrowserController`**: (Implementation) A concrete implementation of the controller (likely using a driver like Selenium or Playwright) to perform the actual browser automation.

### 2. `:agent` Module
*Status: Under Construction*

This module is intended to house the "brain" of the application. It will contain:
*   The main application entry point (`AppKt`).
*   The Agent definition (e.g., `JobScraperAgent`), which utilizes the tools provided by the `:environment` module to achieve specific goals (like scraping job offers).

## Project Structure

```text
CrowsNest/
├── agent/                  # The Agent logic and application entry point
│   ├── build.gradle.kts
│   └── src/main/kotlin/    # (Currently empty, awaiting Agent implementation)
├── environment/            # Browser abstraction and tools
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/crowsnest/environment/
│       ├── BrowserController.kt       # Interface for browser control
│       ├── NavigationTools.kt         # Koog Tools for navigation
│       └── KDriverBrowserController.kt # Implementation of the controller
├── buildSrc/               # Gradle convention plugins
├── build.gradle.kts        # Root build configuration
└── settings.gradle.kts     # Project settings and module inclusion
```

## Key Concepts

*   **Snapshotting**: Instead of feeding raw HTML to the LLM, CrowsNest generates a "snapshot" of the current page. This snapshot lists interactive elements with unique numerical IDs (e.g., `[1] Senior Kotlin Developer`).
*   **Tool-Use**: The Agent does not interact with the browser directly. Instead, it issues commands via `NavigationTools` (e.g., `clickLink("1")`), which the `BrowserController` translates into actual browser actions.

## Getting Started

1.  **Build the project**:
    ```bash
    ./gradlew build
    ```
2.  **Run the Agent** (Once implemented):
    ```bash
    ./gradlew :agent:run
    ```