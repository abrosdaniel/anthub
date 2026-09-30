ALTER TABLE skin_library ADD COLUMN position INTEGER NOT NULL DEFAULT 0;
WITH ranked AS (SELECT id,row_number() OVER (PARTITION BY owner ORDER BY name,id)-1 AS ordinal FROM skin_library)
UPDATE skin_library SET position=ranked.ordinal FROM ranked WHERE skin_library.id=ranked.id;
CREATE INDEX skin_library_order ON skin_library(owner,position);
