-- Lender configuration profiles (EGB successor)
CREATE TABLE lender_profiles (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lender_code     VARCHAR(50) UNIQUE NOT NULL,
    lender_name     VARCHAR(200) NOT NULL,
    required_fields JSONB NOT NULL DEFAULT '[]',
    validation_rules JSONB NOT NULL DEFAULT '[]',
    product_rules   JSONB NOT NULL DEFAULT '[]',
    form_layout     JSONB,
    submission_config JSONB,
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_lender_profiles_code ON lender_profiles(lender_code);
CREATE INDEX idx_lender_profiles_active ON lender_profiles(active) WHERE active = true;
