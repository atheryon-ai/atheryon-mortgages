
```markdown
# Mortgage Origination System – Software Requirements Specification (SRS)

**Project**: Atheryon Mortgages  
**Repository**: https://github.com/atheryon-ai/atheryon-mortgages  
**Version**: 1.0  
**Date**: 13 April 2026  
**Status**: Draft – Ready for Technical Design  
**Intended Audience**: Claude (or any AI/technical lead) to generate detailed High-Level Design (HLD), API contracts, database schema, microservices architecture, state machines, and integration layers.

## 1. Introduction & Purpose
This SRS provides a **complete, self-contained specification** of **ALL data objects**, **ALL lifecycles/state machines**, **ALL domain events**, and **ALL business processes** required for a modern mortgage origination system.

It is explicitly informed by:
- **Oracle Banking Platform (OBP)** – Origination of Loans and Mortgages module (releases 14.x / 25.x), including Functional Activity Codes (FACs) and REST APIs.
- **LIXI2 Credit Application Standard (CAL)** – Latest JSON + XML schemas for Australian lending data exchange.
- **Australian Consumer Data Right (CDR) / Open Banking** – Especially the Product API and lendingRates structure.

The design goal is to enable a system that closely mirrors Westpac’s One Bank Platform (OBP) while remaining extensible and standards-compliant.

## 2. Scope
**In Scope**:
- End-to-end residential mortgage origination (home purchase, refinance, construction, equity release, top-up).
- Support for both broker (via LIXI2) and direct digital channels.
- Full straight-through processing (STP) with manual overrides.
- Pre-approval through to settlement and hand-off to core banking.

**Out of Scope**:
- Post-settlement loan servicing and repayments.
- Commercial, business, or corporate lending (except SMSF mortgages).

## 3. Mortgage Business Processes
1. **Product Enquiry / Pre-Application**
2. **Application Initiation / Capture**
3. **Application Submission**
4. **Assessment & Verification**
5. **Decisioning & Approval**
6. **Offer & Acceptance**
7. **Settlement & Funding**
8. **Hand-Off to Servicing**

## 4. Complete Data Model – All Entities

### 4.1 Product (from CDR Open Banking + Oracle Business Product)
```json
{
  "productId": "string",
  "productType": "MORTGAGE",
  "name": "string",
  "lendingRates": [
    {
      "rateType": "VARIABLE|FIXED",
      "rate": "decimal",
      "comparisonRate": "decimal"
    }
  ],
  "features": ["OFFSET_ACCOUNT", "REDRAW", "PORTABLE", "CASHBACK"],
  "eligibility": { ... },
  "fees": [
    {
      "feeType": "string",
      "amount": "decimal"
    }
  ],
  "constraints": { ... }
}
```

### 4.2 Party (Oracle Party + LIXI2 Applicant/Guarantor)
- `partyId`: string
- `partyType`: "INDIVIDUAL" | "COMPANY" | "TRUST" | "SMSF"
- `role`: "BORROWER" | "CO_BORROWER" | "GUARANTOR"
- `personalDetails`: object (name, dob, taxFileNumber, etc.)
- `contact`: object (email, mobile)
- `kycStatus`: object
- `employment`: object
- `financials`: object (income, liabilities, assets)

### 4.3 LoanApplication (Root Entity – Oracle LoanApplication + LIXI2 CAL)
- `applicationId`: string
- `status`: string (see state machine below)
- `productId`: string
- `parties`: array of Party references
- `loanDetails`: object (amount, termMonths, purpose, interestType, repaymentFrequency)
- `securities`: array of Security references
- `documents`: array of Document references
- `decisionRecord`: object
- `offerDetails`: object
- `channel`: "BROKER" | "DIRECT"
- `timestamps`: object (created, lastUpdated, submitted)

### 4.4 Security / Collateral (LIXI2 Security + Oracle Collateral)
- `securityId`: string
- `type`: "RESIDENTIAL_PROPERTY"
- `address`: object
- `ownership`: array
- `valuation`: object (value, valuer, date, LTV)
- `titleDetails`: object

### 4.5 Supporting Entities
- **FinancialSnapshot** – Linked to Party or LoanApplication (income, liabilities, assets per LIXI2)
- **Document** – `documentId`, `type`, `status` (UPLOADED | PENDING_VERIFICATION | VERIFIED | REJECTED), `url`
- **DecisionRecord** – `outcome` (APPROVED | DECLINED | REFERRED), `score`, `reasonCodes`, `overrideReason`
- **Offer** – rates, conditions, expiryDate, acceptanceStatus
- **Valuation**, **LMIQuote**, **ConsentRecord**, **WorkflowEvent**

**Core Relationships**:
- One `LoanApplication` → Many `Party` (via roles)
- One `LoanApplication` → Many `Security`
- One `LoanApplication` → One `Product`

## 5. Lifecycles & State Machines

### 5.1 LoanApplication State Machine (Main Workflow)
```
DRAFT 
  → IN_PROGRESS 
  → READY_FOR_SUBMISSION 
  → SUBMITTED 
  → UNDER_ASSESSMENT 
  → VERIFIED 
  → DECISIONED → (APPROVED | DECLINED | REFERRED) 
  → OFFER_ISSUED 
  → ACCEPTED 
  → SETTLEMENT_IN_PROGRESS 
  → SETTLED 
  → SERVICING (archived in OBP)
```

### 5.2 Other State Machines
- **Party**: PROSPECT → ONBOARDED → VERIFIED → KYC_COMPLETE
- **Security**: DRAFT → VALUATION_REQUESTED → VALUED → TITLE_VERIFIED
- **Document**: UPLOADED → PENDING_VERIFICATION → VERIFIED | REJECTED

Transitions are driven by user actions, Oracle Functional Activity Codes (FACs), rules engine, and external events (credit bureau response, valuation received, etc.).

## 6. Domain Events (Complete List)
- `LoanApplicationCreated`
- `LoanApplicationSubmitted`
- `ApplicationAssessed`
- `ValuationReceived`
- `CreditDecisionMade`
- `OfferIssued`
- `OfferAccepted`
- `SettlementCompleted`
- `DocumentVerified`
- `LIXI2MessageReceived`
- `LIXI2MessageSent`

Every event must include `applicationId`, `timestamp`, `actor`, and payload delta.

## 7. Integration Requirements
- **Inbound Broker**: Accept LIXI2 JSON/XML → transform → internal Oracle-style model
- **Outbound**: Generate LIXI2 settlement and status messages
- **Product Data**: Consume CDR Open Banking `/products` endpoint in real time
- **External Services**: Credit bureaus, valuers, LMI providers via adapters

## 8. Non-Functional Requirements
- Full audit trail on every state change and data modification
- High performance for straight-through processing (< 30s for simple approvals)
- Extensibility via configuration and extension points (Oracle-style)
- Strong compliance with responsible lending, privacy, and CDR consent rules

## 9. Next Steps for Technical Design
Using this SRS, generate:
- Detailed Entity Relationship Diagram (ERD)
- Full Swagger/OpenAPI specifications for all origination endpoints
- Database schema (PostgreSQL with Oracle compatibility notes)
- State machine implementation (XState, Temporal, or Camunda)
- Microservices architecture with dedicated LIXI2 transformation service
- Event-driven backbone design

---

**End of Document**
```
