CREATE TABLE community_events(id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,topic TEXT NOT NULL,entity TEXT NOT NULL,recipient TEXT NOT NULL,created BIGINT NOT NULL,delivered BOOLEAN NOT NULL DEFAULT FALSE);
CREATE INDEX community_events_pending ON community_events(id) WHERE NOT delivered;
CREATE INDEX community_events_expiry ON community_events(created);
