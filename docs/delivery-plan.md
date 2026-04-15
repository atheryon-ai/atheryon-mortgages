# Delivery Plan: LIXI2 Mortgage Platform

**Author**: Atheryon Labs Architecture
**Date**: 15 April 2026
**Status**: DRAFT
**Source**: `docs/tech-design-lixi2-extension.md` (sections 1–19), `docs/demo-playbook.md` (Tracks A + B)

---

## 1. Executive Summary

10 specialist teams, 36 agents, 24 weeks. Two demo-able tracks by week 6, production-ready by week 24.

Each team is a self-contained unit with a **team lead** (coordinates, reviews, unblocks) and 2–4 **specialist agents** (build, test, deliver). Teams own a vertical slice of the architecture and deliver working software every 2 weeks.

### Team Map

```
                                ┌──────────────────────┐
                                │   PROGRAMME LEAD     │
                                │   (orchestrates all)  │
                                └──────────┬───────────┘
                                           │
              ┌────────────────────────────┼────────────────────────────┐
              │                            │                            │
    ┌─────────┴──────────┐    ┌───────────┴───────────┐    ┌──────────┴──────────┐
    │  PLATFORM LAYER    │    │  DOMAIN LAYER          │    │  EXPERIENCE LAYER   │
    │                    │    │                        │    │                     │
    │  T1 Foundation     │    │  T3 Gateway & Mappers  │    │  T8 UI-Origination  │
    │  T2 Validation     │    │  T4 Assessment Engine  │    │  T9 UI-Migration    │
    │  T7 Config/Events  │    │  T5 Migration Pipeline │    │  T10 SDK & DevEx    │
    │                    │    │  T6 Quality & Recon    │    │                     │
    └────────────────────┘    └────────────────────────┘    └─────────────────────┘
```

### Key Numbers

| Metric | Value |
|--------|-------|
| Teams | 10 |
| Total agents (incl. leads) | 36 |
| Duration | 24 weeks (6 phases) |
| First demo milestone | Week 6 (both tracks) |
| Production milestone | Week 24 |
| Backend deliverables | ~45 Java services/controllers |
| Frontend deliverables | ~15 pages/components |
| Database tables | ~10 new tables |
| API endpoints | ~50 REST endpoints |
| Test target | 300+ automated tests |

---

## 2. Team Roster

### Team 1: Foundation

**Mandate**: Build the platform skeleton — module structure, database schema, CI/CD, test infrastructure, seed data. Every other team depends on this team finishing first.

**Phase alignment**: Phase 0 (Week 1–2), then support role.

| Agent | Role | Skills | Model |
|-------|------|--------|-------|
| **t1-lead** | Team lead | Spring Boot architecture, Flyway, Gradle | Opus |
| **t1-db** | DB migration specialist | PostgreSQL, Flyway, schema design, partitioning | Sonnet |
| **t1-scaffold** | Module scaffolder | Spring Boot module structure, package layout, DI config | Sonnet |
| **t1-seed** | Test data engineer | LIXI2 sample messages, CSV generation, fixture design | Haiku |

**Deliverables**:

| # | Deliverable | Tech Design Ref | Agent | Week |
|---|------------|-----------------|-------|------|
| F1 | Module structure (lixi, core, integration, notification, config, migration packages) | §4 Container Architecture | t1-scaffold | 1 |
| F2 | Flyway migrations — origination tables (`lixi_messages`, `domain_events`, `lender_profiles`, `webhook_subscriptions`) | §5 New Database Tables | t1-db | 1 |
| F3 | Flyway migrations — migration tables (`migration_jobs`, `migration_loan_staging`, `migration_field_mappings`, `remediation_actions`, `reconciliation_reports`) | §13.10 | t1-db | 1 |
| F4 | LIXI2 schema bundle (CAL 2.6.91 JSON Schema + Schematron rules in `resources/lixi/`) | §6 Validation Pipeline | t1-scaffold | 1 |
| F5 | Dependency setup (networknt, ph-schematron, WebClient, Testcontainers) | Appendix A | t1-scaffold | 1 |
| F6 | Integration test harness (Testcontainers + PostgreSQL base class) | §16 Phasing | t1-lead | 2 |
| F7 | Sample LIXI2 CAL messages (valid, warnings, invalid — 6 variants) | §6, Demo Scene 1 | t1-seed | 2 |
| F8 | Migration seed CSV (10,000 loans with known quality issues: MR-008 LMI gaps, missing income, rate outliers, pre-2018 originations) | §13, Demo Scene M1 | t1-seed | 2 |

**Dependencies**: None (first to start).
**Blocks**: All other teams.

---

### Team 2: LIXI2 Validation Pipeline

**Mandate**: Build the 4-tier validation pipeline — the most reusable component in the system. Used by origination (single message), migration (batch), and the standalone validation API.

**Phase alignment**: Phase 1 (Week 3–6).

| Agent | Role | Skills | Model |
|-------|------|--------|-------|
| **t2-lead** | Team lead | Validation architecture, LIXI2 standard knowledge | Opus |
| **t2-schema** | Schema validation specialist | networknt/json-schema-validator, JSON Schema Draft 7 | Sonnet |
| **t2-schematron** | Schematron specialist | ph-schematron, ISO Schematron, SVRL parsing, JSON↔XML conversion | Sonnet |
| **t2-rules** | Business rules specialist | Lender rule engine (Tier 3), domain rules MR-001..MR-010 (Tier 4), SpEL conditions | Sonnet |

**Deliverables**:

