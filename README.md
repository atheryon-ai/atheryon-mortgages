# Atheryon Mortgages Platform

Mortgage origination and migration platform for Australian residential lending.

**Informed by**:
- Oracle Banking Platform (Origination of Loans & Mortgages module)
- LIXI2 Credit Application Standard (CAL 2.6.x)
- Australian Consumer Data Right (CDR) Open Banking Product & Lending Rates schemas

## Architecture

```
┌───────────────────────────────────────────────────────────────────────────┐
│  labs-platform (Next.js)          https://dev.atheryon.ai                │
│  ┌─────────────┐ ┌──────────────┐ ┌────────────────┐ ┌───────────────┐  │
│  │ State       │ │ LIXI Gateway │ │ Migration      │ │ Validate      │  │
│  │ Explorer    │ │ /gateway     │ │ Console        │ │ /validate     │  │
│  │ /explorer   │ │              │ │ /migration     │ │               │  │
│  └──────┬──────┘ └──────┬───────┘ └───────┬────────┘ └───────┬───────┘  │
│         │               │                 │                   │          │
│         └───────────────┴────────┬────────┴───────────────────┘          │
│                    /api/mortgages/* (Next.js proxy routes)               │
└────────────────────────────┬────────────────────────────────────────────┘
                             │ HTTP
┌────────────────────────────┴────────────────────────────────────────────┐
│  mortgages-platform (Spring Boot)     http://localhost:8080              │
│                                                                         │
│  ┌─── Origination ───────────────────────────────────────────────────┐  │
│  │  Products  Parties  Applications  Securities  Financials          │  │
│  │  State Machine (16 states)  Walkthrough  Workflow Events          │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ┌─── LIXI2 Module ─────────────────────────────────────────────────┐  │
│  │  Schema Validator (JSON Schema Draft 7)                           │  │
│  │  Schematron Validator (10 business rules SCH-001..010)            │  │
│  │  Lender Rule Validator (10 rules LR-001..010, configurable)       │  │
│  │  Domain Rule Validator (10 rules MR-001..010 from SRS)            │  │
│  │  LixiCalMapper (inbound, 683 lines, 13 enum maps)                │  │
│  │  LixiCalEmitter (outbound — domain → LIXI2 JSON)                 │  │
│  │  Gateway Controller (5 endpoints) + Message Audit Trail           │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ┌─── Migration Module ─────────────────────────────────────────────┐  │
│  │  CSV Parser (BOM, delimiter auto-detect, quoted fields)           │  │
│  │  Column Auto-Mapper (80+ targets, 180+ synonyms, Levenshtein)    │  │
│  │  6-Stage Pipeline (Parse → Map → Transform → Validate → Classify │  │
│  │                     → Store)                                      │  │
│  │  Quality Engine (5 dimensions, weighted scoring)                  │  │
│  │  Remediation Service (10 rules REM-001..010, preview/apply)       │  │
│  │  Reconciliation Service (record / financial / field-level)        │  │
│  │  Migration Controller (7 endpoints)                               │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ┌─── Infrastructure ───────────────────────────────────────────────┐  │
│  │  H2 in-memory (dev) / PostgreSQL (prod)                          │  │
│  │  9 Flyway migrations (V1–V9)                                      │  │
│  │  Spring Security + Actuator health probes                         │  │
│  │  Swagger/OpenAPI at /api-docs                                     │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **Backend** | Java 17, Spring Boot 3.3.7 |
| **Frontend** | Next.js 16, Fluent UI v9, Zustand, CSS Modules |
| **Database** | H2 (dev), PostgreSQL 16 (prod), Flyway migrations |
| **Validation** | networknt JSON Schema Draft 7, custom Schematron engine |
| **State engine** | Custom `ApplicationStateMachine` (16 states, strict transitions) |
| **Testing** | JUnit 5, Testcontainers (PostgreSQL 16-alpine), Playwright |
| **IaC** | Bicep (Azure Container Apps), GitHub Actions CI/CD |
| **Container** | Eclipse Temurin 17, multi-stage Docker build |

## Quick Start

```bash
# Backend (Spring Boot on port 8080)
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew bootRun --args='--spring.profiles.active=dev'

# Frontend (Next.js on port 3002, in labs-platform repo)
cd ~/repos/labs-platform && npm run dev -- -p 3002

