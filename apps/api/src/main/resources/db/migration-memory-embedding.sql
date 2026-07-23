-- Add embedding storage for retrieval-based proactive (existing DBs)
ALTER TABLE memories
  ADD COLUMN embedding_json LONGTEXT NULL AFTER strength;