| # | Deliverable | Tech Design Ref | Agent | Week |
|---|------------|-----------------|-------|------|
| V1 | `LixiSchemaValidator` — cached JSON Schema per version, <10ms | §6 Tier 1 | t2-schema | 3 |
| V2 | `LixiSchematronValidator` — 1,600 LIXI rules, JSON→XML conversion, SVRL parsing | §6 Tier 2 | t2-schematron | 3–4 |
| V3 | `LenderRuleValidator` — dynamic per-lender field requirements from `lender_profiles`, conditional rules (SpEL) | §6 Tier 3 | t2-rules | 4–5 |
| V4 | `DomainRuleValidator` — MR-001..MR-010 mortgage rules, LTV bands, SVC limits | §6 Tier 4 | t2-rules | 4–5 |
| V5 | `ValidationPipeline` — compose all 4 tiers, aggregate results, calculate field coverage | §6 ValidationResult | t2-lead | 5 |
| V6 | Standalone validation API: `POST /api/v1/lixi/validate` (tiers 1–3, no state machine) | §6 Standalone Service | t2-lead | 5–6 |
| V7 | 60+ unit tests (15 per tier) + 10 integration tests | §17 Phase 1 | t2-schema, t2-rules | 3–6 |

**Dependencies**: T1 (module structure, schema bundle, test harness).
**Blocks**: T3 (gateway uses validation), T5 (migration uses batch validation), T8 (broker submit UI calls validate API).

---

### Team 3: LIXI2 Gateway & Mappers

**Mandate**: Build the LIXI2 ingest/emit gateway — translate between LIXI2 CAL messages and our internal domain model. This is the most critical engineering: 700 LIXI2 elements mapped to ~15 entities.

**Phase alignment**: Phase 1 (Week 3–6).

| Agent | Role | Skills | Model |
|-------|------|--------|-------|
| **t3-lead** | Team lead | Domain-driven design, mapping strategy, API design | Opus |
| **t3-mapper** | CAL mapper specialist | LIXI2 CAL schema, JPA entities, explicit Java mappers, edge cases | Opus |
| **t3-gateway** | Gateway API specialist | Spring MVC, REST controllers, request/response design | Sonnet |
| **t3-store** | Message store specialist | JSONB storage, audit trail, round-trip fidelity | Sonnet |

**Deliverables**:

| # | Deliverable | Tech Design Ref | Agent | Week |
|---|------------|-----------------|-------|------|
| G1 | `LixiCalMapper` — 25 primary mappings (CAL → domain entities) | §5 Primary Mappings | t3-mapper | 3–5 |
| G2 | `LixiCalMapper` — reverse mappings (domain → CAL) for emit | §7 Emit Flow | t3-mapper | 5–6 |
| G3 | Edge case handling (20 income types → 12, address normalization, TFN encryption, multi-facility) | §5 Edge Cases | t3-mapper | 4–6 |
| G4 | `LixiGatewayController` — `POST /ingest`, `GET /emit/{id}` | §7 Ingest & Emit | t3-gateway | 4–5 |
| G5 | `LixiMessageService` — store inbound/outbound in `lixi_messages`, link to application | §5 lixi_messages | t3-store | 3–4 |
| G6 | Ingest pipeline (receive → validate → transform → persist → respond → notify) | §7 Processing Pipeline | t3-gateway | 5–6 |
| G7 | 50+ unit tests for mapping (positive, negative, edge cases) + 10 integration tests for ingest flow | §17 Phase 1 | t3-mapper, t3-gateway | 3–6 |

**Dependencies**: T1 (entities, DB), T2 (validation pipeline called during ingest).
**Blocks**: T4 (assessment needs persisted applications), T7 (events published after ingest), T8 (broker UI submits to gateway).

---

### Team 4: Assessment Engine

**Mandate**: Build the serviceability calculator (HEM, income shading, assessment rate) and decision engine integration. Wire up SVC and CDA adapters for external provider passthrough.

**Phase alignment**: Phase 2 (Week 7–10).

| Agent | Role | Skills | Model |
|-------|------|--------|-------|
| **t4-lead** | Team lead | AU lending regulations, serviceability rules, APRA guidelines | Opus |
| **t4-svc** | Serviceability specialist | HEM tables, income shading, DSR/UMI calculation, SVC adapter | Sonnet |
| **t4-cda** | Credit decisioning specialist | CDA adapter, credit bureau integration, fraud checks, KYC | Sonnet |
| **t4-decision** | Decision engine specialist | Policy rules, risk bands, auto-approve/refer/decline logic | Sonnet |

**Deliverables**:

| # | Deliverable | Tech Design Ref | Agent | Week |
|---|------------|-----------------|-------|------|
| A1 | `HemLookupService` — ABS HEM data loader, quarterly update, postcode/household lookup | §8 Internal Calculator | t4-svc | 7 |
| A2 | `EnhancedServiceabilityCalculator` — assessment rate (+3%, floor 5.50%), HEM floor, income shading, household breakdown | §8 Internal Calculator | t4-svc | 7–8 |
| A3 | `LixiSvcMapper` — request/response mapping (domain ↔ SVC) | §8 External Provider | t4-svc | 8–9 |
| A4 | `ExternalSvcProvider` — passthrough to external SVC provider per lender config | §8 External Provider | t4-svc | 9 |
| A5 | `LixiCdaMapper` — request/response mapping (domain ↔ CDA) | §9 CDA Mapping | t4-cda | 7–8 |
| A6 | `CreditBureauClient` interface + `StubCreditBureauClient` (configurable test scores) | §9 Credit Bureau | t4-cda | 8 |
| A7 | Decision engine integration — CDA results feed into existing `DecisionEngine` | §9 Flow | t4-decision | 9–10 |
| A8 | 30+ unit tests for SVC/CDA mapping + integration tests with Testcontainers | §17 Phase 2 | all | 7–10 |

