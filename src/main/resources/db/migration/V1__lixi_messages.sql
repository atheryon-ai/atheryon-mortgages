-- Store original LIXI2 messages for audit + round-trip fidelity
CREATE TABLE lixi_messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id  UUID,  -- will FK to loan_applications when that table exists via Hibernate
    direction       VARCHAR(10) NOT NULL CHECK (direction IN ('INBOUND', 'OUTBOUND')),
    standard        VARCHAR(10) NOT NULL,
    version         VARCHAR(20) NOT NULL,
    format          VARCHAR(4) NOT NULL CHECK (format IN ('JSON', 'XML')),
    payload         JSONB NOT NULL,
    validation_result JSONB,
    sender_id       VARCHAR(100),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_lixi_messages_app ON lixi_messages(application_id);
CREATE INDEX idx_lixi_messages_sender ON lixi_messages(sender_id);
CREATE INDEX idx_lixi_messages_created ON lixi_messages(created_at);
