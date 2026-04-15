-- Append-only event store for all domain events
CREATE TABLE domain_events (
    id              BIGSERIAL PRIMARY KEY,
    aggregate_id    UUID NOT NULL,
    aggregate_type  VARCHAR(50) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    event_data      JSONB NOT NULL,
    metadata        JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_events_aggregate ON domain_events(aggregate_id, created_at);
CREATE INDEX idx_events_type ON domain_events(event_type);
CREATE INDEX idx_events_created ON domain_events(created_at);
