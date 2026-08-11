-- How often each knowledge-base entry actually gets pulled into a real call - lets an owner see
-- what the agent leans on versus what has been sitting unused since it was written.
ALTER TABLE rag_documents ADD COLUMN hit_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE rag_documents ADD COLUMN last_used_at TIMESTAMPTZ;
