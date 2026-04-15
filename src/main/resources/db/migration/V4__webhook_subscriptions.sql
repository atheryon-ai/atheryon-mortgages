-- Webhook subscriptions for event notifications
CREATE TABLE webhook_subscriptions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscriber_id   VARCHAR(100) NOT NULL,
    application_id  UUID,
    endpoint_url    VARCHAR(500) NOT NULL,
    events          JSONB NOT NULL,
    secret          VARCHAR(200) NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_webhooks_subscriber ON webhook_subscriptions(subscriber_id);
CREATE INDEX idx_webhooks_app ON webhook_subscriptions(application_id);
CREATE INDEX idx_webhooks_active ON webhook_subscriptions(active) WHERE active = true;
