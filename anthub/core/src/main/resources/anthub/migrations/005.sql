ALTER TABLE community_group_items ALTER COLUMN group_id DROP NOT NULL;
ALTER TABLE community_group_items ADD COLUMN owner TEXT;
UPDATE community_group_items i SET owner=d.body->>'owner' FROM documents d WHERE i.group_id=d.id;
CREATE INDEX community_personal_tasks ON community_group_items(owner) WHERE group_id IS NULL;
CREATE SEQUENCE community_task_code_sequence START 10000;
CREATE TABLE community_task_codes(task_id TEXT PRIMARY KEY REFERENCES community_group_items(id) ON DELETE CASCADE,code TEXT UNIQUE NOT NULL);
CREATE TABLE community_task_stocks(location TEXT PRIMARY KEY,task_id TEXT NOT NULL REFERENCES community_group_items(id) ON DELETE CASCADE,body JSONB NOT NULL);
CREATE INDEX community_task_stocks_task ON community_task_stocks(task_id);
