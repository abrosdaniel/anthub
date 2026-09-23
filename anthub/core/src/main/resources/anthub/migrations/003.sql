CREATE TABLE skin_images(hash TEXT PRIMARY KEY, png BYTEA NOT NULL);
CREATE TABLE skin_library(id UUID PRIMARY KEY, owner UUID NOT NULL, hash TEXT NOT NULL REFERENCES skin_images(hash), name TEXT NOT NULL, slim BOOLEAN NOT NULL, UNIQUE(owner,hash));
CREATE INDEX skin_library_owner ON skin_library(owner);
CREATE TABLE skin_profiles(owner UUID PRIMARY KEY, active UUID REFERENCES skin_library(id) ON DELETE SET NULL, fallback TEXT REFERENCES skin_images(hash), slim BOOLEAN NOT NULL DEFAULT FALSE, source TEXT NOT NULL DEFAULT '', checked BIGINT NOT NULL DEFAULT 0);