**Dependencies**: T1 (entities), T3 (persisted applications to assess), T7 (lender config for SVC routing).
**Blocks**: T8 (assessment dashboard UI).

---

### Team 5: Migration Pipeline

**Mandate**: Build the 6-stage batch pipeline: parse CSV → auto-map columns → transform types → validate at scale → classify → store in staging. Handle 50,000+ rows with streaming and backpressure.

**Phase alignment**: Phase 1 (Week 3–6).

| Agent | Role | Skills | Model |
|-------|------|--------|-------|
| **t5-lead** | Team lead | ETL architecture, batch processing, streaming | Opus |
| **t5-parser** | Parser specialist | CSV/Excel parsing, type detection, encoding, BOM handling | Sonnet |
| **t5-mapper** | Auto-mapper specialist | Fuzzy matching (Levenshtein), synonym dictionaries, type compatibility | Opus |
| **t5-transform** | Transform specialist | Type conversions (dates, rates, amounts, addresses, names), normalization | Sonnet |

**Deliverables**:

| # | Deliverable | Tech Design Ref | Agent | Week |
|---|------------|-----------------|-------|------|
| M1 | `MigrationIngestService` — CSV/Excel parse with type detection, BOM handling, 500k row limit | §13.2 Stage 1 | t5-parser | 3 |
| M2 | `ColumnAutoMapper` — 500+ AU lender synonym dictionary, fuzzy matching, sample value analysis, confidence scoring | §13.3 Stage 2 | t5-mapper | 3–5 |
| M3 | `MigrationTransformService` — 6 field transformers (date, rate, amount, address, name, boolean), transform logging | §13.4 Stage 3 | t5-transform | 4–5 |
| M4 | `BatchValidationService` — parallel validation with pre-loaded schemas, per-row classification, rule failure aggregation | §13.5 Stage 4-5 | t5-lead | 5–6 |
| M5 | `MigrationStagingRepository` — bulk insert, partitioned by job_id, classification indexes | §13.10 staging table | t5-parser | 4 |
| M6 | Pipeline orchestrator — compose stages, progress tracking, pause/resume/retry, streaming with backpressure | §13.1 Pipeline Architecture | t5-lead | 5–6 |
| M7 | Migration job API: `POST /jobs` (upload), `GET /jobs/{id}` (status), `GET /mappings`, `PUT /mappings`, `POST /transform`, `POST /validate` | §13.11 APIs | t5-lead | 5–6 |
| M8 | 30+ tests (parser, auto-mapper accuracy, transform correctness, batch validation throughput) | §17 Phase 1 | all | 3–6 |

**Dependencies**: T1 (migration tables, seed CSV), T2 (validation pipeline for batch use).
**Blocks**: T6 (quality engine runs on validated staging data), T9 (migration workspace UI).

---

### Team 6: Quality, Remediation & Reconciliation

**Mandate**: Build the quality aggregation engine (5 dimensions), remediation engine (bulk transforms with audit trail), and reconciliation engine (record/financial/field-level proof). This is what sells migration to CTOs.

**Phase alignment**: Phase 1 quality (Week 5–6), Phase 2 remediation + recon (Week 7–10).

| Agent | Role | Skills | Model |
|-------|------|--------|-------|
| **t6-lead** | Team lead | Data quality frameworks, audit trail design, reconciliation | Opus |
| **t6-quality** | Quality engine specialist | 5-dimension scoring, field coverage calculation, rule failure aggregation | Sonnet |
| **t6-remediation** | Remediation specialist | Bulk transforms, conditional logic, preview/apply cycle, before/after snapshots | Opus |
| **t6-recon** | Reconciliation specialist | Record-level counts, financial aggregates (to the cent), field-level transform tracking, PDF generation | Sonnet |

**Deliverables**:

| # | Deliverable | Tech Design Ref | Agent | Week |
|---|------------|-----------------|-------|------|
| Q1 | `QualityAggregationService` — 5-dimension scoring (completeness, accuracy, consistency, validity, uniqueness), weighted composite | §13.6 | t6-quality | 5–6 |
| Q2 | Field coverage engine — per-field population % across entire portfolio, ranked heatmap data | §13.6 fieldCoverage | t6-quality | 5–6 |
| Q3 | Top rule failures — aggregate `ruleId → count` across portfolio, sorted by frequency | §13.6 topFailures | t6-quality | 5–6 |
| Q4 | Quality API: `GET /quality`, `GET /quality/coverage`, `GET /quality/failures`, `GET /quality/failures/{ruleId}` | §13.11 | t6-quality | 6 |
| Q5 | `RemediationService` — preview impact, apply bulk transforms, re-validate, log everything | §13.7 | t6-remediation | 7–8 |
| Q6 | Remediation data model (`RemediationAction`, `RowCondition`, `BulkTransform`, `FieldTransform`) | §13.7 Models | t6-remediation | 7 |
| Q7 | Pattern analysis — for a given rule failure set: distribution by LVR band, origination date, lender | Demo Scene M3 | t6-remediation | 8–9 |
| Q8 | `ReconciliationService` — record-level, financial, field-level comparison, compliance summary | §13.8 | t6-recon | 8–9 |
| Q9 | `LifecyclePositionResolver` — map legacy status → state machine position, `MIGRATION_IMPORT` trigger | §13.9 | t6-recon | 9 |
| Q10 | Promotion pipeline — move clean+warning rows from staging to `loan_applications` | §13.9 | t6-recon | 9–10 |
| Q11 | Remediation + reconciliation APIs: `/remediate/preview`, `/remediate/apply`, `/remediation-log`, `/reconciliation`, `/promote` | §13.11 | t6-lead | 9–10 |
| Q12 | 40+ tests (quality scoring, remediation apply/rollback, reconciliation accuracy, promotion) | §17 Phase 2 | all | 5–10 |

