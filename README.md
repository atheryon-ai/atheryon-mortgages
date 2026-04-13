# atheryon-mortgages

Mortgage origination platform for Australian residential lending.

**Informed by**:
- Oracle Banking Platform (Origination of Loans & Mortgages module)
- LIXI2 Credit Application Standard (CAL 2.6.x)
- Australian Consumer Data Right (CDR) Open Banking Product & Lending Rates schemas

## Tech Stack

- **Backend**: Java 17 / Spring Boot 3.3.7
- **Database**: H2 in-memory (dev), PostgreSQL (target production)
- **State engine**: Custom `ApplicationStateMachine` with strict transition rules
- **API**: RESTful JSON, documented in `mortgage-origination-srs.md` Section 8
- **Data model**: JPA/Hibernate entities inspired by LIXI2 + Oracle FAC patterns

## Core Entities

- **Product** — Mortgage product catalogue (CDR + Oracle Business Product)
- **Party** — Borrower, Guarantor, Company, Trust (LIXI2 + Oracle Party)
- **LoanApplication** — Root aggregate with 16-state lifecycle
- **PropertySecurity** — Property details and valuations (LIXI2 Security)
- **FinancialSnapshot** — Income, expenses, assets, liabilities, serviceability
- **Document**, **DecisionRecord**, **Offer**, **ConsentRecord**, **WorkflowEvent**

Full JSON schemas in `mortgage-origination-srs.md` Section 4.

## Business Processes

Eight business processes aligned to Oracle FACs (see SRS Section 3):

| # | Process | Oracle FAC |
|---|---------|------------|
| 1 | Product Enquiry / Pre-Application | RPM_FA_LO_ENQUIRY |
| 2 | Application Initiation / Capture | RPM_FA_LO_APP_ENTRY |
| 3 | Application Submission | RPM_FA_LO_APP_SUBMIT |
| 4 | Assessment & Verification | RPM_FA_LO_APP_ASSESS |
| 5 | Decisioning & Approval | RPM_FA_LO_APP_DECISION |
| 6 | Offer & Acceptance | RPM_FA_LO_OFFER |
| 7 | Settlement & Funding | RPM_FA_LO_SETTLE |
| 8 | Hand-Off to Servicing | RPM_FA_LO_HANDOFF |

State machine diagram in SRS Section 6.2.

## Test Observatory Dashboard

A dev-only dashboard at `http://localhost:8080` with three tabs:

1. **Test Runner** — Run E2E test suites by SRS process, streamed output
2. **Lifecycle Walkthrough** — Interactive 14-step walkthrough exercising the full origination lifecycle (see [walkthrough step mapping](docs/walkthrough-steps.md))
3. **Data Inspector** — Entity tree + audit trail at any point during the walkthrough

### Running

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew bootRun --args='--spring.profiles.active=dev'
# Open http://localhost:8080
```

### Playwright E2E Tests

```bash
npx playwright test   # 23 tests covering all 3 dashboard tabs + API endpoints
```

## Key Files

| File | Purpose |
|------|---------|
| `mortgage-origination-srs.md` | Full SRS — data model, state machine, API surface, business rules, LIXI2 integration |
| `docs/walkthrough-steps.md` | 14-step walkthrough mapped to SRS business processes |
| `src/main/resources/static/index.html` | Test Observatory dashboard (single-page, dark theme) |
| `src/.../service/WalkthroughService.java` | Walkthrough backend — calls real service layer |
| `src/.../controller/DevTestController.java` | Test runner API — Gradle + JUnit XML parsing |
| `e2e/observatory.spec.js` | Playwright tests for the dashboard |
| `playwright.config.js` | Playwright config (Chromium, localhost:8080) |
