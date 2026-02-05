CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE offers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    url TEXT UNIQUE,

    title TEXT NOT NULL,
    company TEXT NOT NULL,
    locations TEXT NOT NULL,
    description TEXT NOT NULL,
    posted_at TIMESTAMP,

    scraped_at TIMESTAMP DEFAULT NOW(),

    embedding vector(3072)
);