# Technical Design: LIXI2 AU Lending Extension

**Author**: Atheryon Labs Architecture
**Date**: 15 April 2026
**Status**: DRAFT
**Scope**: End-to-end integration of LIXI2 standards into Atheryon's mortgage origination platform

---

## 1. Executive Summary

This document describes the architecture for integrating LIXI2 — Australia's industry data standard for lending — into Atheryon's existing mortgage origination platform. The design treats LIXI2 as an **I/O adapter layer**, not the core domain model. Our CDM-based state machine and Spring Boot domain entities remain the system of record; LIXI2 provides the external message format that Australian brokers, aggregators, and lenders expect.

**Core architectural principle**: LIXI2 is a message format. We are a platform. The gap between "message" and "platform" is where all the real engineering lives.

### What We're Building

| Capability | What It Does | LIXI2 Standard Used |
|-----------|-------------|---------------------|
| **LIXI Gateway** | Ingest/emit LIXI2 messages, validate, transform | CAL 2.6.x (JSON) |
| **Validation Pipeline** | 4-tier validation: schema → Schematron → EGB → domain | CAL schema + 1,600 rules |
| **Lender Configuration** | Per-lender field requirements, product rules, form specs | EGB 2.0.0 (augmented) |
| **Serviceability Adapter** | Exchange assessment requests/responses with providers | SVC 2.0.x |
| **Decision Adapter** | Exchange credit assessment requests/responses | CDA 2.0.x |
| **Event Backchannel** | Real-time notifications to brokers/aggregators | New (no LIXI2 standard) |
| **Document Packaging** | Settlement document exchange | DAS 2.2.x |
| **Migration Pipeline** | Bulk ingest, column auto-map, quality scoring, remediation, reconciliation | CAL schema + custom |
| **Developer SDKs** | TypeScript + Python SDKs with LIXI2 type safety | All standards |

### What We're NOT Building

