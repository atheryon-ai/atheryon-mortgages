-- V1__baseline_schema.sql
-- Baseline schema for Atheryon Mortgages (PostgreSQL dialect)

-- Application number sequence
CREATE SEQUENCE application_number_seq START WITH 1 INCREMENT BY 1;

-- ============================================================
-- Products
-- ============================================================
CREATE TABLE products (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_type    VARCHAR(255) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    brand           VARCHAR(255),
    effective_from  DATE         NOT NULL,
    effective_to    DATE,
    maximum_ltv     NUMERIC(10, 6),
    minimum_loan_amount NUMERIC(19, 4),
    maximum_loan_amount NUMERIC(19, 4),
    minimum_term_months INTEGER      NOT NULL,
    maximum_term_months INTEGER      NOT NULL,
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP
);

-- Product features (ElementCollection)
CREATE TABLE product_features (
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    feature    VARCHAR(255)
);

-- Lending rates
CREATE TABLE lending_rates (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id             UUID         NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    lending_rate_type      VARCHAR(255) NOT NULL,
    rate                   NUMERIC(10, 6) NOT NULL,
    comparison_rate        NUMERIC(10, 6),
    calculation_frequency  VARCHAR(255),
    application_frequency  VARCHAR(255),
    fixed_term_months      INTEGER
);

-- ============================================================
-- Parties
-- ============================================================
CREATE TABLE parties (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    party_type          VARCHAR(255) NOT NULL,
    title               VARCHAR(255),
    first_name          VARCHAR(255) NOT NULL,
    middle_names        VARCHAR(255),
    surname             VARCHAR(255) NOT NULL,
    date_of_birth       DATE,
    gender              VARCHAR(255),
    residency_status    VARCHAR(255),
    marital_status      VARCHAR(255),
    number_of_dependants INTEGER     NOT NULL,
    email               VARCHAR(255),
    mobile_phone        VARCHAR(255),
    kyc_status          VARCHAR(255),
    created_at          TIMESTAMP,
    updated_at          TIMESTAMP
);

-- Party addresses
CREATE TABLE party_addresses (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    party_id        UUID         NOT NULL REFERENCES parties(id) ON DELETE CASCADE,
    address_type    VARCHAR(255),
    unit_number     VARCHAR(255),
    street_number   VARCHAR(255),
    street_name     VARCHAR(255),
    street_type     VARCHAR(255),
    suburb          VARCHAR(255),
    state           VARCHAR(255),
    postcode        VARCHAR(255),
    country         VARCHAR(255),
    years_at_address INTEGER     NOT NULL,
    months_at_address INTEGER    NOT NULL,
    housing_status  VARCHAR(255)
);

-- Party identifications
CREATE TABLE party_identifications (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    party_id      UUID         NOT NULL REFERENCES parties(id) ON DELETE CASCADE,
    id_type       VARCHAR(255),
    id_number     VARCHAR(255),
    issuing_state VARCHAR(255),
    expiry_date   DATE,
    verified      BOOLEAN      NOT NULL
);

-- Employments
CREATE TABLE employments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    party_id            UUID         NOT NULL REFERENCES parties(id) ON DELETE CASCADE,
    employment_type     VARCHAR(255) NOT NULL,
    employer_name       VARCHAR(255),
    occupation          VARCHAR(255),
    industry            VARCHAR(255),
    start_date          DATE         NOT NULL,
    end_date            DATE,
    annual_base_salary  NUMERIC(19, 4),
    annual_overtime     NUMERIC(19, 4),
    annual_bonus        NUMERIC(19, 4),
    annual_commission   NUMERIC(19, 4),
    is_current          BOOLEAN      NOT NULL
);

