-- Passage count per document, added when indexing moved from one vector per
-- document to one per passage. It is a new migration and not an edit to V1,
-- because V1 has already run wherever this is deployed. Editing it would leave
-- Flyway with a checksum that no longer matches and a column that never arrives.
ALTER TABLE documents ADD COLUMN IF NOT EXISTS passage_count INTEGER NOT NULL DEFAULT 0;