- A generic LIXI2 platform (we're a mortgage origination engine that speaks LIXI2)
- An EGB "engine" that interprets the frozen 2016 spec as gospel
- A replacement for aggregator platforms (NextGen, Simpology)
- Credit bureau or valuation services (we integrate, not replace)

---

## 2. Architecture Principles

| # | Principle | Implication |
|---|-----------|-------------|
| **P1** | **CDM is canonical, LIXI2 is an adapter** | Internal entities (LoanApplication, Party, PropertySecurity) are the system of record. LIXI2 messages are translated at the boundary. |
| **P2** | **State machine owns lifecycle** | ApplicationStateMachine governs all transitions. LIXI2 status codes are derived from our state, not the other way around. |
| **P3** | **Validation is layered and composable** | Each layer (schema, Schematron, EGB, domain) can run independently. A LIXI2 message can be validated without entering the state machine. |
| **P4** | **Lender config over EGB spec** | EGB 2.0.0 is stale. Our configuration system can consume EGB data but doesn't depend on it. JSON lender profiles are the primary mechanism. |
| **P5** | **Modular monolith, not microservices** | Keep domain logic in one deployable (mortgages-platform). Extract services only when scaling demands it. |
| **P6** | **Event-sourced audit trail** | Every state transition, validation result, and external message is an immutable event. |
| **P7** | **Multi-standard ready** | The adapter pattern supports LIXI2 today, MISMO (US) or other standards tomorrow, without changing the core domain. |
| **P8** | **Honest about what's new build** | Don't dress greenfield engineering in "LIXI2 reuse" language. Be explicit about what's standard vs custom. |

---

## 3. System Context (C4 Level 1)

```
                        ┌─────────────────────────┐
                        │      Broker / CRM        │
                        │  (ApplyOnline, NextGen,  │
                        │   Simpology, custom)     │
                        └───────────┬─────────────┘
                                    │ LIXI2 CAL (JSON/XML)
                                    ▼
┌───────────────┐    ┌──────────────────────────────────┐    ┌──────────────────┐
│ Credit Bureau │◄──►│                                  │◄──►│ Valuation        │
│ (Equifax,     │    │     ATHERYON MORTGAGE PLATFORM   │    │ (CoreLogic,      │
│  illion)      │    │                                  │    │  PropTrack)      │
└───────────────┘    │  ┌─────────┐  ┌──────────────┐  │    └──────────────────┘
                     │  │  LIXI   │  │  Mortgage     │  │
┌───────────────┐    │  │ Gateway │──│  Engine       │  │    ┌──────────────────┐
│ LMI Provider  │◄──►│  └─────────┘  └──────────────┘  │◄──►│ Settlement       │
│ (Helia, QBE)  │    │  ┌─────────┐  ┌──────────────┐  │    │ (PEXA, Sympli)   │
└───────────────┘    │  │  Event  │  │  Lender      │  │    └──────────────────┘
                     │  │  Bus    │──│  Config      │  │
                     │  └─────────┘  └──────────────┘  │
                     └──────────┬───────────────────────┘
                                │ SSE / WebSocket / Webhook
                                ▼
                        ┌─────────────────────────┐
                        │   Lender Back-Office     │
                        │   Broker Dashboard       │
                        │   (labs-platform UI)     │
                        └─────────────────────────┘
```

### External System Inventory

| System | Protocol | Standard | Direction | Phase |
|--------|----------|----------|-----------|-------|
| Broker platforms | REST/JSON | LIXI2 CAL | Inbound | 1 |
| Credit bureaus | REST/JSON | LIXI2 CDA | Outbound | 2 |
| Serviceability providers | REST/JSON | LIXI2 SVC | Both | 2 |
| Valuation providers | REST/JSON | LIXI2 VAL | Both | 3 |
| LMI providers | REST/JSON | LIXI2 LMI | Both | 3 |
| Settlement platforms | REST/JSON | LIXI2 DAS | Outbound | 5 |
| Lender back-office | SSE/Webhook | Custom | Outbound | 4 |
| Land registry | REST | LIXI2 TSA (RFC) | Outbound | 5 |

---

## 4. Container Architecture (C4 Level 2)

### Current State

```
┌────────────────────────────────────────────────────────┐
│                    labs-platform                         │
│                  (Next.js 14, Vercel)                   │
│                                                         │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │  Mortgage    │  │  LIXI2       │  │  Explorer    │  │
│  │  Capture UI  │  │  Validator   │  │  (State Viz) │  │
│  └──────┬──────┘  └──────┬───────┘  └──────────────┘  │
│         │                │                              │
│         │     ┌──────────┴──────────┐                  │
│         │     │  API Routes         │                  │
│         │     │  /api/mortgages/*   │                  │
│         └─────┤  (proxy + local)    │                  │
│               └──────────┬──────────┘                  │
└──────────────────────────┼─────────────────────────────┘
                           │ HTTP (localhost:8080)
                           ▼
┌────────────────────────────────────────────────────────┐
│               mortgages-platform                        │
│            (Spring Boot 3.3, Java 17)                   │
│                                                         │
│  ┌────────────────┐  ┌──────────────┐  ┌───────────┐  │
│  │ REST API       │  │ State Machine│  │ Rules     │  │
│  │ Controllers    │──│ (strict DAG) │──│ Engine    │  │
│  └────────────────┘  └──────────────┘  └───────────┘  │
│  ┌────────────────┐  ┌──────────────┐  ┌───────────┐  │
│  │ JPA Entities   │  │ Repositories │  │ Services  │  │
│  │ (15+ entities) │──│ (Spring Data)│──│ Layer     │  │
│  └────────────────┘  └──────────────┘  └───────────┘  │
│                           │                             │
└───────────────────────────┼─────────────────────────────┘
                            │ JDBC
                            ▼
                   ┌─────────────────┐
                   │   PostgreSQL    │
                   │   (port 5432)   │
                   └─────────────────┘
```

### Target State

```
┌──────────────────────────────────────────────────────────────────┐
│                       labs-platform                               │
│                     (Next.js 14, Vercel)                         │
│                                                                   │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────────────────┐  │
│  │ Capture  │ │ Validate │ │ Explorer │ │ Broker Portal     │  │
│  │ UI       │ │ UI       │ │ (State)  │ │ (LIXI2 submit)   │  │
│  └────┬─────┘ └────┬─────┘ └──────────┘ └─────────┬─────────┘  │
│       │             │                               │            │
│       │     ┌───────┴────────────┐     ┌───────────┴──────────┐ │
│       │     │ API Routes         │     │ Notification Panel   │ │
│       └─────┤ /api/mortgages/*   │     │ (SSE client)         │ │
│             └────────┬───────────┘     └───────────┬──────────┘ │
└──────────────────────┼─────────────────────────────┼────────────┘
                       │ HTTP                        │ SSE
                       ▼                             │
┌──────────────────────────────────────────────────────────────────┐
│                    mortgages-platform                              │
│                 (Spring Boot 3.3, Java 17)                       │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │                      LIXI MODULE                            │ │
│  │  ┌──────────┐  ┌──────────┐  ┌────────┐  ┌─────────────┐  │ │
│  │  │ CAL      │  │ SVC      │  │ CDA    │  │ DAS         │  │ │
│  │  │ Adapter  │  │ Adapter  │  │ Adapter│  │ Adapter     │  │ │
│  │  └────┬─────┘  └────┬─────┘  └───┬────┘  └──────┬──────┘  │ │
│  │       │              │            │               │         │ │
│  │  ┌────┴──────────────┴────────────┴───────────────┴──────┐  │ │
│  │  │              LIXI2 Common                             │  │ │
│  │  │  Schema Loader │ Schematron Engine │ JSON ↔ XML       │  │ │
│  │  └───────────────────────────────────────────────────────┘  │ │
│  └───────────────────────────┬─────────────────────────────────┘ │
│                              │ Domain Commands / Queries         │
│  ┌───────────────────────────┴─────────────────────────────────┐ │
│  │                       CORE MODULE                           │ │
│  │  ┌──────────┐  ┌──────────────┐  ┌────────────────────┐   │ │
│  │  │ Entities │  │ State Machine│  │ Rules Engine       │   │ │
│  │  │ (JPA)    │──│ (strict DAG) │──│ SVC │ LTV │ Decisn │   │ │
│  │  └──────────┘  └──────────────┘  └────────────────────┘   │ │
│  │  ┌──────────┐  ┌──────────────┐  ┌────────────────────┐   │ │
│  │  │ Services │  │ Repositories │  │ Event Store        │   │ │
│  │  │ Layer    │──│ (Spring Data)│──│ (write-ahead log)  │   │ │
│  │  └──────────┘  └──────────────┘  └────────────────────┘   │ │
│  └─────────────────────────────────────────────────────────────┘ │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │                  INTEGRATION MODULE                         │ │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌───────────┐  │ │
│  │  │ Credit   │  │ Valuation│  │ LMI      │  │ Settlement│  │ │
│  │  │ Bureau   │  │ Provider │  │ Provider │  │ (PEXA)    │  │ │
│  │  └──────────┘  └──────────┘  └──────────┘  └───────────┘  │ │
│  └─────────────────────────────────────────────────────────────┘ │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │                  NOTIFICATION MODULE                        │ │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌───────────┐  │ │
│  │  │ Event    │  │ SSE      │  │ Webhook  │  │ LIXI2     │  │ │
│  │  │ Bus      │──│ Endpoint │  │ Dispatch │  │ Status Msg│  │ │
│  │  └──────────┘  └──────────┘  └──────────┘  └───────────┘  │ │
│  └─────────────────────────────────────────────────────────────┘ │
│                                                                   │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │                  CONFIG MODULE                              │ │
│  │  ┌──────────────┐  ┌───────────────┐  ┌─────────────────┐  │ │
│  │  │ Lender       │  │ Product       │  │ EGB Import      │  │ │
│  │  │ Profiles     │  │ Catalogue     │  │ (legacy compat) │  │ │
│  │  └──────────────┘  └───────────────┘  └─────────────────┘  │ │
│  └─────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
                            │ JDBC + Redis
                ┌───────────┴───────────┐
                ▼                       ▼
       ┌─────────────────┐     ┌─────────────┐
       │   PostgreSQL    │     │   Redis     │
       │   (port 5432)   │     │ (port 6379) │
       │   • Domain data │     │ • SSE fanout│
       │   • Event store │     │ • Cache     │
       │   • Lender cfg  │     │ • Sessions  │
       └─────────────────┘     └─────────────┘
```

### Module Boundaries

| Module | Knows About | Doesn't Know About |
|--------|------------|-------------------|
| **LIXI** | LIXI2 schemas, message formats, Schematron rules | Domain entities, state machine, database |
| **Core** | Domain entities, state machine, business rules | LIXI2, external providers, notification channels |
| **Integration** | External API contracts, HTTP clients | Domain internals, LIXI2 formats |
| **Notification** | Domain events, delivery channels | How events were produced, LIXI2 specifics |
| **Config** | Lender profiles, product rules | Domain state, message formats |

Communication between modules uses **domain commands and events** (Java interfaces), not direct method calls. This enables future extraction to services without changing interfaces.

---

## 5. Domain Model & LIXI2 Mapping

### Internal Canonical Model (Existing)

```
LoanApplication (aggregate root)
├── applicationNumber: String (ATH-2026-XXXXXX)
├── status: ApplicationStatus (14-state enum)
├── parties: List<Party>
│   ├── role: PartyRole (APPLICANT, CO_APPLICANT, GUARANTOR)
│   ├── type: PartyType (INDIVIDUAL, COMPANY, TRUST, SMSF)
│   ├── employment: List<Employment>
│   ├── addresses: List<Address>
│   └── identifications: List<Identification>
├── securities: List<PropertySecurity>
│   ├── property details (address, type, bedrooms)
│   ├── valuation: Valuation
│   └── lmiQuote: LmiQuote
├── financialSnapshot: FinancialSnapshot
│   ├── incomeItems: List<IncomeItem>
│   ├── liabilities: List<Liability>
│   └── computed: {grossIncome, netIncome, umi, dsr}
├── decisions: List<DecisionRecord>
├── offers: List<Offer>
├── documents: List<Document>
└── workflowEvents: List<WorkflowEvent>
```

### LIXI2 CAL → Internal Model Mapping

This is the most critical engineering in the system. 700 LIXI2 elements must map to our ~15 entities.

```java
// Mapping strategy: explicit, tested Java mappers
// NOT generic configuration-driven transformation
// Reason: core mapping is too critical for magic — explicit code wins

public interface LixiMapper<L, D> {
    D toDomain(L lixiMessage, MappingContext ctx);
    L toLixi(D domainEntity, MappingContext ctx);
}
```

#### Primary Mappings

| LIXI2 CAL Path | Internal Entity.Field | Transform |
|----------------|----------------------|-----------|
| `Package/Content/Application` | `LoanApplication` | 1:1, aggregate root |
| `Application/@UniqueID` | `.externalId` | Store as-is, generate internal applicationNumber |
| `Application/Overview/LoanPurpose` | `.loanPurpose` | Enum map (LIXI2 → internal) |
| `Application/PersonApplicant` | `Party` (type=INDIVIDUAL) | Per-applicant mapping |
| `PersonApplicant/@PrimaryApplicant` | `Party.role` = APPLICANT | Boolean flag → role enum |
| `PersonApplicant/PersonName` | `Party.{firstName,middleName,surname}` | Direct |
| `PersonApplicant/DateOfBirth` | `Party.dateOfBirth` | ISO date parse |
| `PersonApplicant/Employment[]` | `Party.employment[]` | List, most-recent-first |
| `Employment/PAYG` | `Employment.{employer,role,startDate,income}` | Type-specific unwrap |
| `Employment/SelfEmployed` | Same, different income structure | ABN, years trading |
| `PersonApplicant/Income/NonEmployment` | `IncomeItem` (RENTAL, INVESTMENT, etc.) | Map LIXI2 type → our IncomeType |
| `PersonApplicant/Address[]` | `Party.addresses[]` | LIXI2 structured → flat |
| `PersonApplicant/Privacy/ConsentObtained` | `Party.kycConsent` | Boolean |
| `Application/RealEstateAsset` | `PropertySecurity` | 1:1 per security |
| `RealEstateAsset/ContractPrice/Amount` | `.purchasePrice` | Currency decimal |
| `RealEstateAsset/Address` | `.{streetNumber,street,suburb,state,postcode}` | Structured address |
| `RealEstateAsset/Valuation` | `Valuation` | If provided |
| `Application/LoanDetails/LoanAmount` | `LoanApplication.requestedAmount` | Currency decimal |
| `Application/LoanDetails/Term` | `LoanApplication.loanTermMonths` | Convert years→months if needed |
| `Application/LoanDetails/Rate` | Product lookup | Map to internal product |
| `Application/CompanyApplicant` | `Party` (type=COMPANY) | ABN, ACN, directors |
| `Application/TrustApplicant` | `Party` (type=TRUST) | Deed, trustees, type |

#### Edge Cases & Design Decisions

| Issue | Decision | Rationale |
|-------|----------|-----------|
| LIXI2 has ~20 income types, we have ~12 | Map all 20 → our 12, with `OTHER` fallback | Don't lose data; extend our enum later if needed |
| LIXI2 addresses have 5 formats (structured, unstructured, international, PO Box, overseas) | Normalize to structured; store original in `rawAddress` JSONB column | Our pipeline needs structured; keep original for round-trip |
| LIXI2 `Application/@UniqueID` format varies by aggregator | Store in `externalId`, generate our own `applicationNumber` | Our state machine needs a stable ID |
| LIXI2 supports multiple facilities per application | Map to `List<Facility>` on LoanApplication, primary facility drives assessment | Most AU residential is single facility; multi-facility is future |
| LIXI2 `PersonApplicant/TaxDeclaration/TFN` | **DO NOT STORE** in clear text. Hash or encrypt at rest. | TFN is sensitive PII under AU Privacy Act |
| Round-trip fidelity | Store original LIXI2 message in `lixi_messages` table (JSONB) | Enables exact re-emission, audit, and debugging |

### New Database Tables

```sql
-- Store original LIXI2 messages for audit + round-trip
CREATE TABLE lixi_messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id  UUID REFERENCES loan_applications(id),
    direction       VARCHAR(10) NOT NULL,  -- 'INBOUND' | 'OUTBOUND'
    standard        VARCHAR(10) NOT NULL,  -- 'CAL' | 'SVC' | 'CDA' | 'DAS'
    version         VARCHAR(20) NOT NULL,  -- 'CAL-2.6.91'
    format          VARCHAR(4)  NOT NULL,  -- 'JSON' | 'XML'
    payload         JSONB       NOT NULL,  -- full message
    validation_result JSONB,               -- tier results
    sender_id       VARCHAR(100),          -- broker/aggregator ID
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Event store (append-only)
CREATE TABLE domain_events (
    id              BIGSERIAL PRIMARY KEY,
    aggregate_id    UUID NOT NULL,         -- application ID
    aggregate_type  VARCHAR(50) NOT NULL,  -- 'LoanApplication'
    event_type      VARCHAR(100) NOT NULL, -- 'ApplicationCreated', 'StateTransitioned'
    event_data      JSONB NOT NULL,
    metadata        JSONB,                 -- actor, source, correlation ID
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_events_aggregate ON domain_events(aggregate_id, created_at);

-- Lender configuration profiles
CREATE TABLE lender_profiles (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lender_code     VARCHAR(50) UNIQUE NOT NULL,
    lender_name     VARCHAR(200) NOT NULL,
    required_fields JSONB NOT NULL DEFAULT '[]',
    validation_rules JSONB NOT NULL DEFAULT '[]',
    product_rules   JSONB NOT NULL DEFAULT '[]',
    form_layout     JSONB,                 -- optional EGB-derived
    submission_config JSONB,               -- endpoint, auth, format
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Webhook subscriptions
CREATE TABLE webhook_subscriptions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscriber_id   VARCHAR(100) NOT NULL,  -- broker/lender ID
    application_id  UUID,                    -- NULL = all applications
    endpoint_url    VARCHAR(500) NOT NULL,
    events          JSONB NOT NULL,          -- ['STATE_CHANGED', 'DOCUMENT_READY']
    secret          VARCHAR(200) NOT NULL,   -- HMAC signing key
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

---

## 6. Validation Pipeline

The most reusable part of LIXI2. Four tiers, composable, independently runnable.

```
                    ┌────────────────────────────┐
                    │     LIXI2 Message (JSON)    │
                    └─────────────┬──────────────┘
                                  │
                    ┌─────────────▼──────────────┐
                    │  TIER 1: Schema Validation  │
                    │  (networknt json-schema-    │
                    │   validator, Draft 7)       │
                    │                             │
                    │  Input: raw JSON            │
                    │  Checks: types, required,   │
                    │    enums, formats, $ref      │
                    │  Output: structural errors   │
                    │  Cost: <10ms                 │
                    └─────────────┬──────────────┘
                                  │ pass
                    ┌─────────────▼──────────────┐
                    │  TIER 2: Schematron Rules   │
                    │  (ph-schematron or Saxon)   │
                    │                             │
                    │  Input: validated JSON/XML  │
                    │  Checks: cross-field deps,  │
                    │    mandatory combos,         │
                    │    deprecation warnings      │
                    │  Source: LIXI's 1,600 rules  │
                    │  Output: business warnings   │
                    │  Cost: 50-200ms              │
                    └─────────────┬──────────────┘
                                  │ pass/warn
                    ┌─────────────▼──────────────┐
                    │  TIER 3: Lender Rules       │
                    │  (dynamic, per lender)      │
                    │                             │
                    │  Input: validated message   │
                    │  Checks: lender-specific    │
                    │    field requirements,       │
                    │    product eligibility       │
                    │  Source: lender_profiles     │
                    │  Output: field-level warns   │
                    │  Cost: <10ms                 │
                    └─────────────┬──────────────┘
                                  │ pass/warn
                    ┌─────────────▼──────────────┐
                    │  TIER 4: Domain Rules       │
                    │  (Spring Boot, our logic)   │
                    │                             │
                    │  Input: domain entities     │
                    │  Checks: MR-001..MR-010,    │
                    │    LTV bands, SVC limits,   │
                    │    state transition validity │
                    │  Output: business errors     │
                    │  Cost: <50ms                 │
                    └─────────────┬──────────────┘
                                  │
                    ┌─────────────▼──────────────┐
                    │   ValidationResult          │
                    │   {                         │
                    │     valid: boolean,          │
                    │     tier1: SchemaResult,     │
                    │     tier2: SchematronResult, │
                    │     tier3: LenderResult,     │
                    │     tier4: DomainResult,     │
                    │     fieldCoverage: 87%,      │
                    │     errors: [...],           │
                    │     warnings: [...]          │
                    │   }                         │
                    └────────────────────────────┘
```

### Implementation: Tier 1 (Schema)

```java
// Use networknt/json-schema-validator — fastest JVM JSON Schema Draft 7 impl
// Schema loaded once at startup, cached per standard version

@Service
public class LixiSchemaValidator {

    private final Map<String, JsonSchema> schemaCache = new ConcurrentHashMap<>();

    public SchemaValidationResult validate(JsonNode message, String standard, String version) {
        JsonSchema schema = schemaCache.computeIfAbsent(
            standard + ":" + version,
            k -> loadSchema(standard, version)
        );
        Set<ValidationMessage> errors = schema.validate(message);
        return new SchemaValidationResult(errors.isEmpty(), errors);
    }
}
```

### Implementation: Tier 2 (Schematron)

```java
// Two options:
// A) ph-schematron (pure Java, supports Schematron ISO)
// B) Saxon + XSLT-compiled Schematron (faster at scale)
//
// Decision: ph-schematron for simplicity; switch to Saxon if > 100 msg/sec

@Service
public class LixiSchematronValidator {

    private final SchematronResourcePure schematronResource;

    @PostConstruct
    void init() {
        // Load LIXI's Schematron file (bundled in resources/lixi/rules/)
        this.schematronResource = SchematronResourcePure.fromClassPath(
            "lixi/rules/cal-mandatory-rules.sch"
        );
    }

    public SchematronValidationResult validate(Document xmlDoc) {
        SchematronOutputType result = schematronResource.applySchematronValidationToSVRL(xmlDoc);
        // Parse SVRL output for failed-assert and successful-report elements
        return mapToResult(result);
    }
}
```

**Note**: LIXI's Schematron rules expect XML input. For JSON messages, we convert JSON → XML using LIXI2's conversion conventions before running Schematron, then discard the XML. This is a known LIXI2 pattern.

### Implementation: Tier 3 (Lender Rules)

```java
// Dynamic rules loaded from lender_profiles table
// Evaluated against the LIXI2 message (pre-domain-mapping)

@Service
public class LenderRuleValidator {

    private final LenderProfileRepository profiles;

    public LenderValidationResult validate(JsonNode message, String lenderCode) {
        LenderProfile profile = profiles.findByLenderCode(lenderCode)
            .orElseReturn(LenderValidationResult.skip());

        List<FieldViolation> violations = new ArrayList<>();
        for (FieldRequirement req : profile.getRequiredFields()) {
            JsonNode value = message.at(req.getJsonPointer());
            if (req.isRequired() && (value == null || value.isMissingNode())) {
                violations.add(new FieldViolation(req.getPath(), "Required by " + lenderCode));
            }
            // Conditional requirements
            if (req.getCondition() != null && evaluateCondition(req.getCondition(), message)) {
                if (value == null || value.isMissingNode()) {
                    violations.add(new FieldViolation(req.getPath(), "Conditionally required"));
                }
            }
        }
        return new LenderValidationResult(violations);
    }
}
```

### Validation as a Standalone Service

Tiers 1–3 can run **without creating an application** in our state machine. This enables:
- A public validation endpoint for brokers to check messages before submitting
- Batch validation for data migration projects
- CI/CD pipeline validation for aggregator integrations

```
POST /api/v1/lixi/validate
Content-Type: application/json

{
  "message": { ... LIXI2 CAL JSON ... },
  "standard": "CAL",
  "version": "2.6.91",
  "lenderCode": "WBC",         // optional: enable Tier 3
  "tiers": [1, 2, 3]           // optional: select tiers
}

Response:
{
  "valid": false,
  "tiers": {
    "schema":     { "valid": true,  "errors": [] },
    "schematron": { "valid": true,  "warnings": [...] },
    "lender":     { "valid": false, "violations": [...] }
  },
  "fieldCoverage": 0.87,
  "summary": "2 lender-required fields missing"
}
```

---

## 7. LIXI Gateway (Ingest & Emit)

### Ingest Flow

```
POST /api/v1/lixi/ingest
Content-Type: application/json
X-Lixi-Standard: CAL
X-Lixi-Version: 2.6.91
X-Sender-Id: broker-nextgen-001

{ ... LIXI2 CAL message ... }
```

Processing pipeline:

```
1. RECEIVE
   │  Parse JSON, extract headers
   │  Store raw message in lixi_messages (INBOUND)
   │
2. VALIDATE (Tiers 1-3)
   │  Schema → Schematron → Lender rules
   │  If invalid: return 422 with validation result
   │  Store validation result
   │
3. TRANSFORM
   │  LixiCalMapper.toDomain(message, context)
   │  Create LoanApplication + Party + PropertySecurity + FinancialSnapshot
   │  Validate domain rules (Tier 4)
   │
4. PERSIST
   │  Save domain entities via services
   │  Initial state: DRAFT
   │  Record WorkflowEvent: LIXI_INGEST
   │
5. RESPOND
   │  Return application ID + validation summary
   │  Publish ApplicationCreated event
   │
6. NOTIFY
      Fire webhook/SSE to subscribed listeners
```

```java
@RestController
@RequestMapping("/api/v1/lixi")
public class LixiGatewayController {

    @PostMapping("/ingest")
    public ResponseEntity<IngestResult> ingest(
            @RequestBody JsonNode lixiMessage,
            @RequestHeader("X-Lixi-Standard") String standard,
            @RequestHeader("X-Lixi-Version") String version,
            @RequestHeader(value = "X-Sender-Id", required = false) String senderId) {

        // 1. Store raw
        LixiMessage stored = lixiMessageService.store(lixiMessage, INBOUND, standard, version, senderId);

        // 2. Validate tiers 1-3
        ValidationResult validation = validationPipeline.validate(lixiMessage, standard, version);
        if (!validation.isValid()) {
            return ResponseEntity.unprocessableEntity().body(IngestResult.invalid(validation));
        }

        // 3. Transform to domain
        LoanApplication app = lixiCalMapper.toDomain(lixiMessage, MappingContext.of(senderId));

        // 4. Tier 4 domain validation
        DomainValidationResult domainResult = domainValidator.validate(app);
        if (domainResult.hasErrors()) {
            return ResponseEntity.unprocessableEntity().body(IngestResult.domainErrors(domainResult));
        }

        // 5. Persist
        LoanApplication saved = applicationService.create(app);
        lixiMessageService.linkToApplication(stored.getId(), saved.getId());

        // 6. Publish event
        eventPublisher.publish(new ApplicationCreated(saved.getId(), senderId));

        return ResponseEntity.status(201).body(IngestResult.success(saved.getId(), validation));
    }
}
```

### Emit Flow (Application → LIXI2)

```java
@GetMapping("/emit/{applicationId}")
public ResponseEntity<JsonNode> emit(
        @PathVariable UUID applicationId,
        @RequestParam(defaultValue = "CAL") String standard,
        @RequestParam(defaultValue = "2.6.91") String version,
        @RequestParam(defaultValue = "JSON") String format) {

    LoanApplication app = applicationService.getWithDetails(applicationId);
    JsonNode lixiMessage = lixiCalMapper.toLixi(app, EmitContext.of(standard, version));

    // Store outbound message
    lixiMessageService.store(lixiMessage, OUTBOUND, standard, version, null);

    if ("XML".equalsIgnoreCase(format)) {
        String xml = lixiJsonXmlConverter.toXml(lixiMessage);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_XML)
            .body(new TextNode(xml));
    }

    return ResponseEntity.ok(lixiMessage);
}
```

---

## 8. Serviceability Integration (SVC Adapter)

### Architecture

```
                   ┌─────────────────────────────────────┐
                   │          SVC Adapter                  │
                   │                                       │
  Trigger:         │  ┌───────────────────────────────┐   │
  - API call       │  │   Route Decision              │   │
  - State machine  │  │   (based on lender config)    │   │
  - LIXI2 SVC msg  │  └─────────┬──────────┬──────────┘   │
                   │            │          │               │
                   │   ┌────────▼───┐  ┌───▼────────────┐ │
                   │   │  Internal  │  │  External      │ │
                   │   │  Calculator│  │  Provider      │ │
                   │   │            │  │                │ │
                   │   │  HEM table │  │  LIXI2 SVC    │ │
                   │   │  APRA buf  │  │  passthrough   │ │
                   │   │  Shading   │  │  to provider   │ │
                   │   └────────┬───┘  └───┬────────────┘ │
                   │            │          │               │
                   │   ┌────────▼──────────▼────────────┐ │
                   │   │  Result Normalizer             │ │
                   │   │  → ServiceabilityResult        │ │
                   │   │  → Update FinancialSnapshot    │ │
                   │   │  → Record WorkflowEvent        │ │
                   │   └────────────────────────────────┘ │
                   └─────────────────────────────────────┘
