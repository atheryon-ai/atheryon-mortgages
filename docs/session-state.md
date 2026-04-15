# Session State — LIXI2 Architecture Work

**Date**: 15 April 2026
**Purpose**: Resume context after compaction. Read this file first.

## What Was Done (in order)

### 1. State Explorer UX Overhaul
- Implemented 8-batch plan in `~/repos/labs-platform` (frontend only)
- Transport bar with phase-grouped numbered dots, welcome screen phase cards, completion banner, sub-step dots on state diagram, business mode language, error retry, panel containment, mobile tab fix, deleted dead Stepper component
- Fixed pill sizing (20px→28px) and layout jump (removed transform:scale)
- Deployed to dev.atheryon.ai via GitHub Actions
- Commits: `1aeebc07`, `2d747992`

### 2. LIXI2 Spec Critique
- Reviewed `grok_report-2.pdf` (LIXI2 AU Lending Extension v1.0)
- 6 pillars: Validator, EGB Engine, SVC Profiles, Backchannel, Packaging, SDKs
- Critique: fantasy 90-day schedule, stale EGB spec (2016), no user journeys, unsubstantiated reuse claims

### 3. Gap Analysis
- Ran 3 parallel explore agents against labs-platform, mortgages-platform, cdm-platform
- Mapped each of 6 spec pillars against existing capability
- Findings: Validator ~60%, EGB ~15%, SVC ~35%, Backchannel ~15%, Packaging ~10%, SDKs ~25%
- Realistic estimate: 20-30 weeks, not 13

### 4. LIXI2 Standard Deep-Dive
- Fetched lixi.org.au — all 15 standards, tools, licensing, governance
- Key finding: LIXI2 is a MESSAGE FORMAT, not a platform. No orchestration, no state machine, no API spec, no real-time events, no calculation logic.
- CAL (v2.6.91), SVC (v2.0.75), CDA (v2.0.86), DAS (v2.2.90) are production-grade
- EGB (v2.0.0, 2016) is dead — biggest risk for the spec
- 1,600 Schematron rules + 4,000 test XMLs are real validation IP
- JSON is first-class (every schema ships XML + JSON Draft 7)
- Licensing: $985-$9,025/yr per standard, dev license free 6 months

### 5. Tech Design Written
- File: `docs/tech-design-lixi2-extension.md`
- Architecture: LIXI2 as I/O adapter, CDM canonical model, modular monolith
- 5 modules: LIXI, Core, Integration, Notification, Config
- 4-tier validation: Schema → Schematron → Lender → Domain
- Detailed LIXI2 ↔ internal model mapping (25 primary mappings)
- New DB tables: lixi_messages, domain_events, lender_profiles, webhook_subscriptions
- SVC adapter (internal calc + external passthrough)
- CDA adapter (credit bureau integration)
- Event architecture (SSE, webhooks, LIXI2 status callbacks)
- SDK design (TypeScript primary, Python secondary)
- Security, deployment, 24-week phasing, 10 risks
- **GAP**: No migration architecture (batch pipeline, quality at scale, remediation, reconciliation)

### 6. Demo Playbook Written
- File: `docs/demo-playbook.md`
- Two tracks:
  - **Track A (Origination)**: 6 scenes — Sarah Mitchell's first home. Paste LIXI2 → validate → lifecycle → assessment → lender config → real-time events → audit trail.
  - **Track B (Migration)**: 5 scenes — Move 47k-loan book. Upload CSV → quality dashboard → failure drill-down → remediation → reconciliation report.
- Build priorities for each track
- Identified existing infrastructure that can be reused (CSV parser, pipeline DAG, quality dashboard, analytics hooks — all in labs-platform, wired to derivatives)
- Combined 12-week build schedule for both tracks

## What's Pending

### ~~IMMEDIATE: Add Migration Architecture to Tech Design~~ ✅ DONE
Added section 13 (Migration Architecture) to `docs/tech-design-lixi2-extension.md`:
- 13.1 Pipeline Architecture (6-stage streaming pipeline)
- 13.2 Ingest (CSV/Excel parse with type detection)
- 13.3 Column Auto-Mapper (fuzzy match + 500-synonym dictionary)
- 13.4 Transform (type conversion, normalization, transform documentation)
- 13.5 Batch Validation (parallel validation with pre-loaded resources)
- 13.6 Quality Aggregation (5-dimension scoring, field coverage, rule failures)
- 13.7 Remediation Engine (preview → apply → re-validate → audit trail)
- 13.8 Reconciliation Engine (record/financial/field-level comparison)
- 13.9 In-Flight Loan Handling (lifecycle position resolver, migration bypass)
- 13.10 New Database Tables (migration_jobs, staging, field_mappings, remediation_actions, reconciliation_reports)
- 13.11 Migration APIs (full REST API surface)
- Revised phasing (section 17): origination + migration interleaved, both demo-able by week 6
- Added 5 migration risks (R11-R15) and 8 migration success metrics

### LATER: Build the demo
- Track A minimum viable: assessment dashboard, lender profiles, SSE events (~4 weeks)
- Track B minimum viable: migration workspace, quality dashboard, drill-down, reconciliation (~5 weeks)
- Shared: mortgage quality rules, field coverage, seed data

## Key Files

| File | What |
|------|------|
| `docs/tech-design-lixi2-extension.md` | Architecture doc (origination done, migration pending) |
| `docs/demo-playbook.md` | Demo scenes + build priorities for both tracks |
| `docs/walkthrough-steps.md` | 14 explorer steps mapped to SRS processes |
| `mortgage-origination-srs.md` | SRS (data model, state machine, API, rules) |
| `~/repos/labs-platform/src/app/(mortgages)/mortgages/explorer/` | State Explorer (deployed) |
| `~/repos/labs-platform/src/app/(mortgages)/mortgages/validate/page.tsx` | LIXI2 validator (deployed, 6 samples) |
| `~/repos/labs-platform/src/lib/pipeline/` | Pipeline infra (CSV parser, types, orchestration — derivatives-wired) |
| `~/repos/labs-platform/src/app/(analyse)/analyse/quality/page.tsx` | Quality dashboard (derivatives-wired) |

## Key Architectural Decisions (don't re-debate these)

1. **LIXI2 is an adapter, not the core domain** — internal JPA entities are canonical
2. **Modular monolith** — not microservices. Extract later if scaling demands.
3. **4-tier validation** — Schema → Schematron → Lender → Domain, independently runnable
4. **Explicit Java mappers** for LIXI2↔domain (not config-driven transformation) — too critical for magic
5. **Store original LIXI2 messages** in `lixi_messages` table for round-trip fidelity
6. **EGB is config, not engine** — don't bet on 2016 spec revival; JSON lender profiles primary
7. **SSE for real-time** (not WebSocket) — simpler, HTTP-native, sufficient for our use case
