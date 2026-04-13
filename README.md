# atheryon-mortgages

Mortgage origination reference architecture and data models for Australian lending.

**Informed by**:
- Oracle Banking Platform (One Bank Platform / Origination of Loans & Mortgages module used by Westpac)
- LIXI2 Credit Application Standard (CAL, DAS, etc.)
- Australian Consumer Data Right (CDR) Open Banking Product & Lending Rates schemas

## Core Entities

See `models/` directory for detailed JSON schemas.

### Key Entities
- **Product** — Mortgage product catalogue (CDR + Oracle Business Product)
- **Party** — Borrower, Guarantor, Company, Trust (LIXI2 + Oracle Party)
- **LoanApplication** — Core application object with status (Oracle LoanApplication + LIXI2 CAL root)
- **Security/Collateral** — Property details and valuations (LIXI2 Security)
- **FinancialSnapshot**, **Document**, **DecisionRecord**

## Business Processes & State Transitions

Documented with state-change functions that align to Oracle Functional Activity Codes (FACs) and LIXI2 message flows:

1. **Product Enquiry / Pre-Application**
2. **Application Initiation / Capture**
3. **Application Submission**
4. **Assessment & Verification**
5. **Decisioning & Approval**
6. **Offer & Acceptance**
7. **Settlement & Funding**
8. **Post-Settlement Hand-off**

Full details with state machines and API mappings: `processes/`

## Integration Patterns
- Broker inflows → LIXI2 JSON/XML → transformation adapter → Oracle Banking APIs (REST)
- Product data → Open Banking Product API (read-only)
- Internal channels → direct Oracle Origination REST services

## Tech Stack Guidance
- Backend: Java / Microservices (Oracle extensions)
- Data layer: Oracle DB with custom extensions
- Messaging: LIXI2 schemas for external, internal canonical model
- State engine: Oracle Workflow + rules engine

## Next Steps
- Expand full JSON schemas with every LIXI2 element
- Add PlantUML state diagrams
- Generate OpenAPI specs for Oracle-style endpoints

Contributions and internal use only.