# Open in browser
open http://localhost:3002/mortgages/gateway      # LIXI Gateway
open http://localhost:3002/mortgages/migration     # Migration Console
open http://localhost:3002/mortgages/explorer      # State Explorer
open http://localhost:8080                          # Test Observatory
```

## API Endpoints

### Origination (existing)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/products` | List mortgage products |
| GET | `/api/applications` | List loan applications |
| POST | `/api/applications` | Create application |
| GET | `/api/applications/{id}` | Get application detail |
| GET | `/api/applications/{id}/events` | Workflow events |

### LIXI2 Gateway (new)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/lixi/validate` | 4-tier validation (Schema → Schematron → Lender → Domain) |
| POST | `/api/lixi/ingest` | Validate + map + persist → returns applicationId |
| GET | `/api/lixi/samples` | List 6 sample LIXI2 CAL messages |
| GET | `/api/lixi/samples/{name}` | Get sample JSON |
| GET | `/api/lixi/messages/{applicationId}` | Message audit trail |

### Migration Pipeline (new)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/migration/upload` | Upload CSV → full pipeline (parse, map, transform, validate, classify) |
| GET | `/api/migration/jobs` | List migration jobs |
| GET | `/api/migration/jobs/{id}` | Job detail with quality report |
| GET | `/api/migration/jobs/{id}/records` | Paginated staging records (source → mapped → transformed) |
| GET | `/api/migration/jobs/{id}/mappings` | Field mappings with confidence scores |
| PUT | `/api/migration/jobs/{id}/mappings/{mid}` | Confirm/reject/manual-edit a mapping |
| POST | `/api/migration/jobs/{id}/import` | Promote clean records to production |

## Module Structure

```
src/main/java/com/atheryon/mortgages/
├── config/           # Security, Swagger, app config
├── controller/       # REST controllers (origination)
├── entity/           # Core JPA entities (32 enums)
├── repository/       # Spring Data JPA repositories
├── service/          # Business logic (ApplicationService, etc.)
├── statemachine/     # 16-state lifecycle engine
├── rules/            # Decision engine, LTV calculator, serviceability
│
├── lixi/             # LIXI2 Module
│   ├── schema/       # 4-tier validation pipeline (6 files)
│   ├── mapper/       # LixiCalMapper + LixiCalEmitter (3 files)
│   ├── gateway/      # REST controller + service (4 files)
│   └── message/      # LixiMessage entity + repository (2 files)
│
├── migration/        # Migration Module
│   ├── entity/       # MigrationJob, LoanStaging, FieldMapping, etc. (5 files)
│   ├── repository/   # Repositories with custom queries (5 files)
│   ├── pipeline/     # CSV parser, auto-mapper, pipeline orchestrator (3 files)
│   ├── controller/   # MigrationController (7 endpoints)
│   ├── quality/      # Quality scorer, aggregator, reports (6 files)
│   ├── remediation/  # 10-rule remediation engine (4 files)
│   ├── reconciliation/ # 3-level reconciliation service (3 files)
│   └── seed/         # Test data generators (2 files)
│
├── lender/           # (scaffolded) Lender profiles + EGB config
├── notification/     # (scaffolded) SSE + webhook events
└── integration/      # (scaffolded) Credit bureau + settlement adapters
```

## Database Schema

9 Flyway migrations in `src/main/resources/db/migration/`:

| Migration | Table | Purpose |
|-----------|-------|---------|
| V1 | `lixi_messages` | LIXI2 message audit trail (direction, payload JSONB) |
| V2 | `domain_events` | Event sourcing (type, aggregate, payload JSONB) |
| V3 | `lender_profiles` | Lender configuration (rules JSONB, active flag) |
| V4 | `webhook_subscriptions` | Event webhook endpoints |
| V5 | `migration_jobs` | Migration job tracking (status enum, quality report) |
| V6 | `migration_loan_staging` | Staging records (source/mapped/transformed JSONB) |
| V7 | `migration_field_mappings` | Column mapping with confidence + status |
| V8 | `remediation_actions` | Remediation audit trail (rule, before/after quality) |
| V9 | `reconciliation_reports` | Reconciliation results (report data JSONB) |

Core origination tables (created by Hibernate DDL in dev, Flyway in prod):
`loan_applications`, `parties`, `securities`, `products`, `financial_snapshots`,
`documents`, `decision_records`, `offers`, `consent_records`, `workflow_events`, etc.

## Validation Pipeline

4-tier validation with fail-fast between Schema/Schematron and Lender/Domain:

```
Tier 1: Schema (JSON Schema Draft 7)     → hard fail stops pipeline
Tier 2: Schematron (10 rules SCH-001..010) → hard fail stops pipeline
Tier 3: Lender Rules (10 rules LR-001..010) → configurable per lender
Tier 4: Domain Rules (10 rules MR-001..010) → SRS business rules
```