**Dependencies**: T1 (remediation tables), T5 (validated staging data to aggregate).
**Blocks**: T9 (quality dashboard, drill-down, reconciliation pages).

---

### Team 7: Configuration & Events

**Mandate**: Build the lender configuration store (profiles, EGB import), event store (append-only domain events), and notification channels (SSE, webhooks, LIXI2 status callbacks). Also PDF report generation.

**Phase alignment**: Phase 3 (Week 11–14).

| Agent | Role | Skills | Model |
|-------|------|--------|-------|
| **t7-lead** | Team lead | Event-sourced architecture, configuration management | Opus |
| **t7-config** | Lender config specialist | CRUD APIs, EGB 2.0.0 XML parsing, SpEL conditions, product rules | Sonnet |
| **t7-events** | Event infrastructure specialist | Append-only event store, Spring ApplicationEvents, SSE (SseEmitter), webhook dispatch | Sonnet |
| **t7-reports** | Report generation specialist | Thymeleaf templates, Flying Saucer PDF, NDJSON export, branded layouts | Sonnet |

**Deliverables**:

| # | Deliverable | Tech Design Ref | Agent | Week |
|---|------------|-----------------|-------|------|
| C1 | `LenderProfile` entity + `LenderProfileRepository` + CRUD API (`/api/v1/config/lenders/*`) | §11 Lender Config | t7-config | 11 |
| C2 | `EgbImporter` — parse EGB 2.0.0 XML into `LenderProfile` format | §11 EGB Import | t7-config | 12 |
| C3 | Lender product rules (`ProductRule` — maxLvr, min/maxLoanAmount, LMI threshold, allowed purposes) | §11 ProductRule | t7-config | 11–12 |
| C4 | Pre-built lender profiles: CBA, WBC, ANZ (different required fields for demo) | Demo Scene 4 | t7-config | 12 |
| C5 | `EventStore` — append-only `domain_events`, Spring event bridge, `getEvents(aggregateId)` | §10 Event Store | t7-events | 11–12 |
| C6 | `EventStreamController` — SSE endpoint `/api/v1/events/stream`, application/subscriber filtering | §10 SSE | t7-events | 12–13 |
| C7 | `WebhookDispatcher` — async delivery, HMAC-SHA256 signing, 3x backoff retry | §10 Webhooks | t7-events | 12–13 |
| C8 | LIXI2 status callback — emit CAL status message on state transition back to originating aggregator | §10 LIXI2 Status | t7-events | 13–14 |
| C9 | PDF reconciliation report — Thymeleaf template, branded, auditor-ready | §13.8, Demo M5 | t7-reports | 13 |
| C10 | LIXI2 bulk export — promoted loans as NDJSON stream of CAL messages | §13.11 export/lixi | t7-reports | 13–14 |
| C11 | 30+ tests (lender CRUD, EGB import, event store, SSE connectivity, webhook delivery, PDF output) | §17 Phase 3 | all | 11–14 |

**Dependencies**: T1 (tables), T3 (events published by gateway), T6 (reconciliation data for PDF).
**Blocks**: T8 (lender config UI, notification panel), T10 (SDKs wrap these APIs).

---

### Team 8: Frontend — Origination

**Mandate**: Build the origination demo experience in labs-platform (Next.js). 6 scenes from Track A: broker submit → lifecycle → assessment → lender config → real-time events → audit trail.

**Phase alignment**: Phase 1 broker UI (Week 3–6), Phase 2 assessment UI (Week 7–10), Phase 3 events UI (Week 11–14).

| Agent | Role | Skills | Model |
|-------|------|--------|-------|
| **t8-lead** | Team lead | Next.js architecture, Atheryon design system, Fluent UI, page routing | Opus |
| **t8-broker** | Broker submit UI specialist | Form design, LIXI2 paste/upload, validation result display, mapped preview card | Sonnet |
| **t8-assess** | Assessment dashboard specialist | Gauge components (SVC result), decision outcome display, risk band indicators | Sonnet |
| **t8-realtime** | Real-time UI specialist | SSE client (EventSource), notification panel, event feed, lender config switcher | Sonnet |

**Deliverables**:

| # | Deliverable | Demo Scene | Agent | Week |
|---|------------|------------|-------|------|
| U1 | Enhanced validate page — lender selector dropdown (CBA/WBC/ANZ), Tier 3 switches per lender | Scene 1 | t8-broker | 3–4 |
| U2 | Mapped application preview — below validation results, show borrower/property/loan as human-readable card (not raw JSON) | Scene 1 | t8-broker | 4–5 |
| U3 | "Submit to Pipeline" button — create application from validated LIXI2, navigate to explorer | Scene 1→2 | t8-broker | 5–6 |
| U4 | Assessment dashboard page — SVC result gauges (UMI, DSR, NDI), HEM indicator, assessment rate display | Scene 3 | t8-assess | 7–8 |
| U5 | Decision outcome display — APPROVED/REFERRED/DECLINED with conditions, credit score (when available) | Scene 3 | t8-assess | 8–9 |
| U6 | Lender config UI — CRUD interface for lender profiles, field requirements editor, product rules table | Scene 4 | t8-realtime | 11–12 |
| U7 | Lender comparison — side-by-side view showing how different lender configs affect the same application | Scene 4 | t8-realtime | 12 |
| U8 | SSE notification panel — real-time event feed in sidebar, auto-scroll, event type badges | Scene 5 | t8-realtime | 12–13 |
| U9 | Audit trail page — full event history for an application, filterable by type, expandable detail | Scene 6 | t8-realtime | 13–14 |

