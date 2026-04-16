-- Remediation action log (append-only audit trail)
CREATE TABLE remediation_actions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id          UUID NOT NULL REFERENCES migration_jobs(id),
    rule_id         VARCHAR(20) NOT NULL,
    description     VARCHAR(500) NOT NULL,
    condition       JSONB NOT NULL,
    transform       JSONB NOT NULL,
    affected_rows   INTEGER NOT NULL,
    quality_before  DECIMAL(5,4) NOT NULL,
    quality_after   DECIMAL(5,4) NOT NULL,
    actor_id        VARCHAR(100) NOT NULL,
    applied_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_remediation_job ON remediation_actions(job_id);
CREATE INDEX idx_remediation_rule ON remediation_actions(rule_id);