## Migration Pipeline

6-stage processing of legacy CSV portfolios:

```
Parse → Map → Transform → Validate → Classify → Store
  │       │       │           │          │         │
  │       │       │           │          │         └─ Persist to staging table
  │       │       │           │          └─ CLEAN / WARNING / FAILED
  │       │       │           └─ Required fields, business rules
  │       │       └─ Date normalization, type conversion, enum mapping
  │       └─ Auto-mapper: Exact (1.0) → Synonym (0.9) → Fuzzy (0.7)
  └─ BOM handling, delimiter auto-detect, quoted fields
```

Quality engine scores across 5 dimensions (weighted):
Completeness (30%), Accuracy (25%), Consistency (20%), Validity (20%), Uniqueness (5%)

## Frontend Pages (labs-platform)

| Page | Route | Description |
|------|-------|-------------|
| Dashboard | `/mortgages` | Metric cards overview |
| LIXI Gateway | `/mortgages/gateway` | Validate/ingest LIXI2 messages, 6 samples, 4-tier results |
| Migration Console | `/mortgages/migration` | Upload CSV, review mappings, quality, records, import |
| State Explorer | `/mortgages/explorer` | 14-step interactive lifecycle walkthrough |
| Validate | `/mortgages/validate` | LIXI2 schema validation playground |
| Capture | `/mortgages/capture` | Mortgage data entry form |

## Infrastructure

### Azure Resources

| Resource | Name | Purpose |
|----------|------|---------|
| Resource Group | `rg-atheryon-ai` | All Atheryon resources |
| Container Apps Environment | `cdm-containerenv-dev` | Shared networking |
| Container App | `ca-mortgages-dev` | This service (internal ingress, port 8080) |
| Container Registry | `crlabsdev.azurecr.io` | Docker images |
| PostgreSQL | (shared labs DB server) | Production database |

### IaC

- **Bicep template**: `infra/azure/container-app.bicep` — Container App definition with health probes
- **GitHub Actions**: `.github/workflows/deploy-dev.yml` — Build → push to ACR → deploy to Container App
- **Docker**: Multi-stage build (Temurin 17 JDK → JRE), non-root user, 512MB heap

### Environment Variables

| Variable | Dev | Prod |
|----------|-----|------|
| `SPRING_PROFILES_ACTIVE` | `dev` | `prod` |
| `DB_USERNAME` | `sa` (H2) | `atheryon` |
| `DB_PASSWORD` | (empty) | (secret) |
| `JAVA_OPTS` | default | `-Xmx512m -Xms256m -XX:+UseG1GC` |

### Health Probes

| Probe | Path | Interval |
|-------|------|----------|
| Startup | `/actuator/health` | 5s (30 retries, 160s max) |
| Liveness | `/actuator/health/liveness` | 15s |
| Readiness | `/actuator/health/readiness` | 10s |

## Testing

```bash
# Unit + integration tests (requires Docker for Testcontainers)
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew test

# Playwright E2E (Test Observatory dashboard)
npx playwright test
```

Integration tests use Testcontainers with PostgreSQL 16-alpine for realistic DB testing.

## Documentation

| Document | Description |
|----------|-------------|
| [`docs/tech-design-lixi2-extension.md`](docs/tech-design-lixi2-extension.md) | Architecture: origination + migration (2,443 lines, 19 sections) |
| [`docs/demo-playbook.md`](docs/demo-playbook.md) | Demo design: Track A origination + Track B migration |
| [`docs/delivery-plan.md`](docs/delivery-plan.md) | Execution plan: 10 teams, 36 agents, 96 deliverables, 24 weeks |
| [`docs/walkthrough-steps.md`](docs/walkthrough-steps.md) | 14-step explorer mapping to SRS processes |
| [`mortgage-origination-srs.md`](mortgage-origination-srs.md) | SRS: data model, state machine, API, business rules |

## Seed Data

| File | Records | Purpose |
|------|---------|---------|
| `src/main/resources/lixi/samples/` | 6 files | LIXI2 CAL samples (valid, warnings, failures, edge cases) |
| `src/main/resources/lixi/schema/cal-2.6.91.json` | — | JSON Schema Draft 7 for CAL validation |
| `src/main/resources/migration/seed-portfolio.csv` | 500 | Portfolio with deliberate quality issues |
| `src/main/resources/migration/seed-100-loans.csv` | 100 | Quick test dataset |
| `src/main/resources/migration/seed-10k-loans.csv` | 10,000 | Performance test dataset |