**Dependencies**: T2 (validation API), T3 (ingest API), T4 (assessment API), T7 (config API, SSE endpoint).
**Blocks**: T10 (SDK examples reference these UIs).

---

### Team 9: Frontend — Migration

**Mandate**: Build the migration demo experience in labs-platform (Next.js). 5 scenes from Track B: upload CSV → quality dashboard → drill-down → remediation → reconciliation.

**Phase alignment**: Phase 1 workspace + quality (Week 3–6), Phase 2 drill-down + recon (Week 7–10).

| Agent | Role | Skills | Model |
|-------|------|--------|-------|
| **t9-lead** | Team lead | Dashboard design, data visualization, Recharts, large dataset UX | Opus |
| **t9-workspace** | Migration workspace specialist | File upload (drag-and-drop), pipeline progress animation, field mapping confirmation UI | Sonnet |
| **t9-quality** | Quality dashboard specialist | 5-dimension gauges, classification stacked bars, field coverage heatmap, top failures table | Sonnet |
| **t9-recon** | Drill-down + reconciliation specialist | Failure detail page, pattern analysis charts, before/after comparison, reconciliation display, PDF download | Sonnet |

**Deliverables**:

| # | Deliverable | Demo Scene | Agent | Week |
|---|------------|------------|-------|------|
| W1 | Migration workspace page (`/mortgages/migrate`) — drag-and-drop CSV upload, file info display | Scene M1 | t9-workspace | 3–4 |
| W2 | Pipeline progress component — animated stage bars (ingest/parse/map/validate/classify), rows/sec, ETA | Scene M1 | t9-workspace | 4–5 |
| W3 | Field mapping confirmation — auto-detected mappings table, confidence %, edit/confirm/reject per column, unmapped column flags | Scene M1 | t9-workspace | 5–6 |
| W4 | Quality dashboard page (`/mortgages/migrate/quality`) — 5-dimension gauges with composite score | Scene M2 | t9-quality | 4–5 |
| W5 | Classification summary — clean/warning/failed bar with counts and percentages | Scene M2 | t9-quality | 5 |
| W6 | Field coverage heatmap — ranked list of LIXI2 fields with population % bars, status icons | Scene M2 | t9-quality | 5–6 |
| W7 | Top failures table — rule failures ranked by frequency, clickable to drill-down | Scene M2 | t9-quality | 6 |
| W8 | Failure drill-down page (`/mortgages/migrate/failures/{ruleId}`) — pattern analysis (LVR distribution, origination date histogram), sample loans table | Scene M3 | t9-recon | 7–8 |
| W9 | Remediation options UI — radio selection (bulk fix / conditional / export), "Preview Impact" + "Apply Fix" buttons | Scene M3 | t9-recon | 8–9 |
| W10 | Before/after comparison — side-by-side quality metrics, classification counts, quality delta display | Scene M4 | t9-recon | 9 |
| W11 | Remediation audit log — timestamped log entries with who/when/what/condition | Scene M4 | t9-recon | 9 |
| W12 | Reconciliation page (`/mortgages/migrate/reconciliation`) — record-level, financial, field-level, compliance sections | Scene M5 | t9-recon | 9–10 |
| W13 | PDF download button — triggers server-side PDF generation, downloads branded reconciliation report | Scene M5 | t9-recon | 10 |

**Dependencies**: T5 (migration pipeline APIs), T6 (quality/remediation/reconciliation APIs).
**Blocks**: None (end of chain).

---

### Team 10: SDK & Developer Experience

**Mandate**: Build TypeScript and Python SDKs, auto-generate LIXI2 types from JSON Schema, publish OpenAPI 3.1 spec, write sample applications and API documentation.

**Phase alignment**: Phase 5 (Week 19–22).

| Agent | Role | Skills | Model |
|-------|------|--------|-------|
| **t10-lead** | Team lead | API design, developer experience, documentation strategy | Opus |
| **t10-ts** | TypeScript SDK specialist | TypeScript, fetch API, EventSource (SSE), json-schema-to-typescript, npm publishing | Sonnet |
| **t10-py** | Python SDK specialist | Python 3.11+, httpx, async, type hints, PyPI publishing | Sonnet |
| **t10-docs** | Documentation specialist | OpenAPI 3.1, Springdoc, documentation site, usage examples, sample applications | Haiku |

**Deliverables**:

| # | Deliverable | Tech Design Ref | Agent | Week |
|---|------------|-----------------|-------|------|
| S1 | TypeScript SDK (`@atheryon/mortgage-sdk`) — client, resources (applications, serviceability, decisions, validation, events, migration), typed errors | §14 SDK Design | t10-ts | 19–20 |
| S2 | Auto-generated LIXI2 CAL types (`lixi-cal-2.6.91.d.ts`) from JSON Schema | §14 Type Generation | t10-ts | 19 |
| S3 | SSE subscription wrapper (typed event stream with reconnection) | §14 Events | t10-ts | 20 |
| S4 | Migration SDK methods (upload, checkStatus, getMappings, confirmMappings, getQuality, remediate, reconcile) | §13.11 | t10-ts | 20–21 |
| S5 | Python SDK (`atheryon-mortgage`) — sync + async clients, core operations, batch migration methods | §14 SDK Architecture | t10-py | 19–21 |
| S6 | OpenAPI 3.1 spec — auto-generated from Spring Boot controllers via Springdoc | §17 Phase 5 | t10-docs | 19 |
| S7 | API documentation site — built from OpenAPI, usage examples, authentication guide | §17 Phase 5 | t10-docs | 20–21 |
| S8 | Sample: TypeScript broker submission flow (paste LIXI2 → validate → ingest → subscribe to events) | §14 Examples | t10-ts | 21–22 |
| S9 | Sample: Python batch migration (upload CSV → confirm mappings → quality check → remediate → export) | §14 Examples | t10-py | 21–22 |
| S10 | SDK integration tests against dev environment | §17 Phase 5 | all | 21–22 |