-- ============================================================
-- Loan Applications
-- ============================================================
CREATE TABLE loan_applications (
    id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_number         VARCHAR(255) NOT NULL UNIQUE,
    status                     VARCHAR(255) NOT NULL,
    channel                    VARCHAR(255) NOT NULL,
    product_id                 UUID REFERENCES products(id),
    purpose                    VARCHAR(255),
    occupancy_type             VARCHAR(255),
    requested_amount           NUMERIC(19, 4),
    term_months                INTEGER      NOT NULL,
    interest_type              VARCHAR(255),
    repayment_type             VARCHAR(255),
    repayment_frequency        VARCHAR(255),
    fixed_portion_amount       NUMERIC(19, 4),
    fixed_term_months          INTEGER,
    interest_only_period_months INTEGER,
    first_home_buyer           BOOLEAN      NOT NULL,
    assigned_to                VARCHAR(255),
    created_at                 TIMESTAMP,
    updated_at                 TIMESTAMP,
    submitted_at               TIMESTAMP,
    decisioned_at              TIMESTAMP,
    offer_issued_at            TIMESTAMP,
    offer_accepted_at          TIMESTAMP,
    settlement_date            DATE,
    settled_at                 TIMESTAMP,
    withdrawn_at               TIMESTAMP
);

-- Application parties (join table)
CREATE TABLE application_parties (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id        UUID         NOT NULL REFERENCES loan_applications(id) ON DELETE CASCADE,
    party_id              UUID         NOT NULL REFERENCES parties(id),
    role                  VARCHAR(255) NOT NULL,
    ownership_percentage  NUMERIC(10, 6)
);

-- ============================================================
-- Securities (property)
-- ============================================================
CREATE TABLE securities (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id      UUID         NOT NULL REFERENCES loan_applications(id) ON DELETE CASCADE,
    security_type       VARCHAR(255) NOT NULL,
    primary_purpose     VARCHAR(255),
    property_category   VARCHAR(255),
    street_number       VARCHAR(255),
    street_name         VARCHAR(255),
    street_type         VARCHAR(255),
    suburb              VARCHAR(255),
    state               VARCHAR(255),
    postcode            VARCHAR(255),
    number_of_bedrooms  INTEGER,
    land_area_sqm       NUMERIC(19, 4),
    year_built          INTEGER,
    is_new_construction BOOLEAN      NOT NULL,
    purchase_price      NUMERIC(19, 4),
    contract_date       DATE,
    created_at          TIMESTAMP,
    updated_at          TIMESTAMP
);

-- Valuations
CREATE TABLE valuations (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    security_id          UUID         NOT NULL UNIQUE REFERENCES securities(id) ON DELETE CASCADE,
    valuation_type       VARCHAR(255) NOT NULL,
    provider             VARCHAR(255),
    requested_date       DATE,
    completed_date       DATE,
    status               VARCHAR(255) NOT NULL,
    estimated_value      NUMERIC(19, 4),
    forced_sale_value    NUMERIC(19, 4),
    valuation_confidence VARCHAR(255),
    calculated_ltv       NUMERIC(10, 6),
    report_reference     VARCHAR(255),
    valuer_comments      VARCHAR(2000),
    expiry_date          DATE
);

-- LMI quotes
CREATE TABLE lmi_quotes (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    security_id           UUID         NOT NULL UNIQUE REFERENCES securities(id) ON DELETE CASCADE,
    application_id        UUID,
    insurer               VARCHAR(255),
    quote_date            DATE,
    ltv_band              VARCHAR(255),
    loan_amount           NUMERIC(19, 4),
    property_value        NUMERIC(19, 4),
    premium               NUMERIC(19, 4),
    stamp_duty_on_premium NUMERIC(19, 4),
    total_cost            NUMERIC(19, 4),
    capitalised           BOOLEAN      NOT NULL,
    quote_reference       VARCHAR(255),
    quote_expiry_date     DATE,
    status                VARCHAR(255) NOT NULL
);