```

### Internal Calculator (Existing — Enhanced)

Our `ServiceabilityCalculator` already handles the core math. Enhancements:

```java
@Service
public class EnhancedServiceabilityCalculator {

    private final HemLookupService hemLookup;  // NEW: ABS HEM table

    public ServiceabilityResult assess(FinancialSnapshot snapshot, LoanFacility facility) {

        // Assessment rate = max(product rate + 3%, floor of 5.50%)
        BigDecimal assessmentRate = facility.getRate()
            .add(new BigDecimal("3.00"))
            .max(new BigDecimal("5.50"));

        // Income shading (existing logic)
        BigDecimal netMonthlyIncome = calculateShadedIncome(snapshot.getIncomeItems());

        // Expenses: max(declared, HEM benchmark)
        BigDecimal hemBenchmark = hemLookup.lookup(
            snapshot.getHouseholdSize(),
            snapshot.getPostcode(),
            snapshot.getDependents()
        );
        BigDecimal assessedExpenses = snapshot.getDeclaredExpenses().max(hemBenchmark);

        // Existing commitments
        BigDecimal existingCommitments = calculateCommitments(snapshot.getLiabilities());

        // Proposed repayment at assessment rate
        BigDecimal proposedRepayment = calculateRepayment(
            facility.getAmount(), assessmentRate, facility.getTermMonths()
        );

        // Results
        BigDecimal ndi = netMonthlyIncome.subtract(assessedExpenses);
        BigDecimal umi = ndi.subtract(existingCommitments).subtract(proposedRepayment);
        BigDecimal dsr = existingCommitments.add(proposedRepayment)
            .divide(snapshot.getGrossMonthlyIncome(), 4, RoundingMode.HALF_UP);

        ServiceabilityOutcome outcome = determineOutcome(umi, dsr);

        return ServiceabilityResult.builder()
            .ndi(ndi)
            .umi(umi)
            .dsr(dsr)
            .assessmentRate(assessmentRate)
            .hemApplied(hemBenchmark.compareTo(snapshot.getDeclaredExpenses()) > 0)
            .hemBenchmark(hemBenchmark)
            .outcome(outcome)
            // NEW: household-level breakdown for joint applications
            .householdResults(calculateHouseholdBreakdown(snapshot))
            .build();
    }
}
```

### External Provider Passthrough

When a lender requires an external SVC provider (e.g., for LMI-backed assessment):

```java
@Service
public class ExternalSvcProvider {

    private final LixiSvcMapper svcMapper;
    private final WebClient webClient;

    public ServiceabilityResult assessExternal(
            LoanApplication app, LenderProfile lender) {

        // 1. Build LIXI2 SVC request from our domain model
        JsonNode svcRequest = svcMapper.toSvcRequest(app);

        // 2. Send to external provider (configured per lender)
        JsonNode svcResponse = webClient.post()
            .uri(lender.getSvcEndpoint())
            .header("Authorization", "Bearer " + lender.getSvcApiKey())
            .bodyValue(svcRequest)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .block(Duration.ofSeconds(30));

        // 3. Map response back to our domain
        return svcMapper.fromSvcResponse(svcResponse);
    }
}
```

---

## 9. Credit Decisioning Integration (CDA Adapter)

### Flow

```
State Machine: UNDER_ASSESSMENT
        │
        ▼
┌─────────────────────────────┐
│  CDA Adapter                │
│                              │
│  1. Build CDA request from   │
│     application data         │
│                              │
│  2. Route to:                │
│     a) Internal engine       │
│        (DecisionEngine)      │
│     b) External provider     │
│        (Equifax, illion)     │
│     c) Hybrid (both, merge)  │
│                              │
│  3. Merge results:           │
│     - Credit score           │
│     - KYC/VOI result         │
│     - Fraud check            │
│     - Policy assessment      │
│                              │
│  4. Feed into DecisionEngine │
│     for final outcome        │
└──────────────┬──────────────┘
               │
               ▼
    DecisionRecord created
    State → DECISIONED
```

### CDA Request/Response Mapping

| CDA Element | Source |
|------------|--------|
| `Applicant/PersonName` | Party entity |
| `Applicant/DateOfBirth` | Party entity |
| `Applicant/Address/Current` | Party.addresses[0] |
| `Applicant/Employment/Current` | Party.employment[0] |
| `Applicant/IdentityDocument[]` | Party.identifications |
| `LoanDetails/Amount` | LoanApplication.requestedAmount |
| `LoanDetails/Purpose` | LoanApplication.loanPurpose |
| `AssessmentInstructions` | Lender config → check types |

| CDA Response Element | Maps To |
|---------------------|---------|
| `Decision/Score` | DecisionRecord.creditScore |
| `Decision/ScoreBand` | DecisionRecord.scoreBand |
| `IdentityCheck/Result` | Party.kycStatus |
| `IdentityCheck/Type` (KYC/VOI) | Party.kycCheckType |
| `FraudDetection/Score` | DecisionRecord.fraudScore |
| `FraudDetection/ReferenceId` | DecisionRecord.fraudCheckRef |

### Credit Bureau Integration

```java
public interface CreditBureauClient {
    CreditCheckResult checkCredit(CreditCheckRequest request);
    IdentityVerificationResult verifyIdentity(IdentityCheckRequest request);
    FraudCheckResult checkFraud(FraudCheckRequest request);
}

// Equifax implementation (AU)
@Service
@ConditionalOnProperty("integration.credit.provider", havingValue = "equifax")
public class EquifaxClient implements CreditBureauClient {

    // Uses Equifax Commercial API
    // Maps to/from LIXI2 CDA format internally
    // Caches results (credit scores valid for 30 days per AU regulations)
}

// Stub for development
@Service
@Profile("dev")
public class StubCreditBureauClient implements CreditBureauClient {
    // Returns configurable test scores
    // Default: credit score 720, KYC pass, fraud clear
}
```

---

## 10. Event Architecture & Backchannel

### Event Model

```java
// All domain events extend this base
public sealed interface DomainEvent permits
    ApplicationCreated,
    StateTransitioned,
    ServiceabilityAssessed,
    DecisionMade,
    OfferIssued,
    OfferAccepted,
    DocumentUploaded,
    DocumentVerified,
    SettlementInitiated,
    SettlementCompleted,
    LixiMessageReceived,
    LixiMessageSent {

    UUID aggregateId();
    String eventType();
    Instant occurredAt();
    Map<String, Object> metadata();
}