**Dependencies**: T2–T7 (all backend APIs must be stable), T8–T9 (reference UIs for examples).
**Blocks**: None (final deliverable).

---

## 3. Dependency Graph

```
Week:   1    2    3    4    5    6    7    8    9    10   11   12   13   14   15   16   17   18   19   20   21   22   23   24
        ┃    ┃    ┃    ┃    ┃    ┃    ┃    ┃    ┃    ┃    ┃    ┃    ┃    ┃    ┃    ┃    ┃    ┃    ┃    ┃    ┃    ┃    ┃    ┃

T1 ████████──┘    │    │    │    │    │    │    │    │    │    │    │    │    │    │    │    │    │    │    │    │    │    │
   Foundation     │    │    │    │    │    │    │    │    │    │    │    │    │    │    │    │    │    │    │    │    │    │
        │         │    │    │    │    │    │    │    │    │    │    │    │    │    │    │    │    │    │    │    │    │    │
        ├────────►T2 ████████████████──┘    │    │    │    │    │    │    │    │    │    │    │    │    │    │    │    │
        │         Validation Pipeline       │    │    │    │    │    │    │    │    │    │    │    │    │    │    │    │
        │              │                    │    │    │    │    │    │    │    │    │    │    │    │    │    │    │    │
        ├────────►T3 ████████████████──┘    │    │    │    │    │    │    │    │    │    │    │    │    │    │    │    │
        │         Gateway & Mappers   │     │    │    │    │    │    │    │    │    │    │    │    │    │    │    │    │
        │              │              │     │    │    │    │    │    │    │    │    │    │    │    │    │    │    │    │
        │              └──────────────┼────►T4 ████████████████──┘    │    │    │    │    │    │    │    │    │    │
        │                             │     Assessment Engine        │    │    │    │    │    │    │    │    │    │
        │                             │          │                   │    │    │    │    │    │    │    │    │    │
        ├────────►T5 ████████████████─┘          │                   │    │    │    │    │    │    │    │    │    │
        │         Migration Pipeline             │                   │    │    │    │    │    │    │    │    │    │
        │              │                         │                   │    │    │    │    │    │    │    │    │    │
        │              └────►T6 ██████████████████████████──┘        │    │    │    │    │    │    │    │    │    │
        │                    Quality & Recon               │        │    │    │    │    │    │    │    │    │    │
        │                         │                        │        │    │    │    │    │    │    │    │    │    │
        │                         │                        └───────►T7 ████████████████──┘    │    │    │    │
        │                         │                                 Config & Events          │    │    │    │
        │                         │                                      │                   │    │    │    │
        ├─────────────────────────┼──────────────────────────────────────┼───►T8 ████████████████████████──┘
        │                         │                                      │   Frontend-Orig (spans 3-14)
        │                         │                                      │                   │    │    │
        ├─────────────────────────┼──────────────────────────►T9 ████████████████──┘         │    │    │
        │                         │                           Frontend-Migr (spans 3-10)     │    │    │
        │                         │                                                          │    │    │
        └─────────────────────────┴──────────────────────────────────────────────────────────►T10 ████████
                                                                                             SDK (19-22)

        ▲ Week 6: DEMO MILESTONE          ▲ Week 10: POLISHED            ▲ Week 14: AUDIT-READY
```

### Critical Path

```
T1 (Foundation) → T2 (Validation) → T5 (Migration Pipeline) → T6 (Quality) → T9 (Migration UI)
                                   → T3 (Gateway)            → T4 (Assessment) → T8 (Origination UI)
```

Two parallel critical paths from T2 onwards — one for each demo track. Both must reach demo-able state by week 6.

---

## 4. Phase Milestones & Demo Gates

### Week 6: First Demo Gate (Both Tracks)

**Origination** can show:
- Paste LIXI2 → 4-tier validation in <100ms (T2, T8-U1)
- Mapped preview card: borrower, property, loan extracted (T3, T8-U2)
- Submit to pipeline → application created (T3, T8-U3)
- State machine walkthrough (existing explorer — already deployed)

**Migration** can show:
- Upload 10,000-loan CSV → pipeline processes at 1,800 rows/sec (T5, T9-W1/W2)
- Auto-detected column mappings with confidence scores (T5, T9-W3)
- Quality dashboard: 5-dimension gauges, classification bar (T6, T9-W4/W5)
- Field coverage heatmap: ranked list of population % (T6, T9-W6)
- Top rule failures with counts (T6, T9-W7)

**Gate criteria**: Both tracks demoed to internal stakeholders. Fix showstoppers in week 7.

### Week 10: Polished Demo Gate

**Origination adds**:
- Assessment dashboard with SVC gauges (T4, T8-U4)
- Decision outcome display (T4, T8-U5)

**Migration adds**:
- Failure drill-down: pattern analysis, sample loans (T6, T9-W8)
- Remediation: preview impact, apply fix, before/after quality delta (T6, T9-W9/W10/W11)
- Reconciliation: record-level, financial, field-level (T6, T9-W12)
- PDF download for auditors (T9-W13)

**Gate criteria**: Full demo to first external prospect. Both tracks 20 minutes.

### Week 14: Audit-Ready Gate

