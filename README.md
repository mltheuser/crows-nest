# CrowsNest

CrowsNest is an AI-powered job scraping and matching platform. It uses LLM agents to discover job offers from various boards, stores them in a PostgreSQL database with vector embeddings, and matches them to job seekers using similarity search.

## Architecture

```
CrowsNest/
├── agent/          # AI agent for web scraping
├── matcher/        # Embedding and matching worker
├── environment/    # Browser abstraction (KDriver)
├── database/       # PostgreSQL + pgvector integration
├── web/            # Web UI for seeker registration
└── docker-compose.yml
```

---

## Quick Start

### Prerequisites
- Java 21+
- Docker & Docker Compose
- Create `.env` file with `GEMINI_API_KEY` (see Environment Variables)

### 1. Build the Project
```bash
./gradlew build
```

### 2. Run All Tests
```bash
./gradlew testAll
```

### 3. Run a Specific Module's Tests
```bash
./gradlew :environment:test
./gradlew :database:test
./gradlew :agent:test
./gradlew :matcher:test
```

---

## Local Integration Testing

### Start the Database
```bash
docker-compose up -d
```

This starts PostgreSQL 16 with pgvector on `localhost:5432`.

### Verify Database is Running
```bash
docker exec -it crowsnest-postgres-1 psql -U dev -d crowsnest -c "\dt"
```

### Run the Agent
```bash
./gradlew :agent:run
```

The agent will scrape job offers and save them to the database.

### Run the Matcher
```bash
./gradlew :matcher:run
```

The matcher will embed offers/seekers and find matches using LLM validation.

### Run the Web Server
```bash
./gradlew :web:run
```

Then open http://localhost:8080

### View Scraped Data
```bash
docker exec -it crowsnest-postgres-1 psql -U dev -d crowsnest -c "SELECT title, company, locations FROM offers LIMIT 10;"
```

### View Learned Schemas
```bash
docker exec -it crowsnest-postgres-1 psql -U dev -d crowsnest -c "SELECT domain, page_type, selectors FROM page_schemas;"
```

### Clear Offers Only
```bash
docker exec -it crowsnest-postgres-1 psql -U dev -d crowsnest -c "DELETE FROM offers;"
```

### Clear Schemas Only
```bash
docker exec -it crowsnest-postgres-1 psql -U dev -d crowsnest -c "DELETE FROM page_schemas;"
```

### Clear All Tables (Reset Database)
```bash
docker exec -it crowsnest-postgres-1 psql -U dev -d crowsnest -c "TRUNCATE offers, seekers, matches, users, page_schemas CASCADE;"
```

### Full Reset (Delete Volume)
```bash
docker-compose down -v
docker-compose up -d
```

---

## Modules

### `:agent`
AI-powered scraping agent using Koog framework with **schema learning**.
- Learns CSS selectors per domain to reduce LLM calls
- Validates learned schemas against LLM extraction
- Automatic fallback to LLM when schema fails
- Hybrid pagination (URL links + button clicks)
- Two-tier duplicate detection (URL + title/company/location)
- Automatic retry on rate limits (429 errors)

### `:matcher`
Three-phase embedding and matching worker.
- **Embed phase**: Batch embed seekers (HIGH priority) and offers
- **Match phase**: Similarity search + LLM validation
- **Notify phase**: Email notifications for high-score matches
- Uses Gemini `gemini-embedding-001` (3072 dimensions)

### `:environment`
Browser abstraction layer using KDriver for headless Chrome automation.
- `BrowserController` - Interface for browser operations
- `getHtmlSummary()` - Condensed HTML for schema learning
- `extractBySelector()` - CSS selector-based extraction
- `NavigationTools` - LLM-ready tools for the agent

### `:database`
PostgreSQL integration with pgvector for vector similarity search.
- `OfferRepository` - CRUD for job/housing/retail offers
- `SeekerRepository` - CRUD for seekers
- `MatchRepository` - CRUD for matches with LLM scores
- `SchemaRepository` - CRUD for learned page schemas
- `DatabaseFactory` - Factory for creating repositories

### `:web`
Web UI for seeker registration using Ktor + htmx.
- htmx for dynamic updates without custom JS
- Ktor HTML DSL for type-safe templates

---

## Gradle Setup

All modules share a common build configuration:

- **Version catalog** (`gradle/libs.versions.toml`) - all dependency versions defined centrally
- **Convention plugin** - automatically applied to all subprojects from root `build.gradle.kts`
- **Global test command**: `./gradlew testAll`

---

## Environment Variables

Create a `.env` file in the project root (it's gitignored):
```bash
cp .env.example .env
# Edit .env with your values
```

The `.env` file is automatically loaded by Gradle for all `run` and `test` tasks.

| Variable | Default | Description |
|----------|---------|-------------|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/crowsnest` | PostgreSQL JDBC URL |
| `DATABASE_USER` | `dev` | Database username |
| `DATABASE_PASSWORD` | `dev` | Database password |
| `GEMINI_API_KEY` | (required) | Google Gemini API key |
| `GOOGLE_CLIENT_ID` | (optional) | Google OAuth client ID |
| `GOOGLE_CLIENT_SECRET` | (optional) | Google OAuth client secret |

---

## Database Schema

```sql
-- Offers (jobs, housing, retail)
CREATE TABLE offers (
    id UUID PRIMARY KEY,
    type TEXT,           -- 'job', 'housing', 'retail'
    title TEXT,
    source TEXT,         -- company/landlord/store
    location TEXT,
    content TEXT,
    url TEXT UNIQUE,
    scraped_at TIMESTAMP,
    posted_at TIMESTAMP,
    embedding vector(3072),
    matched BOOLEAN
);

-- Seekers
CREATE TABLE seekers (
    id UUID PRIMARY KEY,
    user_id UUID,
    type TEXT,
    email TEXT,
    profile TEXT,
    embedding vector(3072)
);

-- Matches
CREATE TABLE matches (
    id UUID PRIMARY KEY,
    offer_id UUID,
    seeker_id UUID,
    similarity FLOAT,
    llm_score INTEGER,   -- 0-100
    llm_reason TEXT,
    seeker_fit_score INTEGER,
    notified BOOLEAN,
    created_at TIMESTAMP,
    UNIQUE(offer_id, seeker_id)
);

-- Learned page schemas (for reducing LLM calls)
CREATE TABLE page_schemas (
    id UUID PRIMARY KEY,
    domain TEXT,
    page_type TEXT,      -- 'listing' or 'detail'
    selectors TEXT,      -- JSON: {"title": ".job-title", ...}
    created_at TIMESTAMP,
    UNIQUE(domain, page_type)
);
```

IVFFlat indexes are created for fast similarity search at scale (500k+ vectors).