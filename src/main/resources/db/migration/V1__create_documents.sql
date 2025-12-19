CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS documents (
    id UUID PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    content_hash VARCHAR(255) NOT NULL UNIQUE,
    vector_id VARCHAR(255),
    indexed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS document_metadata (
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    metadata_key VARCHAR(255) NOT NULL,
    metadata_value VARCHAR(255),
    PRIMARY KEY (document_id, metadata_key)
);

CREATE INDEX IF NOT EXISTS idx_document_metadata_key_value
    ON document_metadata (metadata_key, metadata_value);