// Example
public record StateTransitioned(
    UUID aggregateId,
    ApplicationStatus fromState,
    ApplicationStatus toState,
    String trigger,          // "LIXI_INGEST", "USER_ACTION", "AUTO_DECISION"
    String actor,            // user ID or system
    Instant occurredAt,
    Map<String, Object> metadata
) implements DomainEvent {
    public String eventType() { return "STATE_TRANSITIONED"; }
}
```

### Event Store

```java
@Service
public class EventStore {

    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher springEvents;

    @Transactional
    public void append(DomainEvent event) {
        // 1. Persist to event table (append-only)
        jdbc.update("""
            INSERT INTO domain_events (aggregate_id, aggregate_type, event_type, event_data, metadata)
            VALUES (?, 'LoanApplication', ?, ?::jsonb, ?::jsonb)
            """,
            event.aggregateId(),
            event.eventType(),
            objectMapper.writeValueAsString(event),
            objectMapper.writeValueAsString(event.metadata())
        );

        // 2. Publish to Spring's internal event bus (for same-JVM listeners)
        springEvents.publishEvent(event);
    }

    public List<DomainEvent> getEvents(UUID aggregateId) {
        return jdbc.query(
            "SELECT * FROM domain_events WHERE aggregate_id = ? ORDER BY created_at",
            eventRowMapper, aggregateId
        );
    }
}
```

### Notification Channels

#### SSE (Server-Sent Events) — Broker Dashboard

```java
@RestController
@RequestMapping("/api/v1/events")
public class EventStreamController {

    private final SseEmitterStore emitters;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @RequestParam(required = false) UUID applicationId,
            @RequestParam(required = false) String subscriberId) {

        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.register(emitter, applicationId, subscriberId);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));

        return emitter;
    }

    // Internal listener — fans out domain events to SSE clients
    @EventListener
    public void onDomainEvent(DomainEvent event) {
        NotificationPayload payload = notificationMapper.toPayload(event);
        emitters.sendToSubscribers(event.aggregateId(), payload);
    }
}
```

#### Webhooks — System-to-System

```java
@Service
public class WebhookDispatcher {

    private final WebhookSubscriptionRepository subscriptions;
    private final WebClient webClient;
    private final HmacSigner signer;

    @Async
    @EventListener
    public void onDomainEvent(DomainEvent event) {
        List<WebhookSubscription> subs = subscriptions.findActive(event.aggregateId(), event.eventType());

        for (WebhookSubscription sub : subs) {
            String payload = objectMapper.writeValueAsString(event);
            String signature = signer.sign(payload, sub.getSecret());

            webClient.post()
                .uri(sub.getEndpointUrl())
                .header("X-Atheryon-Signature", signature)
                .header("X-Event-Type", event.eventType())
                .bodyValue(payload)
                .retrieve()
                .toBodilessEntity()
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
                .subscribe();
        }
    }
}
```

#### LIXI2 Status Messages — Aggregator Callback

When a state transition happens, we can optionally emit a LIXI2 CAL status message back to the originating aggregator:

```java
@EventListener
public void onStateTransitioned(StateTransitioned event) {
    LixiMessage originalInbound = lixiMessageService.findInbound(event.aggregateId());
    if (originalInbound == null) return;  // Not a LIXI-originated application

    // Build LIXI2 status response
    JsonNode statusMessage = lixiCalMapper.toStatusUpdate(
        event.aggregateId(),
        event.toState(),
        originalInbound
    );

    // Store + send
    lixiMessageService.store(statusMessage, OUTBOUND, "CAL", originalInbound.getVersion(), null);
    aggregatorCallbackService.send(originalInbound.getSenderId(), statusMessage);
}
```

---

## 11. Lender Configuration (EGB Successor)

### Why Not Just Use EGB?

EGB 2.0.0 (November 2016) defines a data structure for lender requirements. But:
- Last updated 10 years ago — many fields reference deprecated CAL elements
- No JSON equivalent (XML-only in LIXI1 era)
- No active LIXI working group maintaining it
- No known production EGB implementations outside of legacy aggregator platforms

### Our Approach: Pragmatic Configuration Store

Support EGB import as one input format, but don't depend on it.

```java
// Lender Profile — the configuration unit
public class LenderProfile {

    private String lenderCode;          // "WBC", "CBA", "ANZ"
    private String lenderName;
    private boolean active;

    // Field requirements (was EGB's "data mapping")
    private List<FieldRequirement> requiredFields;

    // Product-level rules (was EGB's "product rules")
    private List<ProductRule> productRules;

    // Validation rules beyond standard Schematron
    private List<CustomValidationRule> validationRules;

    // Optional: form layout hints (was EGB's "forms")
    private FormLayout formLayout;

    // Submission configuration
    private SubmissionConfig submissionConfig;
}

public class FieldRequirement {
    private String lixiPath;          // "/Application/PersonApplicant/Income/PAYG"
    private String cdmPath;           // "borrower.income[type=PAYG]"
    private boolean required;
    private String condition;          // SpEL: "#loanAmount > 500000"
    private String validationRegex;
    private String description;        // "Required for PAYG employees"
}

public class ProductRule {
    private String productCode;
    private BigDecimal maxLvr;
    private BigDecimal minLoanAmount;
    private BigDecimal maxLoanAmount;
    private Integer maxTermMonths;
    private List<String> allowedPurposes;
    private List<String> allowedPropertyTypes;
    private BigDecimal lmiThresholdLvr;
}
```

### EGB Import (Compatibility)

```java
@Service
public class EgbImporter {

    // Parse EGB 2.0.0 XML into our LenderProfile format
    public LenderProfile importEgb(InputStream egbXml) {
        Document doc = parseXml(egbXml);

        LenderProfile profile = new LenderProfile();
        profile.setRequiredFields(extractFieldRequirements(doc));
        profile.setProductRules(extractProductRules(doc));
        profile.setFormLayout(extractFormLayout(doc));

        return profile;
    }
}
```

### Lender Config API

```
GET    /api/v1/config/lenders                    List all lender profiles
GET    /api/v1/config/lenders/{code}             Get profile
POST   /api/v1/config/lenders                    Create profile
PUT    /api/v1/config/lenders/{code}             Update profile
POST   /api/v1/config/lenders/import-egb         Import from EGB XML
GET    /api/v1/config/lenders/{code}/validate    Validate a message against this lender's rules
```

---

## 12. Document Packaging (DAS Integration)

### Document Lifecycle

```
1. COLLECT    — Gather required documents from application
                (ID, payslips, valuations, contracts)

2. GENERATE   — Create documents from templates
                (offer letter, loan contract, disclosure)

3. VALIDATE   — Check completeness against lender checklist
                (all required docs present and verified)

4. PACKAGE    — Bundle into DAS 2.2.x format
                (settlement instruction set)

5. SIGN       — Route to e-signing platform
                (DocuSign, Adobe Sign)

6. SUBMIT     — Send to settlement platform
                (PEXA for electronic conveyancing)

7. ARCHIVE    — Store signed documents with audit trail
                (immutable, tamper-evident)
```

### DAS Adapter

```java
@Service
public class DasAdapter {

    private final LixiDasMapper dasMapper;

    // Build a DAS settlement instruction from our domain
    public JsonNode buildSettlementInstruction(LoanApplication app) {
        return dasMapper.toSettlementInstruction(
            app,
            app.getOffer(),
            app.getSecurities(),
            app.getParties()
        );
    }

    // Parse an incoming DAS message (e.g., from conveyancer)
    public SettlementUpdate fromDas(JsonNode dasMessage) {
        return dasMapper.fromSettlementUpdate(dasMessage);
    }
}
```

### Settlement Integration (PEXA)

```java
public interface SettlementPlatformClient {
    SettlementWorkspace createWorkspace(SettlementRequest request);
    SettlementStatus getStatus(String workspaceId);
    void submitDocuments(String workspaceId, List<Document> docs);
    SettlementResult settle(String workspaceId);
}

@Service
@ConditionalOnProperty("integration.settlement.provider", havingValue = "pexa")
public class PexaClient implements SettlementPlatformClient {
    // PEXA API integration
    // Maps DAS format to PEXA workspace model
}

@Service
@Profile("dev")
public class StubSettlementClient implements SettlementPlatformClient {
    // Auto-settles after 5 seconds for demo
}
```

---

## 13. Migration Architecture (Batch Pipeline)

Origination handles one LIXI2 message at a time. Migration handles 50,000 loans from a legacy CSV dump. Same validation pipeline, different entry point, additional capabilities: column auto-mapping, bulk transform, quality aggregation, remediation, and reconciliation.

### Why Migration Is a Separate Architecture Concern

| Origination | Migration |
|-------------|-----------|
| Single message | 10k–500k records |
| LIXI2 format on arrival | Arbitrary CSV/Excel, unknown schema |
| Stateless validation | Stateful pipeline (pause, resume, retry) |
| Instant feedback | Batch processing with progress tracking |
| One pass | Multiple passes (validate → remediate → re-validate) |
| New DRAFT application | May enter at any lifecycle state |

### 13.1 Pipeline Architecture

```
Source File (CSV/Excel)
    │
    ▼
┌──────────────────────────────────────────────────────────────┐
│                    MIGRATION PIPELINE                          │
│                                                                │
│  ┌─────────┐  ┌─────────┐  ┌──────────┐  ┌────────────────┐ │
│  │ 1.INGEST│  │ 2. MAP  │  │ 3.TRANS- │  │ 4. VALIDATE    │ │
│  │         │──│         │──│   FORM   │──│                │ │
│  │ Parse   │  │ Column  │  │ Type     │  │ Tiers 1-4      │ │
│  │ Detect  │  │ auto-   │  │ convert, │  │ per-row,       │ │
│  │ types   │  │ match + │  │ normalize│  │ batch-         │ │
│  │ BOM/enc │  │ confirm │  │ address  │  │ optimized      │ │
│  └─────────┘  └─────────┘  └──────────┘  └────────────────┘ │
│                                                                │
│  ┌─────────────────┐  ┌─────────────────────────────────────┐ │
│  │ 5. CLASSIFY     │  │ 6. STORE                            │ │
│  │                 │──│                                     │ │
│  │ Clean / Warn /  │  │ staging table → quality check →     │ │
│  │ Failed          │  │ promote to loan_applications        │ │
│  │ quality score   │  │ set lifecycle state                 │ │
│  └─────────────────┘  └─────────────────────────────────────┘ │
│                                                                │
│  Progress: ████████████████████░░░░░░░  34,102 / 47,312       │
│  Speed: 1,847 rows/sec │ ETA: 7s │ Errors: 2,697             │
└──────────────────────────────────────────────────────────────┘
```

Each stage is a function: `(MigrationJob, Stream<Row>) → Stream<Row>`. Stages compose into a pipeline that can be paused, resumed, and retried. The pipeline is **streaming** — we don't load 500k rows into memory. Rows flow through stages with backpressure.

### 13.2 Stage 1: Ingest (Parse)

```java
@Service
public class MigrationIngestService {

    private final CsvParser csvParser;
    private final ExcelParser excelParser;

    public IngestResult ingest(InputStream file, String filename, UUID jobId) {

        FileFormat format = detectFormat(filename);  // .csv, .xlsx, .xls

        // Parse with type detection
        ParseResult parsed = switch (format) {
            case CSV -> csvParser.parse(file, CsvOptions.builder()
                .detectTypes(true)          // numeric, date, boolean
                .handleBom(true)            // UTF-8 BOM
                .trimWhitespace(true)
                .maxRows(500_000)           // safety limit
                .build());
            case XLSX, XLS -> excelParser.parse(file);
        };

        // Store raw rows in staging
        int stored = stagingRepository.bulkInsert(jobId, parsed.rows());

        return IngestResult.builder()
            .jobId(jobId)
            .rowCount(stored)
            .columns(parsed.columns())       // name, detected type, sample values
            .detectedTypes(parsed.typeMap())  // column → {STRING, NUMERIC, DATE, BOOLEAN}
            .build();
    }
}
```

### 13.3 Stage 2: Column Auto-Mapper

The hardest migration problem: the source CSV has columns like `LOAN_ACCT_NUM`, `BORR_NAME`, `PROP_ADDR`. We need to match these to LIXI2 CAL fields like `Application/@UniqueID`, `PersonApplicant/PersonName`, `RealEstateAsset/Address`.

```java
@Service
public class ColumnAutoMapper {

