# Matcher Module

Three-phase embedding and matching worker using Gemini API.

## Purpose

This module processes new offers and seekers through three phases:
1. **Embed** - Batch embed seekers (HIGH priority) then offers using Gemini
2. **Match** - Find similar items via vector search, validate with LLM
3. **Notify** - Send email notifications for high-score matches

## Components

### `MatcherWorker`
The main worker that runs continuously with three phases:
- `embedPhase()` - Embeds items without embeddings (seekers first)
- `matchPhase()` - Finds similar items and validates with LLM
- `notifyPhase()` - Sends notifications for unnotified high-score matches

### `GeminiEmbeddingService`
Generates embeddings using Gemini API:
- Model: `gemini-embedding-001`
- Dimensions: 3072 (full precision)
- Supports batch embedding for efficiency

### `LLMMatchValidator`
Validates matches using LLM for final confirmation:
- Returns score (0-100), reason, and seeker fit score
- Uses structured output with Koog

### `EmailService` / `MockEmailService`
Interface for sending match notifications:
- `MockEmailService` logs to console (for development)
- Real Gmail API implementation can be added later

## Configuration

```kotlin
MatcherConfig(
    similarityThreshold = 0.85f,  // Minimum similarity for LLM validation
    matchSaveThreshold = 60,      // Save match if LLM score >= 60
    notifyThreshold = 80,         // Send notification if score >= 80
    batchSize = 16,               // Batch size for embedding
    embeddingDelayMs = 2000,      // Rate limit: ~32 calls/min
    llmDelayMs = 6000             // Rate limit: ~10 calls/min
)
```

## Running

```bash
./gradlew :matcher:run
```

Requires:
- `GEMINI_API_KEY` in `.env` file
- Database running (`docker-compose up -d`)

## Testing

```bash
./gradlew :matcher:test
```

Integration tests require:
- PostgreSQL running (`docker-compose up -d`)
- `GEMINI_API_KEY` in `.env` file

Tests fail with clear error messages if dependencies are missing.

### Test Cases
| Test | Description |
|------|-------------|
| `embedding service generates valid embeddings` | Live Gemini API |
| `batch embedding works for multiple texts` | Batch API |
| `LLM validator scores good match highly` | LLM validation (good match) |
| `LLM validator scores poor match low` | LLM validation (poor match) |
| `full matching cycle processes offer and seeker` | End-to-end cycle |
| `notification phase sends emails for high score matches` | Mock email service |

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    MatcherWorker                         │
├─────────────────┬─────────────────┬─────────────────────┤
│   Embed Phase   │   Match Phase   │    Notify Phase     │
│                 │                 │                     │
│ Seekers (HIGH)  │ Similarity ≥85% │ LLM Score ≥80%     │
│ Offers (NORMAL) │ LLM Validate    │ Send Email          │
│ → embedBatch()  │ → save if ≥60   │ → markNotified()   │
└────────┬────────┴────────┬────────┴──────────┬──────────┘
         │                 │                   │
         ▼                 ▼                   ▼
   ┌──────────┐     ┌────────────┐     ┌─────────────┐
   │ Gemini   │     │ PostgreSQL │     │ EmailService│
   │ Embedding│     │ + pgvector │     │   (mock)    │
   └──────────┘     └────────────┘     └─────────────┘
```