-- ============================================================
-- Financial snapshots
-- ============================================================
CREATE TABLE financial_snapshots (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id              UUID         NOT NULL UNIQUE REFERENCES loan_applications(id) ON DELETE CASCADE,
    captured_at                 TIMESTAMP,
    total_gross_annual_income   NUMERIC(19, 4),
    total_net_annual_income     NUMERIC(19, 4),
    declared_monthly_expenses   NUMERIC(19, 4),
    hem_monthly_benchmark       NUMERIC(19, 4),
    assessed_monthly_expenses   NUMERIC(19, 4),
    net_disposable_income       NUMERIC(19, 4),
    debt_service_ratio          NUMERIC(10, 6),
    uncommitted_monthly_income  NUMERIC(19, 4),
    assessment_rate             NUMERIC(10, 6),
    buffer_rate                 NUMERIC(10, 6),
    serviceability_outcome      VARCHAR(255),
    created_at                  TIMESTAMP
);

-- Income items
CREATE TABLE income_items (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    financial_snapshot_id UUID         NOT NULL REFERENCES financial_snapshots(id) ON DELETE CASCADE,
    party_id              UUID,
    income_type           VARCHAR(255) NOT NULL,
    gross_annual_amount   NUMERIC(19, 4),
    net_annual_amount     NUMERIC(19, 4),
    frequency             VARCHAR(255),
    verified              BOOLEAN      NOT NULL,
    verification_source   VARCHAR(255)
);

-- Expense items
CREATE TABLE expense_items (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    financial_snapshot_id UUID         NOT NULL REFERENCES financial_snapshots(id) ON DELETE CASCADE,
    category              VARCHAR(255),
    monthly_amount        NUMERIC(19, 4),
    frequency             VARCHAR(255),
    source                VARCHAR(255)
);

-- Assets
CREATE TABLE assets (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    financial_snapshot_id UUID         NOT NULL REFERENCES financial_snapshots(id) ON DELETE CASCADE,
    asset_type            VARCHAR(255) NOT NULL,
    description           VARCHAR(255),
    estimated_value       NUMERIC(19, 4),
    party_id              UUID
);

-- Liabilities
CREATE TABLE liabilities (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    financial_snapshot_id UUID         NOT NULL REFERENCES financial_snapshots(id) ON DELETE CASCADE,
    liability_type        VARCHAR(255) NOT NULL,
    lender                VARCHAR(255),
    outstanding_balance   NUMERIC(19, 4),
    credit_limit          NUMERIC(19, 4),
    monthly_repayment     NUMERIC(19, 4),
    interest_rate         NUMERIC(10, 6),
    to_be_refinanced      BOOLEAN      NOT NULL,
    party_id              UUID
);

-- ============================================================
-- Documents
-- ============================================================
CREATE TABLE documents (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id     UUID         NOT NULL REFERENCES loan_applications(id) ON DELETE CASCADE,
    party_id           UUID,
    document_type      VARCHAR(255) NOT NULL,
    document_category  VARCHAR(255) NOT NULL,
    status             VARCHAR(255) NOT NULL,
    file_name          VARCHAR(255),
    mime_type          VARCHAR(255),
    file_size_bytes    BIGINT       NOT NULL,
    storage_reference  VARCHAR(255),
    uploaded_by        VARCHAR(255),
    uploaded_at        TIMESTAMP,
    verified_by        VARCHAR(255),
    verified_at        TIMESTAMP,
    rejection_reason   VARCHAR(255),
    expiry_date        DATE,
    created_at         TIMESTAMP
);

-- ============================================================
-- Decision records
-- ============================================================
CREATE TABLE decision_records (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id            UUID         NOT NULL UNIQUE REFERENCES loan_applications(id) ON DELETE CASCADE,
    decision_type             VARCHAR(255) NOT NULL,
    outcome                   VARCHAR(255) NOT NULL,
    decision_date             TIMESTAMP    NOT NULL,
    decided_by                VARCHAR(255),
    delegated_authority_level VARCHAR(255),
    credit_bureau             VARCHAR(255),
    credit_score              INTEGER,
    credit_report_date        DATE,
    credit_report_reference   VARCHAR(255),
    max_approved_amount       NUMERIC(19, 4),
    approved_ltv              NUMERIC(10, 6),
    expiry_date               DATE
);