    // Pre-built synonym dictionary: 500+ known column names from AU lenders
    private final Map<String, List<SynonymEntry>> synonymIndex;

    // LIXI2 CAL field registry: path, type, description, examples
    private final List<LixiFieldDescriptor> lixiFields;

    public List<ColumnMapping> autoMap(List<ColumnDescriptor> sourceColumns) {

        List<ColumnMapping> mappings = new ArrayList<>();

        for (ColumnDescriptor col : sourceColumns) {
            // 1. Exact synonym match (highest confidence)
            Optional<SynonymEntry> exactMatch = findExactSynonym(col.getName());
            if (exactMatch.isPresent()) {
                mappings.add(ColumnMapping.confirmed(col, exactMatch.get().lixiPath(), 0.98));
                continue;
            }

            // 2. Fuzzy name match (Levenshtein + token overlap)
            List<ScoredMatch> fuzzyMatches = fuzzyMatch(col.getName(), lixiFields);

            // 3. Type-compatible filter
            fuzzyMatches = fuzzyMatches.stream()
                .filter(m -> typeCompatible(col.getDetectedType(), m.field().getType()))
                .toList();

            // 4. Sample value analysis (if fuzzy is ambiguous)
            if (fuzzyMatches.size() > 1 && fuzzyMatches.get(0).score() < 0.85) {
                fuzzyMatches = refineBySampleValues(col.getSampleValues(), fuzzyMatches);
            }

            if (!fuzzyMatches.isEmpty() && fuzzyMatches.get(0).score() >= 0.70) {
                mappings.add(ColumnMapping.suggested(
                    col, fuzzyMatches.get(0).field().getPath(), fuzzyMatches.get(0).score(),
                    fuzzyMatches.subList(0, Math.min(3, fuzzyMatches.size()))  // top 3 candidates
                ));
            } else {
                mappings.add(ColumnMapping.unmapped(col));
            }
        }

        return mappings;
    }
}
```

**Synonym dictionary** — pre-seeded with common AU lender column naming patterns:

| Source Pattern | LIXI2 CAL Target | Confidence |
|---------------|-------------------|------------|
| `LOAN_ACCT_NUM`, `ACCOUNT_NUMBER`, `LOAN_ID` | `Application/@UniqueID` | 0.98 |
| `BORR_NAME`, `BORROWER_NAME`, `APPLICANT_NAME` | `PersonApplicant/PersonName` | 0.97 |
| `PROP_ADDRESS`, `PROPERTY_ADDR`, `SECURITY_ADDR` | `RealEstateAsset/Address` | 0.96 |
| `ORIG_AMOUNT`, `LOAN_AMOUNT`, `PRINCIPAL` | `LoanDetails/LoanAmount` | 0.99 |
| `CURRENT_RATE`, `INT_RATE`, `INTEREST_RATE` | `LoanDetails/InterestRate` | 0.97 |
| `SETTLEMENT_DT`, `SETTLE_DATE`, `SETTLED` | `LoanDetails/SettledDate` | 0.95 |
| `DOB`, `DATE_OF_BIRTH`, `BIRTH_DATE` | `PersonApplicant/DateOfBirth` | 0.98 |
| `POSTCODE`, `POST_CODE`, `ZIP` | `Address/Postcode` | 0.99 |
| `PROPERTY_VALUE`, `VALUATION`, `EST_VALUE` | `RealEstateAsset/Valuation/Amount` | 0.93 |
| `LVR`, `LOAN_TO_VALUE`, `LTV_RATIO` | computed (amount/value) | 0.95 |

The operator **always reviews** the mapping before proceeding. Auto-mapping is a suggestion, never a silent decision.

### 13.4 Stage 3: Transform

Apply confirmed mappings. Handle type conversions, normalizations, and edge cases.

```java
@Service
public class MigrationTransformService {

    // Registry of field-level transformers
    private final Map<String, FieldTransformer> transformers = Map.of(
        "date",    new DateNormalizer(),       // DD/MM/YYYY → ISO 8601
        "rate",    new RateNormalizer(),       // monthly ↔ annual, % ↔ decimal
        "amount",  new AmountNormalizer(),     // strip $, commas, negative
        "address", new AddressNormalizer(),    // free-text → structured (street/suburb/state/postcode)
        "name",    new NameNormalizer(),       // "SMITH, JOHN" → {first: "John", last: "Smith"}
        "boolean", new BooleanNormalizer()     // Y/N, Yes/No, 1/0, TRUE/FALSE → boolean
    );

    public TransformResult transformRow(
            Map<String, String> sourceRow,
            List<ColumnMapping> mappings,
            UUID jobId) {

        Map<String, Object> lixiFields = new LinkedHashMap<>();
        List<TransformNote> notes = new ArrayList<>();

        for (ColumnMapping mapping : mappings) {
            if (mapping.isUnmapped()) {
                // Preserve unmapped fields in metadata.legacy
                notes.add(TransformNote.preserved(mapping.getSourceColumn(), sourceRow.get(mapping.getSourceColumn())));
                continue;
            }

            String rawValue = sourceRow.get(mapping.getSourceColumn());
            if (rawValue == null || rawValue.isBlank()) {
                continue;  // null propagation — don't fabricate data
            }

            FieldTransformer transformer = transformers.get(mapping.getTargetType());
            if (transformer != null) {
                TransformOutput output = transformer.transform(rawValue, mapping);
                lixiFields.put(mapping.getTargetPath(), output.value());
                if (output.wasTransformed()) {
                    notes.add(TransformNote.transformed(
                        mapping.getSourceColumn(), rawValue,
                        mapping.getTargetPath(), output.value().toString(),
                        output.transformDescription()  // "converted from DD/MM/YYYY to ISO 8601"
                    ));
                }
            } else {
                lixiFields.put(mapping.getTargetPath(), rawValue);
            }
        }

        return TransformResult.of(lixiFields, notes);
    }
}
```

**Transform documentation**: Every transformation is logged — what field, what the original value was, what we changed it to, and why. This feeds directly into the reconciliation report.

### 13.5 Stage 4–5: Validate & Classify

Validation uses the same 4-tier pipeline as origination (section 6), but optimized for batch:

```java
@Service
public class BatchValidationService {

    private final LixiSchemaValidator schemaValidator;
    private final LixiSchematronValidator schematronValidator;
    private final LenderRuleValidator lenderValidator;
    private final DomainRuleValidator domainValidator;

    /**
     * Validate a batch of transformed rows.
     * Schema is loaded once and reused. Schematron is compiled once.
     * Each row gets a ValidationResult + classification.
     */
    public BatchValidationResult validateBatch(
            UUID jobId,
            Stream<TransformedRow> rows,
            String lenderCode) {

        // Pre-load resources (once per batch, not per row)
        JsonSchema schema = schemaValidator.getSchema("CAL", "2.6.91");
        LenderProfile lender = lenderValidator.getProfile(lenderCode);

        AtomicInteger clean = new AtomicInteger();
        AtomicInteger warning = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        ConcurrentHashMap<String, AtomicInteger> ruleFailureCounts = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, AtomicInteger> fieldCoverage = new ConcurrentHashMap<>();

        rows.parallel().forEach(row -> {
            // Track field coverage
            row.getPopulatedFields().forEach(f ->
                fieldCoverage.computeIfAbsent(f, k -> new AtomicInteger()).incrementAndGet()
            );

            // Validate (tiers 1-4)
            ValidationResult result = validateSingleRow(row, schema, lender);

            // Classify
            Classification classification;
            if (result.hasErrors()) {
                classification = Classification.FAILED;
                failed.incrementAndGet();
            } else if (result.hasWarnings()) {
                classification = Classification.WARNING;
                warning.incrementAndGet();
            } else {
                classification = Classification.CLEAN;
                clean.incrementAndGet();
            }

            // Aggregate rule failures
            result.getViolations().forEach(v ->
                ruleFailureCounts.computeIfAbsent(v.getRuleId(), k -> new AtomicInteger()).incrementAndGet()
            );

            // Update staging row with result
            stagingRepository.updateValidation(jobId, row.getRowIndex(), classification, result);
        });

        return BatchValidationResult.builder()
            .clean(clean.get())
            .warning(warning.get())
            .failed(failed.get())
            .ruleFailureCounts(ruleFailureCounts)
            .fieldCoverage(fieldCoverage)
            .build();
    }
}
```

### 13.6 Quality Aggregation Engine

After validation, aggregate quality metrics across the entire portfolio.

```java
@Service
public class QualityAggregationService {

    /**
     * Calculate 5-dimension quality score + field coverage for a migration job.
     */
    public QualityReport aggregate(UUID jobId) {

        MigrationJob job = jobRepository.findById(jobId).orElseThrow();
        long totalRows = job.getRowCount();

        // 1. Completeness: % of required LIXI2 fields populated across all rows
        Map<String, Double> fieldCoverage = stagingRepository.getFieldCoverage(jobId);
        double completeness = fieldCoverage.values().stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);

        // 2. Accuracy: % of rows passing all domain rules (MR-001..MR-010)
        long passingDomainRules = stagingRepository.countByClassification(jobId, Classification.CLEAN);
        double accuracy = (double) passingDomainRules / totalRows;

        // 3. Consistency: cross-field logical checks (LVR = amount/value, income > expenses)
        long consistentRows = stagingRepository.countConsistentRows(jobId);
        double consistency = (double) consistentRows / totalRows;

        // 4. Validity: % of rows passing schema + Schematron (structure correct)
        long schemaValid = stagingRepository.countSchemaValid(jobId);
        double validity = (double) schemaValid / totalRows;

        // 5. Uniqueness: no duplicate loan IDs, no duplicate borrower+property combos
        long uniqueRows = stagingRepository.countUniqueRows(jobId);
        double uniqueness = (double) uniqueRows / totalRows;

        // Composite (weighted)
        double composite = completeness * 0.25
            + accuracy * 0.25
            + consistency * 0.20
            + validity * 0.20
            + uniqueness * 0.10;

        // Top rule failures (ranked by frequency)
        List<RuleFailureSummary> topFailures = stagingRepository.getTopRuleFailures(jobId, 20);

        // Field coverage detail (ranked by coverage %, lowest first for actionability)
        List<FieldCoverageEntry> coverageDetail = fieldCoverage.entrySet().stream()
            .map(e -> new FieldCoverageEntry(e.getKey(), e.getValue()))
            .sorted(Comparator.comparingDouble(FieldCoverageEntry::coverage))
            .toList();

        return QualityReport.builder()
            .jobId(jobId)
            .totalRows(totalRows)
            .completeness(completeness)
            .accuracy(accuracy)
            .consistency(consistency)
            .validity(validity)
            .uniqueness(uniqueness)
            .composite(composite)
            .classificationBreakdown(Map.of(
                Classification.CLEAN, passingDomainRules,
                Classification.WARNING, stagingRepository.countByClassification(jobId, Classification.WARNING),
                Classification.FAILED, stagingRepository.countByClassification(jobId, Classification.FAILED)
            ))
            .topFailures(topFailures)
            .fieldCoverage(coverageDetail)
            .build();
    }
}
```

### 13.7 Remediation Engine

Remediation is the difference between "finding problems" and "fixing them". Operators define bulk transforms — conditional field updates applied to failing rows — then re-validate to measure the impact.

```java
@Service
public class RemediationService {

    private final MigrationStagingRepository staging;
    private final BatchValidationService validator;
    private final EventStore eventStore;

    /**
     * Preview the impact of a remediation action without applying it.
     */
    public RemediationPreview preview(UUID jobId, RemediationAction action) {

        // Count rows that match the condition
        long matchingRows = staging.countMatchingCondition(jobId, action.getCondition());

        // Simulate the transform on a sample (100 rows)
        List<BeforeAfter> samples = staging.sampleMatchingRows(jobId, action.getCondition(), 100)
            .stream()
            .map(row -> {
                Map<String, Object> before = Map.copyOf(row.getData());
                Map<String, Object> after = applyTransform(row.getData(), action);
                return new BeforeAfter(row.getRowIndex(), before, after);
            })
            .toList();

        return RemediationPreview.builder()
            .matchingRows(matchingRows)
            .samples(samples)
            .estimatedQualityDelta(estimateQualityDelta(jobId, action))
            .build();
    }

