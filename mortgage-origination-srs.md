
```markdown
# Mortgage Origination System – Software Requirements Specification (SRS)

**Project**: Atheryon Mortgages  
**Repository**: https://github.com/atheryon-ai/atheryon-mortgages  
**Version**: 1.1 (Expanded)  
**Date**: 13 April 2026  
**Status**: Ready for Technical Design  
**Audience**: Claude – to produce detailed HLD, ERD, Swagger/OpenAPI, DB schema, microservices, state machines, and LIXI2 adapters.

## 1. Introduction & Purpose
This SRS defines **ALL** required data objects, lifecycles, domain events, and business processes for a mortgage origination system modelled on **Westpac’s One Bank Platform (Oracle Banking Platform – Origination of Loans & Mortgages)**.

It integrates:
- Oracle Banking APIs + Functional Activity Codes (FACs)
- LIXI2 Credit Application Standard (CAL – JSON/XML)
- Australian CDR Open Banking Product API

## 2. Scope
**In Scope**: Full residential mortgage origination (purchase, refinance, construction, equity release) for broker + direct channels, from pre-approval to settlement hand-off.  
**Out of Scope**: Post-settlement servicing, commercial lending.

## 3. Business Processes
1. Product Enquiry / Pre-Application  
2. Application Initiation / Capture  
3. Application Submission  
4. Assessment & Verification  
5. Decisioning & Approval  
6. Offer & Acceptance  
7. Settlement & Funding  
8. Hand-Off to Servicing

## 4. Detailed Data Model

### 4.1 Product (CDR + Oracle Business Product)
```json
{
  "productId": "string",                    // CDR productId
  "productType": "MORTGAGE",
  "name": "string",
  "lendingRates": [
    {
      "rateType": "VARIABLE|FIXED",
      "rate": "decimal",
      "comparisonRate": "decimal",
      "calculationMethod": "string"
    }
  ],
  "features": ["OFFSET_ACCOUNT", "REDRAW", "PORTABLE", "CASHBACK", "MULTI_OFFSET"],
  "eligibility": {
    "minimumAge": "int",
    "maximumLTV": "decimal",
    "minimumLoanAmount": "decimal"
  },
  "fees": [{ "feeType": "string", "amount": "decimal", "frequency": "string" }],
  "constraints": { ... }
}
```

### 4.2 Party (Oracle Party + LIXI2)
```json
{
  "partyId": "string",
  "partyType": "INDIVIDUAL|COMPANY|TRUST|SMSF",
  "role": "BORROWER|CO_BORROWER|GUARANTOR",
  "personalDetails": {
    "fullName": "string",
    "dateOfBirth": "date",
    "taxFileNumber": "string",
    "driversLicence": "string"
  },
  "contact": { "email": "string", "mobile": "string", "address": "object" },
  "kycStatus": { "status": "VERIFIED|PENDING", "amlChecked": "boolean" },
  "employment": { "employerName": "string", "income": "decimal", "verified": "boolean" },
  "financials": "FinancialSnapshot reference"
}
```

### 4.3 LoanApplication (Core Root – Oracle LoanApplication + LIXI2 CAL)
```json
{
  "applicationId": "string",
  "status": "DRAFT|SUBMITTED|...|SETTLED",
  "productId": "string",
  "parties": [ { "partyId": "string", "role": "string" } ],
  "loanDetails": {
    "purpose": "PURCHASE|REFINANCE|CONSTRUCTION",
    "requestedAmount": "decimal",
    "termMonths": "int",
    "interestType": "VARIABLE|FIXED",
    "repaymentFrequency": "MONTHLY|FORTNIGHTLY"
  },
  "securities": [ { "securityId": "string" } ],
  "documents": [ { "documentId": "string" } ],
  "decisionRecord": "DecisionRecord reference",
  "offerDetails": "Offer reference",
  "channel": "BROKER|DIRECT",
  "timestamps": { "createdAt": "datetime", "submittedAt": "datetime", "settledAt": "datetime" }
}
```

### 4.4 Security / Collateral
```json
{
  "securityId": "string",
  "type": "RESIDENTIAL_PROPERTY",
  "address": { "fullAddress": "string", "postcode": "string", "state": "string" },
  "ownership": [{ "partyId": "string", "percentage": "decimal" }],
  "valuation": {
    "estimatedValue": "decimal",
    "valuerName": "string",
    "valuationDate": "date",
    "ltv": "decimal"
  },
  "titleDetails": { "titleReference": "string", "ownershipType": "string" }
}
```

### 4.5 Supporting Entities (Summary)
- **FinancialSnapshot**: income[], liabilities[], assets[] (LIXI2 structure)
- **Document**: documentId, type, status, url, verificationStatus
- **DecisionRecord**: outcome, automatedScore, reasonCodes[], manualOverrideReason
- **Offer**: offerId, approvedAmount, interestRate, conditions[], expiryDate, accepted
- **Valuation**, **LMIQuote**, **ConsentRecord** (CDR), **WorkflowEvent**

**Relationships**:
- LoanApplication 1→N Party (roles)
- LoanApplication 1→N Security
- LoanApplication 1→1 Product

## 5. Lifecycles & State Machines

### LoanApplication Main State Machine
```
DRAFT → IN_PROGRESS → READY_FOR_SUBMISSION → SUBMITTED → UNDER_ASSESSMENT 
→ VERIFIED → DECISIONED → (APPROVED | DECLINED | REFERRED) 
→ OFFER_ISSUED → ACCEPTED → SETTLEMENT_IN_PROGRESS → SETTLED → SERVICING
```

**Example Triggers** (Oracle FAC style):
- `RPM_FA_LO_APP_ENTRY` → DRAFT to IN_PROGRESS
- `RPM_FA_LO_APP_ASSESSMENT` → UNDER_ASSESSMENT
- `RPM_FA_LO_ACC_APPRVL` → Decisioned → Approved

Other machines: Party, Security, Document (as in previous version).

## 6. Domain Events (Expanded)
- `LoanApplicationCreated` {applicationId, partyIds, amount}
- `LoanApplicationSubmitted` {applicationId, channel}
- `ApplicationAssessed` {applicationId, creditScore, ltv}
- `ValuationReceived` {applicationId, securityId, value}
- `CreditDecisionMade` {applicationId, outcome, reasons}
- `OfferIssued`, `OfferAccepted`, `SettlementCompleted`, `LIXI2MessageReceived`, etc.

All events carry `applicationId`, `timestamp`, `actor` (USER|SYSTEM|BROKER), and relevant delta.

## 7. Integration Requirements
- Inbound: LIXI2 CAL JSON/XML → transformation service → LoanApplication
- Outbound: LIXI2 settlement instructions to lawyers
- Product: Real-time consumption of CDR `/products`
- External adapters for credit bureaus, valuers, LMI

## 8. Non-Functional Requirements
- Full audit trail on every state change
- Straight-through processing target: <30s for simple cases
- Oracle-style extensibility points for Australian rules
- Compliance with responsible lending and CDR consent

## 9. Next Steps for Claude
Generate:
- Detailed ERD + PostgreSQL schema (with Oracle compatibility)
- Full Swagger/OpenAPI for all origination endpoints (mirroring Oracle Banking APIs)
- State machine code (XState / Temporal)
- Microservices breakdown including LIXI2 transformation layer
- Event-driven architecture (Kafka-style)

---

**End of Document – Version 1.1 Expanded**
```