-- Decision conditions
CREATE TABLE decision_conditions (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    decision_record_id UUID         NOT NULL REFERENCES decision_records(id) ON DELETE CASCADE,
    condition_type     VARCHAR(255),
    description        VARCHAR(255) NOT NULL,
    status             VARCHAR(255) NOT NULL,
    satisfied_date     DATE,
    satisfied_by       VARCHAR(255)
);

-- Decision decline reasons (ElementCollection)
CREATE TABLE decision_decline_reasons (
    decision_record_id UUID NOT NULL REFERENCES decision_records(id) ON DELETE CASCADE,
    reason             VARCHAR(255)
);

-- Policy rule results
CREATE TABLE policy_rule_results (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    decision_record_id UUID         NOT NULL REFERENCES decision_records(id) ON DELETE CASCADE,
    rule_id            VARCHAR(255) NOT NULL,
    rule_name          VARCHAR(255) NOT NULL,
    result             VARCHAR(255) NOT NULL,
    detail             VARCHAR(255)
);

-- ============================================================
-- Offers
-- ============================================================
CREATE TABLE offers (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id              UUID         NOT NULL UNIQUE REFERENCES loan_applications(id) ON DELETE CASCADE,
    offer_status                VARCHAR(255) NOT NULL,
    offer_date                  DATE,
    expiry_date                 DATE,
    approved_amount             NUMERIC(19, 4),
    interest_rate               NUMERIC(10, 6),
    comparison_rate             NUMERIC(10, 6),
    rate_type                   VARCHAR(255),
    fixed_term_months           INTEGER,
    term_months                 INTEGER      NOT NULL,
    repayment_type              VARCHAR(255),
    estimated_monthly_repayment NUMERIC(19, 4),
    lmi_required                BOOLEAN      NOT NULL,
    lmi_premium                 NUMERIC(19, 4),
    lmi_capitalised             BOOLEAN,
    accepted_date               TIMESTAMP,
    accepted_by                 VARCHAR(255),
    acceptance_method           VARCHAR(255),
    cooling_off_expiry_date     DATE,
    created_at                  TIMESTAMP
);

-- ============================================================
-- Consent records
-- ============================================================
CREATE TABLE consent_records (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id  UUID         NOT NULL REFERENCES loan_applications(id) ON DELETE CASCADE,
    party_id        UUID,
    consent_type    VARCHAR(255) NOT NULL,
    granted         BOOLEAN      NOT NULL,
    granted_at      TIMESTAMP,
    expiry_date     DATE,
    revoked_at      TIMESTAMP,
    version         VARCHAR(255),
    capture_method  VARCHAR(255)
);

-- ============================================================
-- Workflow events
-- ============================================================
CREATE TABLE workflow_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id  UUID         NOT NULL REFERENCES loan_applications(id) ON DELETE CASCADE,
    event_type      VARCHAR(255) NOT NULL,
    occurred_at     TIMESTAMP    NOT NULL,
    actor_type      VARCHAR(255),
    actor_id        VARCHAR(255),
    actor_name      VARCHAR(255),
    previous_state  VARCHAR(255),
    new_state       VARCHAR(255),
    payload         TEXT,
    ip_address      VARCHAR(255),
    user_agent      VARCHAR(255),
    correlation_id  VARCHAR(255)
);

-- ============================================================
-- Broker details
-- ============================================================
CREATE TABLE broker_details (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id   UUID         NOT NULL UNIQUE REFERENCES loan_applications(id) ON DELETE CASCADE,
    broker_id        VARCHAR(255),
    broker_company   VARCHAR(255),
    aggregator_id    VARCHAR(255),
    aggregator_name  VARCHAR(255),
    broker_reference VARCHAR(255)
);