    /**
     * Apply a remediation action: update rows, re-validate, log everything.
     */
    @Transactional
    public RemediationResult apply(UUID jobId, RemediationAction action, String actorId) {

        // 1. Snapshot before state
        QualityReport beforeQuality = qualityService.aggregate(jobId);

        // 2. Apply transform to matching rows
        int updatedRows = staging.bulkUpdate(jobId, action.getCondition(), action.getTransform());

        // 3. Re-validate updated rows
        BatchValidationResult revalidation = validator.revalidateRows(
            jobId, action.getCondition()
        );

        // 4. Snapshot after state
        QualityReport afterQuality = qualityService.aggregate(jobId);

        // 5. Log remediation action (immutable audit trail)
        RemediationLogEntry logEntry = RemediationLogEntry.builder()
            .jobId(jobId)
            .actionId(UUID.randomUUID())
            .ruleId(action.getRuleId())
            .condition(action.getCondition())
            .transform(action.getTransform())
            .affectedRows(updatedRows)
            .qualityBefore(beforeQuality.getComposite())
            .qualityAfter(afterQuality.getComposite())
            .actorId(actorId)
            .appliedAt(Instant.now())
            .build();

        remediationLogRepository.save(logEntry);

        // 6. Publish event
        eventStore.append(new RemediationApplied(
            jobId, action.getRuleId(), updatedRows, actorId
        ));

        return RemediationResult.builder()
            .updatedRows(updatedRows)
            .qualityBefore(beforeQuality)
            .qualityAfter(afterQuality)
            .logEntry(logEntry)
            .build();
    }

    private Map<String, Object> applyTransform(Map<String, Object> data, RemediationAction action) {
        Map<String, Object> result = new LinkedHashMap<>(data);
        for (FieldTransform ft : action.getTransform().getFields()) {
            switch (ft.getOperation()) {
                case SET -> result.put(ft.getTargetField(), ft.getValue());
                case COPY -> result.put(ft.getTargetField(), data.get(ft.getSourceField()));
                case MAP -> result.put(ft.getTargetField(), ft.getValueMap().get(data.get(ft.getSourceField())));
                case DELETE -> result.remove(ft.getTargetField());
            }
        }
        return result;
    }
}
```

**Remediation action model**:

```java
public class RemediationAction {
    private String ruleId;             // "MR-008"
    private String description;        // "Set LMI='Y' for pre-2018 high-LVR loans"
    private RowCondition condition;    // WHERE clause equivalent
    private BulkTransform transform;  // SET clause equivalent
}

public class RowCondition {
    private String field;             // "originationDate"
    private Operator operator;        // LT, GT, EQ, IN, IS_NULL, IS_NOT_NULL
    private Object value;             // "2018-01-01"
    private LogicalOp combiner;       // AND, OR (for compound conditions)
    private List<RowCondition> children;
}

public class BulkTransform {
    private List<FieldTransform> fields;
}

public class FieldTransform {
    private String targetField;
    private TransformOp operation;    // SET, COPY, MAP, DELETE
    private Object value;             // for SET
    private String sourceField;       // for COPY
    private Map<Object, Object> valueMap;  // for MAP (old → new)
}
```

### 13.8 Reconciliation Engine

After remediation, prove the migration is clean. Three levels: record, financial, field.

```java
@Service
public class ReconciliationService {

    /**
     * Build a full reconciliation report comparing source file against target database.
     */
    public ReconciliationReport reconcile(UUID jobId) {

        MigrationJob job = jobRepository.findById(jobId).orElseThrow();

        // ── Record-Level ────────────────────────────────────
        long sourceCount = job.getRowCount();
        long targetCount = staging.countPromoted(jobId);
        long rejected = staging.countByClassification(jobId, Classification.FAILED);
        long duplicatesRemoved = staging.countDuplicatesRemoved(jobId);

        RecordReconciliation records = new RecordReconciliation(
            sourceCount, targetCount, rejected, duplicatesRemoved
        );

        // ── Financial ───────────────────────────────────────
        FinancialAggregates sourceAgg = staging.aggregateSource(jobId);
        FinancialAggregates targetAgg = staging.aggregateTarget(jobId);

        FinancialReconciliation financial = FinancialReconciliation.builder()
            .totalNotional(compare(sourceAgg.getTotalNotional(), targetAgg.getTotalNotional()))
            .avgLoanAmount(compare(sourceAgg.getAvgLoanAmount(), targetAgg.getAvgLoanAmount()))
            .avgInterestRate(compare(sourceAgg.getAvgRate(), targetAgg.getAvgRate()))
            .avgLvr(compare(sourceAgg.getAvgLvr(), targetAgg.getAvgLvr()))
            .build();

        // ── Field-Level ─────────────────────────────────────
        List<ColumnMapping> mappings = fieldMappingRepository.findByJobId(jobId);
        List<FieldReconciliation> fields = new ArrayList<>();

        for (ColumnMapping mapping : mappings) {
            if (mapping.isUnmapped()) {
                fields.add(FieldReconciliation.unmapped(
                    mapping.getSourceColumn(),
                    "Preserved in metadata.legacy"
                ));
                continue;
            }

            // Compare source values vs target values for this field
            FieldComparisonResult comparison = staging.compareField(
                jobId, mapping.getSourceColumn(), mapping.getTargetPath()
            );

            fields.add(FieldReconciliation.builder()
                .sourceColumn(mapping.getSourceColumn())
                .targetField(mapping.getTargetPath())
                .exactMatchPercent(comparison.getExactMatchPercent())
                .transformedPercent(comparison.getTransformedPercent())
                .transformDescription(comparison.getTransformDescription())
                .build());
        }

        // ── LIXI2 Compliance ────────────────────────────────
        long compliant = staging.countByClassification(jobId, Classification.CLEAN);
        long withWarnings = staging.countByClassification(jobId, Classification.WARNING);
        long pendingReview = staging.countByClassification(jobId, Classification.FAILED);

        // ── Remediation History ─────────────────────────────
        List<RemediationLogEntry> remediations = remediationLogRepository.findByJobId(jobId);

        return ReconciliationReport.builder()
            .jobId(jobId)
            .generatedAt(Instant.now())
            .records(records)
            .financial(financial)
            .fields(fields)
            .compliance(new ComplianceSummary(compliant, withWarnings, pendingReview))
            .remediations(remediations)
            .build();
    }

    private FinancialComparison compare(BigDecimal source, BigDecimal target) {
        BigDecimal delta = target.subtract(source);
        return new FinancialComparison(source, target, delta, delta.compareTo(BigDecimal.ZERO) == 0);
    }
}
```

**PDF report generation**: The reconciliation report exports to a branded PDF using Thymeleaf + Flying Saucer (same stack as offer letters in section 12). The PDF is the artifact the auditor signs off on.

### 13.9 In-Flight Loan Handling

Not all migrated loans are new. A 47,000-loan book includes loans in every lifecycle state: some are settled and serviced, some are under assessment, some have conditional approval. We must set the correct state machine position.

```java
@Service
public class LifecyclePositionResolver {

    /**
     * Determine the correct ApplicationStatus for a migrated loan
     * based on its source data.
     */
    public ApplicationStatus resolveStatus(Map<String, Object> loanData) {

        String legacyStatus = (String) loanData.get("status");
        boolean hasSettlementDate = loanData.get("settlementDate") != null;
        boolean hasOfferDate = loanData.get("offerDate") != null;
        boolean hasDecisionDate = loanData.get("decisionDate") != null;

        // Settled loans (majority of a typical book)
        if (hasSettlementDate) {
            return ApplicationStatus.SETTLED;
        }

        // Status-based mapping (lender-specific, configurable per migration job)
        return switch (normalizeStatus(legacyStatus)) {
            case "ACTIVE", "SERVICING" -> ApplicationStatus.SETTLED;
            case "APPROVED", "OFFER_ISSUED" -> hasOfferDate
                ? ApplicationStatus.OFFER_ISSUED
                : ApplicationStatus.CONDITIONALLY_APPROVED;
            case "CONDITIONAL" -> ApplicationStatus.CONDITIONALLY_APPROVED;
            case "ASSESSMENT", "PENDING" -> ApplicationStatus.UNDER_ASSESSMENT;
            case "SUBMITTED" -> ApplicationStatus.SUBMITTED;
            case "DISCHARGED", "CLOSED" -> ApplicationStatus.DISCHARGED;
            default -> ApplicationStatus.DRAFT;
        };
    }
}
```

**State machine bypass**: Migrated loans don't walk through every intermediate state. We insert them directly at their resolved position using a `MIGRATION_IMPORT` trigger that the state machine recognizes as valid for any target state. This is the **only** bypass — all subsequent transitions follow the strict DAG.

```java
// In ApplicationStateMachine — special case for migration
public void setMigrationState(LoanApplication app, ApplicationStatus targetState, UUID jobId) {
    ApplicationStatus previousState = app.getStatus();

    // Record the jump (no intermediate states)
    app.setStatus(targetState);

    // Event records the full context
    eventStore.append(new StateTransitioned(
        app.getId(),
        previousState,
        targetState,
        "MIGRATION_IMPORT",   // trigger type
        "migration-job:" + jobId,
        Instant.now(),
        Map.of("migrationJobId", jobId.toString(), "sourceStatus", previousState.name())
    ));
}
```

### 13.10 New Database Tables

```sql
-- Migration job tracking
CREATE TABLE migration_jobs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(200) NOT NULL,         -- "Q1 2026 Legacy Extract"
    source_filename VARCHAR(500) NOT NULL,
    source_row_count BIGINT NOT NULL,
    source_column_count INTEGER NOT NULL,
    lender_code     VARCHAR(50),                   -- target lender profile
    status          VARCHAR(20) NOT NULL DEFAULT 'CREATED',
        -- CREATED → INGESTED → MAPPED → TRANSFORMING → VALIDATING → CLASSIFIED → PROMOTED → RECONCILED
    quality_report  JSONB,                         -- cached QualityReport
    reconciliation  JSONB,                         -- cached ReconciliationReport
    created_by      VARCHAR(100) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Staging table for migration rows (partitioned by job for fast drops)
CREATE TABLE migration_loan_staging (
    id              BIGSERIAL,
    job_id          UUID NOT NULL REFERENCES migration_jobs(id),
    row_index       INTEGER NOT NULL,
    source_data     JSONB NOT NULL,                -- raw CSV row as key:value
    mapped_data     JSONB,                         -- after column mapping
    transformed_data JSONB,                        -- after type transforms
    lixi_data       JSONB,                         -- LIXI2 CAL structure
    validation_result JSONB,                       -- per-tier results
    classification  VARCHAR(10),                   -- CLEAN, WARNING, FAILED
    quality_score   DECIMAL(5,4),                  -- row-level 0.0000–1.0000
    lifecycle_state VARCHAR(30),                   -- resolved ApplicationStatus
    promoted        BOOLEAN NOT NULL DEFAULT false, -- true when copied to loan_applications
    promoted_id     UUID,                          -- FK to loan_applications.id
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (job_id, id)
) PARTITION BY LIST (job_id);
-- Partitions created dynamically per job: migration_loan_staging_{job_id_short}

CREATE INDEX idx_staging_classification ON migration_loan_staging(job_id, classification);
CREATE INDEX idx_staging_quality ON migration_loan_staging(job_id, quality_score);

-- Column mapping configuration per job
CREATE TABLE migration_field_mappings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id          UUID NOT NULL REFERENCES migration_jobs(id),
    source_column   VARCHAR(200) NOT NULL,
    target_path     VARCHAR(500),                  -- LIXI2 CAL path, null if unmapped
    confidence      DECIMAL(3,2),                  -- auto-mapper confidence 0.00–1.00
    status          VARCHAR(10) NOT NULL DEFAULT 'SUGGESTED',
        -- SUGGESTED, CONFIRMED, REJECTED, MANUAL
    transform_type  VARCHAR(20),                   -- date, rate, amount, address, name, boolean
    confirmed_by    VARCHAR(100),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Remediation action log (append-only audit trail)
CREATE TABLE remediation_actions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id          UUID NOT NULL REFERENCES migration_jobs(id),
    rule_id         VARCHAR(20) NOT NULL,          -- "MR-008"
    description     VARCHAR(500) NOT NULL,
    condition       JSONB NOT NULL,                -- RowCondition tree
    transform       JSONB NOT NULL,                -- BulkTransform specification
    affected_rows   INTEGER NOT NULL,
    quality_before  DECIMAL(5,4) NOT NULL,
    quality_after   DECIMAL(5,4) NOT NULL,
    actor_id        VARCHAR(100) NOT NULL,
    applied_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Reconciliation snapshots (one per job, regenerated after each remediation pass)