**Origination adds**:
- Lender config UI (switch lender, see validation change) (T7, T8-U6/U7)
- Real-time SSE notifications (T7, T8-U8)
- Full audit trail (T7, T8-U9)

**Migration adds**:
- PDF reconciliation report (branded, auditor-ready) (T7-C9)
- LIXI2 bulk export (NDJSON stream) (T7-C10)

**Gate criteria**: Migration demo includes PDF handoff. "Here's the report for your auditor."

### Week 22: SDK Gate

- TypeScript + Python SDKs published (T10)
- Sample applications working (T10)
- OpenAPI documentation site live (T10)

### Week 24: Production Gate

- Security audit complete
- Performance targets met (50 msg/sec ingest, 2,000 rows/sec migration)
- Production deployment at `mortgages.atheryon.ai`

---

## 5. Cross-Team Coordination

### Shared Contracts

Teams communicate through **interface contracts** defined in Phase 0:

| Contract | Owner | Consumers | Format |
|----------|-------|-----------|--------|
| `ValidationResult` | T2 | T3, T5, T6, T8, T9 | Java record in `core` module |
| `LoanApplication` (entity) | T1 | T3, T4, T6, T7, T8 | JPA entity in `core` module |
| `MigrationJob` / staging schema | T1 | T5, T6, T9 | JPA entity + Flyway migration |
| `DomainEvent` sealed interface | T7 | T3, T4, T6 | Java sealed interface in `core` |
| `LenderProfile` | T7 | T2 (Tier 3), T4 (SVC routing), T8 | JPA entity in `config` module |
| REST API paths | Each team | Frontend teams (T8, T9), SDK team (T10) | OpenAPI spec (auto-generated) |

### Integration Protocol

1. **Contract-first**: Backend teams publish Java interfaces and OpenAPI fragments in week 2 (T1 coordinates).
2. **Stub-first**: Frontend teams (T8, T9) build against mocked API responses from week 3. Backend teams provide Wiremock stubs.
3. **Integration checkpoints**: Week 4 (T2+T3 validate→ingest), Week 6 (T5+T6 pipeline→quality), Week 8 (T4+T8 assessment→dashboard), Week 10 (T6+T9 recon→UI).
4. **Daily sync**: Programme lead reviews all team statuses. Cross-team blockers escalated within 4 hours.

### Shared Code Ownership

| Package | Primary Owner | Secondary |
|---------|--------------|-----------|
| `com.atheryon.mortgages.core.entity` | T1 → T3 | All teams read |
| `com.atheryon.mortgages.core.event` | T7 | T3, T4, T6 publish events |
| `com.atheryon.mortgages.lixi.schema` | T2 | T5 (batch use) |
| `com.atheryon.mortgages.lixi.mapper` | T3 | T5 (migration transform) |
| `com.atheryon.mortgages.migration.*` | T5, T6 | T9 consumes APIs |
| `src/app/(mortgages)/mortgages/*` | T8, T9 | T10 references |

---

## 6. Agent Specifications

### Model Selection Strategy

| Task Type | Model | Rationale |
|-----------|-------|-----------|
| Team leads (architecture, coordination, code review) | **Opus** | Complex reasoning, cross-team context |
| Core mapping (LIXI2 ↔ domain, CAL mapper, auto-mapper) | **Opus** | Domain complexity, edge cases, fidelity-critical |
| Standard implementation (services, controllers, components) | **Sonnet** | Good quality at lower cost, faster iteration |
| Seed data, boilerplate, docs | **Haiku** | Mechanical tasks, high volume, low complexity |

### Agent Naming Convention

```
t{team#}-{role}
```

Examples: `t1-lead`, `t2-schema`, `t3-mapper`, `t5-parser`, `t9-quality`

### Agent Working Style

Each agent operates in a **git worktree** for isolation:

```bash
# Agent t3-mapper starts work
cd ~/repos/mortgages-platform && git pull
git worktree add ../mortgages-wt-t3-mapper -b feat/cal-mapper
cd ../mortgages-wt-t3-mapper
# ... work, commit, push ...
# Team lead t3-lead reviews, merges to main
```

Frontend agents work in labs-platform worktrees:

```bash
cd ~/repos/labs-platform && git pull
git worktree add ../labs-wt-t9-quality -b feat/migration-quality-dashboard
cd ../labs-wt-t9-quality
```

### Agent Coordination Within Teams

| Signal | Mechanism |
|--------|-----------|
| "I'm done with my deliverable" | `TaskUpdate` → status: completed, message to team lead |
| "I'm blocked on another team" | `SendMessage` to programme lead, create blocking task |
| "Merge conflict with teammate" | Team lead resolves (owns main branch merges) |
| "API contract changed" | Publishing team broadcasts to all consumers |
| "Tests failing after merge" | Team lead rolls back, notifies affected agents |

---

## 7. Risk Mitigation

### Team-Level Risks

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| T3 CAL mapper takes longer than 4 weeks (700 elements) | High | Critical | Prioritize 80 most-used elements (covers 95% of residential). Add remaining incrementally. |
| T2 Schematron performance (1,600 rules) blocks pipeline | Medium | High | Benchmark in week 3. If >500ms, switch to compiled Saxon XSLT. |
| T5 auto-mapper accuracy below 80% | Medium | High | Synonym dictionary covers known AU lenders. Fallback: pre-map seed CSV manually for demo, improve mapper post-demo. |
| T8/T9 build against unstable backend APIs | High | Medium | Stub-first protocol. Frontend teams maintain Wiremock stubs updated weekly. |
| T7 SSE connectivity issues in Azure Container Apps | Medium | Medium | Test SSE through Azure ingress in week 11. Fallback: polling with 2-second interval. |
| Cross-team merge conflicts on shared entities | High | Low | T1 lead owns `core.entity` package. All entity changes go through T1-lead review. |
| Week 6 demo gate not met | Medium | Critical | Week 5 is "demo freeze" — no new features, only bug fixes and polish. |