CREATE TABLE reconciliation_reports (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id          UUID NOT NULL REFERENCES migration_jobs(id),
    report_data     JSONB NOT NULL,                -- full ReconciliationReport
    generated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### 13.11 Migration APIs

```
POST   /api/v1/migration/jobs                       Create migration job (upload CSV)
GET    /api/v1/migration/jobs                       List migration jobs
GET    /api/v1/migration/jobs/{id}                  Get job status + progress
DELETE /api/v1/migration/jobs/{id}                  Cancel job (deletes staging data)

GET    /api/v1/migration/jobs/{id}/mappings         Get auto-detected column mappings
PUT    /api/v1/migration/jobs/{id}/mappings         Confirm/update column mappings
POST   /api/v1/migration/jobs/{id}/transform        Trigger transform stage
POST   /api/v1/migration/jobs/{id}/validate         Trigger validation stage

GET    /api/v1/migration/jobs/{id}/quality           Get quality report
GET    /api/v1/migration/jobs/{id}/quality/coverage  Field coverage detail
GET    /api/v1/migration/jobs/{id}/quality/failures  Rule failure summary
GET    /api/v1/migration/jobs/{id}/quality/failures/{ruleId}  Drill-down: rows + pattern analysis

POST   /api/v1/migration/jobs/{id}/remediate/preview Preview remediation impact
POST   /api/v1/migration/jobs/{id}/remediate/apply   Apply remediation + re-validate
GET    /api/v1/migration/jobs/{id}/remediation-log   List all remediation actions

POST   /api/v1/migration/jobs/{id}/promote           Promote clean+warning rows to loan_applications
GET    /api/v1/migration/jobs/{id}/reconciliation     Generate/get reconciliation report
GET    /api/v1/migration/jobs/{id}/reconciliation/pdf Download PDF reconciliation report

GET    /api/v1/migration/jobs/{id}/export/lixi       Export promoted loans as LIXI2 CAL JSON (NDJSON stream)
GET    /api/v1/migration/jobs/{id}/rows               Paginated staging rows (filterable by classification)
```

---

## 14. SDK Design

### TypeScript SDK (Primary)

```typescript
// @atheryon/mortgage-sdk

import { AtherMortgageClient } from '@atheryon/mortgage-sdk';

// Initialize
const client = new AtherMortgageClient({
  apiKey: process.env.ATHERYON_API_KEY,
  environment: 'sandbox',           // or 'production'
  baseUrl: 'https://dev.atheryon.ai',
});

// --- Application Lifecycle ---

// Create from LIXI2 message
const app = await client.applications.ingestLixi(lixiCalJson, {
  standard: 'CAL',
  version: '2.6.91',
  lenderCode: 'WBC',
});

// Create programmatically
const app = await client.applications.create({
  borrower: {
    firstName: 'Sarah',
    lastName: 'Mitchell',
    dateOfBirth: '1985-03-22',
    email: 'sarah@example.com',
    employment: [{
      type: 'PAYG',
      employer: 'Acme Corp',
      role: 'Software Engineer',
      startDate: '2020-01-15',
      grossAnnualIncome: 120000,
    }],
  },
  property: {
    address: '42 Blue Gum Drive, Randwick NSW 2031',
    purchasePrice: 850000,
    propertyType: 'HOUSE',
  },
  loan: {
    amount: 680000,
    termMonths: 360,
    purpose: 'OWNER_OCCUPIED_PURCHASE',
    rateType: 'VARIABLE',
  },
});

// --- Assessment ---

const svcResult = await client.serviceability.assess(app.id);
// { umi: 1250.00, dsr: 0.32, outcome: 'PASS', hemApplied: true }

const decision = await client.decisions.run(app.id);
// { outcome: 'APPROVED', creditScore: 745, conditions: [...] }

// --- Real-time Events ---

const stream = client.events.subscribe(app.id);
stream.on('STATE_CHANGED', (event) => {
  console.log(`${event.fromState} → ${event.toState}`);
});

// --- LIXI2 Export ---

const lixi = await client.applications.exportLixi(app.id, {
  standard: 'CAL',
  version: '2.6.91',
  format: 'json',
});

// --- Validation Only (no state machine) ---

const result = await client.validation.validate(lixiMessage, {
  standard: 'CAL',
  version: '2.6.91',
  lenderCode: 'WBC',
  tiers: ['schema', 'schematron', 'lender'],
});
```

### SDK Architecture

```
@atheryon/mortgage-sdk/
├── src/
│   ├── client.ts              # AtherMortgageClient entry point
│   ├── resources/
│   │   ├── applications.ts    # CRUD + lifecycle transitions
│   │   ├── serviceability.ts  # SVC assessment
│   │   ├── decisions.ts       # CDA + decision engine
│   │   ├── documents.ts       # Upload, verify, list
│   │   ├── validation.ts      # Standalone LIXI2 validation
│   │   └── events.ts          # SSE subscription
│   ├── types/
│   │   ├── application.ts     # Domain types
│   │   ├── lixi.ts            # LIXI2 message types (generated from schema)
│   │   ├── validation.ts      # Validation result types
│   │   └── events.ts          # Event payload types
│   ├── http.ts                # HTTP client (fetch-based, retries, auth)
│   └── errors.ts              # Typed error classes
├── generated/
│   └── lixi-cal-2.6.91.d.ts  # Auto-generated from LIXI2 JSON Schema
└── package.json
```

Type generation from LIXI2 schema:

```bash
# Generate TypeScript types from LIXI2 JSON Schema
npx json-schema-to-typescript \
  --input schemas/lixi-cal-2.6.91.json \
  --output generated/lixi-cal-2.6.91.d.ts \
  --bannerComment "Auto-generated from LIXI2 CAL 2.6.91 schema. Do not edit."
```

---

## 15. Security Architecture

### Authentication & Authorization

| Layer | Mechanism | Details |
|-------|-----------|---------|
| **API Gateway** | API Key + JWT | API keys for server-to-server, JWT for user sessions |
| **Broker Auth** | mTLS + API Key | Mutual TLS for broker integrations, API key in header |
| **Lender Auth** | OAuth 2.0 | Lender back-office uses OAuth with PKCE |
| **Webhook Verification** | HMAC-SHA256 | Payload signed with subscriber's secret |
| **Internal** | Spring Security | Role-based: BROKER, ASSESSOR, UNDERWRITER, ADMIN |

### Data Protection

| Data Class | Protection | Regulation |
|-----------|------------|------------|
| TFN (Tax File Number) | Encrypt at rest (AES-256), never log, mask in API responses | Privacy Act 1988 (AU) |
| Credit scores | Encrypt at rest, access-logged, 30-day cache per AU regulations | Privacy Act, CR Code |
| Personal information | Encrypt at rest, access-controlled per role | APPs (AU Privacy Principles) |
| LIXI2 messages | Stored in JSONB with PII fields encrypted | LIXI EULA + Privacy Act |
| API keys | Hashed (bcrypt), never stored in plain text | OWASP best practice |

### Audit Trail

Every API call, state transition, and external message is logged to the `domain_events` table with:
- Actor identity (user ID, API key ID, or system)
- Timestamp (UTC)
- Source IP address
- Correlation ID (for tracing request chains)
- Before/after state (for mutations)

---

## 16. Deployment Architecture

### Azure Infrastructure

```
┌───────────────────────────────────────────────────────┐
│                   Azure Container Apps                  │
│                  (cdm-containerenv-dev)                 │
│                                                         │
│  ┌───────────────────┐    ┌──────────────────────────┐ │
│  │  ca-labs-dev       │    │  ca-mortgages-dev        │ │
│  │  (Next.js)        │    │  (Spring Boot)           │ │
│  │  Port 3000        │◄──►│  Port 8080               │ │
│  │                   │    │                          │ │
│  │  External ingress │    │  Internal ingress        │ │
│  │  dev.atheryon.ai  │    │  (cluster-only)          │ │
│  └───────────────────┘    └───────────┬──────────────┘ │
│                                       │                 │
│  ┌────────────────────────────────────┼────────────────┤
│  │  Managed services                  │                │
│  │                                    │                │
│  │  ┌──────────────┐  ┌──────────────▼──────────────┐ │
│  │  │  Redis Cache  │  │  PostgreSQL Flexible Server │ │
│  │  │  (events,     │  │  (domain data, events,     │ │
│  │  │   sessions)   │  │   lender config, messages)  │ │
│  │  └──────────────┘  └────────────────────────────┘ │
│  └─────────────────────────────────────────────────────┘
└───────────────────────────────────────────────────────┘
```

### CI/CD Pipeline

```
Push to main
    │
    ├── GitHub Actions: deploy-dev.yml
    │   ├── Build Docker image (multi-stage)
    │   ├── Push to ACR (acrathndev.azurecr.io)
    │   ├── Run Flyway migrations
    │   ├── Deploy to ca-mortgages-dev
    │   ├── Health check (GET /health)
    │   └── Smoke test (POST /api/v1/lixi/validate with sample)
    │
    └── GitHub Actions: test.yml
        ├── Unit tests (JUnit 5)
        ├── Integration tests (Testcontainers + PostgreSQL)
        ├── LIXI2 validation tests (sample messages)
        └── E2E tests (Playwright against dev)
```

---

## 17. Phasing & Delivery Plan (Revised — Origination + Migration)

The original plan was 24 weeks, origination-only. This revised plan interleaves migration work alongside origination, because:
1. Migration reuses validation + quality infrastructure — build once, use twice
2. Migration is the bigger commercial opportunity (every core banking replacement needs it)
3. Both tracks should be demo-able by week 8

### Phase 0: Foundation (Week 1–2)

| Deliverable | Track | Detail |
|------------|-------|--------|
| Module structure | Both | Create lixi, integration, notification, config, **migration** packages |
| Database migrations — origination | Orig | `lixi_messages`, `domain_events`, `lender_profiles`, `webhook_subscriptions` |
| Database migrations — migration | Migr | `migration_jobs`, `migration_loan_staging`, `migration_field_mappings`, `remediation_actions`, `reconciliation_reports` |
| LIXI2 schema bundle | Both | Download CAL 2.6.91 JSON Schema, Schematron rules; package as resources |
| Dependency setup | Both | networknt/json-schema-validator, ph-schematron, WebClient |
| Integration test harness | Both | Testcontainers + PostgreSQL + sample LIXI2 messages |
| Seed data: migration CSV | Migr | Generate 10,000-loan CSV with known quality issues (MR-008 LMI gaps, missing income, rate outliers) |

### Phase 1: Validation + Ingest + Quality Engine (Week 3–6)

| Deliverable | Track | Detail |
|------------|-------|--------|
| Tier 1: JSON Schema validation | Both | `LixiSchemaValidator` with cached schema per version |
| Tier 2: Schematron validation | Both | `LixiSchematronValidator` with LIXI's 1,600 rules |
| CAL → Domain mapper | Orig | `LixiCalMapper` — 25 primary mappings |
| Ingest endpoint | Orig | `POST /api/v1/lixi/ingest` — validate + transform + persist |
| Emit endpoint | Orig | `GET /api/v1/lixi/emit/{id}` — domain → LIXI2 CAL |
| Standalone validation API | Both | `POST /api/v1/lixi/validate` — tiers 1–3, no state machine |
| Raw message store | Orig | `lixi_messages` table, inbound/outbound with full payload |
| Mortgage quality rules | Migr | MR-001..MR-010 in quality engine + mortgage-specific: LVR consistency, income completeness, employment depth |
| Column auto-mapper | Migr | `ColumnAutoMapper` with 500+ synonym dictionary, fuzzy matching |
| Migration ingest pipeline | Migr | Stages 1-5: parse → map → transform → validate → classify |
| Quality aggregation engine | Migr | 5-dimension scoring, field coverage, rule failure aggregation |
| Labs-platform: broker submit UI | Orig | Paste/upload LIXI2 JSON, see validation results |
| Labs-platform: migration workspace | Migr | `/mortgages/migrate` — upload CSV, pipeline progress, field mapping confirmation |
| Labs-platform: quality dashboard | Migr | `/mortgages/migrate/quality` — gauges, classification bar, field coverage heatmap, top failures |
| **Tests**: 80+ (50 mapping, 10 ingest, 20 batch validation) |

**Week 6 milestone**: Both tracks demo-able. Origination: paste LIXI2 → validate → create application. Migration: upload CSV → quality dashboard → field coverage.

### Phase 2: Assessment + Migration Drill-Down (Week 7–10)

| Deliverable | Track | Detail |
|------------|-------|--------|
| HEM lookup table | Orig | ABS data loader, quarterly update |
| Enhanced ServiceabilityCalculator | Orig | HEM integration, household breakdown |
| SVC adapter | Orig | `LixiSvcMapper` — request/response |
| External SVC passthrough | Orig | Forward LIXI2 SVC to configured provider |
| CDA adapter | Orig | `LixiCdaMapper` — request/response |
| Credit bureau stub | Orig | `StubCreditBureauClient` with configurable test data |
| Decision engine integration | Orig | CDA results fed into DecisionEngine |
| Failure drill-down | Migr | `/mortgages/migrate/failures/{ruleId}` — pattern analysis, sample table, distribution charts |
| Remediation engine | Migr | Preview + apply bulk transforms, re-validate, before/after comparison |
| Reconciliation engine | Migr | Record-level + financial + field-level reconciliation |
| Labs-platform: reconciliation page | Migr | `/mortgages/migrate/reconciliation` — auditor-ready view |
| Lifecycle position resolver | Migr | Map legacy status → state machine position for in-flight loans |
| Promotion pipeline | Migr | Promote clean+warning rows from staging to `loan_applications` |
| **Tests**: 50+ (30 SVC/CDA, 20 migration drill-down + remediation) |

**Week 10 milestone**: Both tracks polished. Origination: full assessment flow. Migration: drill-down → remediate → before/after → reconciliation.

### Phase 3: Lender Config + Events (Week 11–14)

| Deliverable | Track | Detail |
|------------|-------|--------|
| Lender profile store | Both | CRUD API, JSON-based configuration |
| Tier 3 validation | Both | Dynamic per-lender field requirements |
| EGB importer | Orig | Parse EGB 2.0.0 XML into LenderProfile |
| Event store | Both | `domain_events` table, `EventStore` service |
| SSE endpoint | Orig | `/api/v1/events/stream` with filtering |
| Webhook dispatcher | Orig | Async HMAC-signed delivery, 3x retry |
| PDF report generator | Migr | Reconciliation + remediation log as branded PDF |
| LIXI2 bulk export | Migr | Export promoted loans as LIXI2 CAL NDJSON |
| Labs-platform: lender config UI | Both | CRUD interface for lender profiles |
| Labs-platform: notification panel | Orig | SSE-powered real-time event feed |
| **Tests**: 30+ (lender rules, event store, webhook, PDF generation) |

### Phase 4: Document Packaging (Week 15–18)

| Deliverable | Track | Detail |
|------------|-------|--------|
| DAS adapter | Orig | `LixiDasMapper` — settlement instruction generation |
| Document template engine | Orig | Thymeleaf-based PDF generation (offer letter, contract) |
| E-signing integration | Orig | DocuSign API client with webhook callbacks |
| Settlement stub | Orig | `StubSettlementClient` for PEXA workflow simulation |
| Document status tracking | Orig | Upload → Verify → Sign → Submit → Archive |
| Labs-platform: document panel | Orig | Upload, review, sign status dashboard |
| **Tests**: 15+ (DAS mapping, document lifecycle) |

### Phase 5: SDK + Developer Experience (Week 19–22)

| Deliverable | Track | Detail |
|------------|-------|--------|
| TypeScript SDK | Both | `@atheryon/mortgage-sdk` — lifecycle + migration APIs |
| Generated LIXI2 types | Both | Auto-generated from JSON Schema |
| Python SDK | Both | `atheryon-mortgage` PyPI package — core + batch operations |
| OpenAPI 3.1 spec | Both | Auto-generated from Spring Boot controllers |
| API documentation site | Both | Built from OpenAPI spec + usage examples |
| Sample applications | Both | TS: broker submission, Python: batch migration |
| **Tests**: SDK integration tests against dev environment |

### Phase 6: Hardening (Week 23–24)

| Deliverable | Track | Detail |
|------------|-------|--------|
| Security audit | Both | PII encryption, API key management, audit logging |
| Performance — origination | Orig | Load test ingest (target: 50 msg/sec) |
| Performance — migration | Migr | Load test batch pipeline (target: 2,000 rows/sec for 500k rows) |
| Error handling | Both | Retry policies, dead letter queues, alerting |
| Monitoring | Both | Application Insights, custom dashboards |
| Documentation | Both | Architecture guide, runbook, troubleshooting |
| Production deployment | Both | `labs.atheryon.ai` / `mortgages.atheryon.ai` |

### Dependency Graph (Revised)

```
                    Origination                    Migration
                    ───────────                    ─────────
Phase 0 (Wk 1-2)   Foundation + schemas           Seed data + migration tables
                    ──────┬────────────────────────────┬──────────────────
Phase 1 (Wk 3-6)   Validation + ingest MVP        Quality engine + batch pipeline
                    ──────┬────────────────────────────┬──────────────────
Phase 2 (Wk 7-10)  Assessment (SVC/CDA)           Drill-down + remediation + recon
                    ──────┬────────────────────────────┬──────────────────
Phase 3 (Wk 11-14) Config + events                PDF reports + LIXI2 export
                    ──────┬────────────────────────────┘
Phase 4 (Wk 15-18) Documents (DAS/PEXA)           (migration complete)
                    ──────┬──────────────────────────────────────────────
Phase 5 (Wk 19-22) SDK (both tracks)
                    ──────┬──────────────────────────────────────────────
Phase 6 (Wk 23-24) Hardening + perf testing

Week:  1  2  3  4  5  6  7  8  9  10  11  12  13  14  15  16  17  18  19  20  21  22  23  24
                     ▲              ▲                ▲
                     │              │                │
              Demo-able       Polished        Audit-ready
              (both tracks)   (both tracks)   (migration)
```

Phase 1 is the **MVP for both tracks**. After week 6: origination demo (paste LIXI2 → validate → create application) and migration demo (upload CSV → quality dashboard → field coverage). Week 10: full remediation + reconciliation workflow. Week 14: PDF reports for auditors.

---

## 18. Risk Register

| # | Risk | Likelihood | Impact | Mitigation |
|---|------|-----------|--------|------------|
| R1 | LIXI2 schema changes break our mappers | Medium | High | Pin schema versions. Validate against multiple versions. Regression test suite with LIXI sample messages. |
| R2 | Schematron rules require XML but we're JSON-first | Certain | Medium | JSON→XML conversion before Schematron. Explore json-schematron alternatives. Accept the conversion overhead (~20ms). |
| R3 | EGB 2.0.0 is too stale to be useful | High | Low | Don't depend on it. Our lender config store is primary. EGB import is a convenience, not a requirement. |
| R4 | Credit bureau integration is complex and vendor-specific | High | High | Build behind interface. Stub for dev/demo. Negotiate API access early (Equifax lead time is 4-6 weeks). |
| R5 | PEXA integration requires accreditation | High | Medium | Use stub for phases 1-4. Begin PEXA accreditation process in parallel (12-16 weeks). |
| R6 | LIXI licensing costs | Low | Low | Dev license is free for 6 months. Production license ~$2,955/year at our volume. Budget it. |
| R7 | Performance: Schematron on 1,600 rules may be slow | Medium | Medium | Benchmark early (Phase 1). If >500ms, switch from ph-schematron to compiled Saxon XSLT. |
| R8 | TFN handling requires careful crypto | Certain | Critical | Never log TFNs. Encrypt at rest with per-tenant keys. Mask in all API responses. Audit access. |
| R9 | Mapping 700 LIXI2 elements is labor-intensive | Certain | Medium | Prioritize: map the ~80 elements used in 95% of residential mortgage applications. Add edge cases incrementally. |
| R10 | Team capacity (small team, 24-week plan) | High | High | Phase 1 (MVP) in 6 weeks is achievable. Phases 2-6 can overlap. Use AI agents for mapper generation and test creation. |
| R11 | Migration staging table size (500k rows × JSONB) | Medium | Medium | Partition by job_id. Drop partitions when job completes. Don't keep staging data permanently — promote to loan_applications and archive. |
| R12 | Column auto-mapper accuracy on unknown schemas | High | Medium | Synonym dictionary covers ~80% of AU lender column names. Remaining 20% require operator confirmation. Never auto-promote unconfirmed mappings. |
| R13 | In-flight loans: state machine position incorrect | Medium | High | Lifecycle resolver is configurable per migration job. Default to DRAFT if status is ambiguous. Operator reviews before promotion. |
| R14 | Remediation audit trail compliance | Low | Critical | Every bulk transform is logged with who/when/what/condition. Immutable `remediation_actions` table. PDF export for auditor. |
| R15 | Legacy data quality worse than expected (< 50% completeness) | Medium | High | Quality dashboard makes this visible immediately. Remediation engine provides tools to fix. Reconciliation proves the fixes. Set client expectations during discovery. |

---

## 19. Success Metrics

| Metric | Target | When |
|--------|--------|------|
| LIXI2 message ingest success rate | >95% (valid messages) | Phase 1 |
| Validation pipeline latency (all 4 tiers) | <500ms p95 | Phase 1 |
| Round-trip fidelity (ingest → emit → diff) | <2% field loss | Phase 1 |
| Serviceability calculation accuracy vs external provider | <1% variance | Phase 2 |
| Webhook delivery success rate | >99.5% (with retries) | Phase 3 |
| API response time (p95) | <200ms | Phase 6 |
| Lender onboarding time (new profile) | <2 hours | Phase 3 |
| **Migration** | | |
| Batch pipeline throughput | >1,500 rows/sec (10k CSV under 10s) | Phase 1 |
| Column auto-mapper accuracy | >80% columns auto-matched at >0.90 confidence | Phase 1 |
| Quality report generation time | <5s for 50k-row job | Phase 1 |
| Remediation round-trip (apply + re-validate) | <30s for 50k-row job | Phase 2 |
| Reconciliation: financial delta | $0 for amount/rate/LVR (exact match) | Phase 2 |
| Reconciliation: record count match | 100% (source rows = target + rejected) | Phase 2 |
| Field coverage visibility | 100% of LIXI2 mandatory fields tracked | Phase 1 |
| PDF report generation | <10s for full reconciliation + remediation log | Phase 3 |

---

## Appendix A: Technology Stack

| Component | Technology | Version | Why |
|-----------|-----------|---------|-----|
| Runtime | Java 17 (OpenJDK) | 17.x | Existing mortgages-platform stack |
| Framework | Spring Boot | 3.3.x | Existing, mature, excellent ecosystem |
| Database | PostgreSQL | 15+ | Existing, JSONB for flexible storage |
| Cache | Redis | 7.x | SSE fanout, session cache, rate limiting |
| JSON Schema | networknt/json-schema-validator | 1.5.x | Fastest JVM Draft 7 implementation |
| Schematron | ph-schematron | 8.x | Pure Java, ISO Schematron support |
| HTTP Client | Spring WebClient | 6.x | Non-blocking, built-in retry |
| PDF Generation | Thymeleaf + Flying Saucer | 3.x / 9.x | HTML templates → PDF, already in Spring |
| E-Signing | DocuSign eSignature API | v2.1 | Market leader in AU |
| Frontend | Next.js + React | 14.x | Existing labs-platform stack |
| State Management | Zustand | 4.x | Existing, lightweight |
| TypeScript SDK | TypeScript + fetch | 5.x | Zero-dependency, modern |
| Python SDK | Python + httpx | 3.11+ | Async-capable, type-hinted |
| CI/CD | GitHub Actions | - | Existing pipeline |
| Infrastructure | Azure Container Apps | - | Existing deployment target |
| Monitoring | Azure Application Insights | - | Existing observability stack |

## Appendix B: LIXI2 Schema Versions Supported

| Standard | Initial Version | Purpose |
|----------|----------------|---------|
| CAL | 2.6.91 | Credit Application (AU) — primary ingest/emit |
| SVC | 2.0.75 | Serviceability assessment exchange |
| CDA | 2.0.86 | Credit decisioning exchange |
| DAS | 2.2.90 | Document preparation & settlement |
| LMI | 2.0.25 | Mortgage insurance (future) |
| VAL | 2.0.25 | Valuations (future) |
| EGB | 2.0.0 | Lender configuration import (legacy compat) |

We will NOT support LIXI1 (XML-only, deprecated). All integration is LIXI2 JSON-first with XML conversion only for Schematron validation.

---

*End of Technical Design*