### Escalation Protocol

```
Agent blocked → Team lead (4 hours)
Team blocked → Programme lead (same day)
Cross-team conflict → Programme lead mediates (4 hours)
Demo gate at risk → All hands on blocking items (immediate)
```

---

## 8. Test Strategy

### Test Ownership

| Test Type | Owner | Count Target | When |
|-----------|-------|-------------|------|
| Unit tests (services, mappers, calculators) | Each team | 200+ total | Continuous |
| Integration tests (API + DB, Testcontainers) | Each team | 60+ total | Per deliverable |
| E2E tests (Playwright, UI flows) | T8, T9 leads | 30+ total | After each milestone |
| Performance tests (throughput, latency) | T2-lead (ingest), T5-lead (batch) | 10 benchmarks | Week 22–24 |
| Security tests (PII handling, auth) | Programme lead | 10 | Week 23–24 |

### Shared Test Infrastructure

- **Testcontainers base class** (T1-F6): PostgreSQL + schema pre-loaded. All integration tests extend this.
- **LIXI2 test fixtures** (T1-F7): 6 sample CAL messages (valid, partial, invalid). Used by T2, T3, T5.
- **Migration test CSV** (T1-F8): 10,000 rows with known issues. Used by T5, T6, T9.
- **Wiremock stubs**: Each backend team publishes stubs for their APIs. Frontend teams consume.

### CI/CD Integration

```
PR opened → GitHub Actions
    ├── Unit tests (all teams, 2 min)
    ├── Integration tests (Testcontainers, 5 min)
    ├── Lint + type check (frontend, 1 min)
    └── Build Docker image (verify it builds, 3 min)

Merge to main → Deploy pipeline
    ├── Build + push to ACR
    ├── Run Flyway migrations
    ├── Deploy to ca-mortgages-dev
    ├── Health check
    └── Smoke test (validate endpoint + migration upload)
```

---

## 9. Resource Summary

### Agent Hours by Phase

| Phase | Weeks | Teams Active | Agents Active | Focus |
|-------|-------|-------------|---------------|-------|
| 0: Foundation | 1–2 | T1 | 4 | Skeleton, DB, schemas, seed data |
| 1: MVP | 3–6 | T1, T2, T3, T5, T6, T8, T9 | 28 | Validation, gateway, migration pipeline, quality, UIs |
| 2: Polish | 7–10 | T4, T6, T8, T9 | 16 | Assessment, remediation, reconciliation, UIs |
| 3: Config/Events | 11–14 | T7, T8 | 8 | Lender config, events, SSE, PDF reports |
| 4: Documents | 15–18 | (subset of T7) | 4 | DAS adapter, e-signing, settlement stub |
| 5: SDK | 19–22 | T10 | 4 | TypeScript + Python SDKs, docs |
| 6: Hardening | 23–24 | All leads | 10 | Security, performance, production |

### Peak Concurrency

**Week 3–6** is the peak: 7 teams, 28 agents running in parallel. This is when both demo tracks are being built simultaneously. After week 6, teams ramp down as deliverables complete.

```
Agents:  4   4   28  28  28  28  16  16  16  16  8   8   8   8   4   4   4   4   4   4   4   4   10  10
Week:    1   2   3   4   5   6   7   8   9   10  11  12  13  14  15  16  17  18  19  20  21  22  23  24
              ▲                   ▲               ▲
              Foundation done     Demo gate       Audit-ready
```

---

## 10. Deliverable Tracking

### Master Deliverable List (by ID)

| ID | Team | Deliverable | Week | Status |
|----|------|-------------|------|--------|
| F1–F8 | T1 | Foundation (8 items) | 1–2 | Pending |
| V1–V7 | T2 | Validation Pipeline (7 items) | 3–6 | Pending |
| G1–G7 | T3 | Gateway & Mappers (7 items) | 3–6 | Pending |
| A1–A8 | T4 | Assessment Engine (8 items) | 7–10 | Pending |
| M1–M8 | T5 | Migration Pipeline (8 items) | 3–6 | Pending |
| Q1–Q12 | T6 | Quality & Recon (12 items) | 5–10 | Pending |
| C1–C11 | T7 | Config & Events (11 items) | 11–14 | Pending |
| U1–U9 | T8 | Frontend Origination (9 items) | 3–14 | Pending |
| W1–W13 | T9 | Frontend Migration (13 items) | 3–10 | Pending |
| S1–S10 | T10 | SDK & DevEx (10 items) | 19–22 | Pending |
| **Total** | | **96 deliverables** | | |

### Weekly Burn-Down Targets

| Week | Deliverables Due | Cumulative | % |
|------|-----------------|------------|---|
| 2 | F1–F8 (8) | 8 | 8% |
| 4 | V1, V2, G1, G5, M1, M2, M5, W1, W4 (9) | 17 | 18% |
| 6 | V3–V7, G2–G7, M3–M8, Q1–Q4, U1–U3, W2–W7 (25) | 42 | 44% |
| 8 | A1–A5, Q5–Q7, U4, W8 (9) | 51 | 53% |
| 10 | A6–A8, Q8–Q12, U5, W9–W13 (14) | 65 | 68% |
| 14 | C1–C11, U6–U9 (15) | 80 | 83% |
| 18 | (Phase 4 — DAS, documents) (6) | 86 | 90% |
| 22 | S1–S10 (10) | 96 | 100% |

---

*End of Delivery Plan*
