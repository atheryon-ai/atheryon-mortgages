

```markdown
# Mortgage Origination System – Software Requirements Specification (SRS)

**Project**: Atheryon Mortgages  
**Repository**: https://github.com/atheryon-ai/atheryon-mortgages  
**Version**: 1.3 (with PlantUML Diagrams)  
**Date**: 13 April 2026  
**Status**: Ready for Technical Design  
**Audience**: Claude – for producing detailed HLD, ERD, Swagger/OpenAPI, DB schema, microservices, state machines, and LIXI2 adapters.

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
  "productId": "string",
  "productType": "MORTGAGE",
  "name": "string",
  "lendingRates": [{ "rateType": "VARIABLE|FIXED", "rate": "decimal", "comparisonRate": "decimal" }],
  "features": ["OFFSET_ACCOUNT", "REDRAW", "PORTABLE", "CASHBACK", "MULTI_OFFSET"],
  "eligibility": { "minimumAge": "int", "maximumLTV": "decimal", "minimumLoanAmount": "decimal" },
  "fees": [{ "feeType": "string", "amount": "decimal" }]
}
```

### 4.2 Party (Oracle Party + LIXI2)
```json
{
  "partyId": "string",
  "partyType": "INDIVIDUAL|COMPANY|TRUST|SMSF",
  "role": "BORROWER|CO_BORROWER|GUARANTOR",
  "personalDetails": { "fullName": "string", "dateOfBirth": "date", "taxFileNumber": "string" },
  "contact": { "email": "string", "mobile": "string" },
  "kycStatus": { "status": "VERIFIED|PENDING", "amlChecked": true },
  "employment": { "employerName": "string", "annualIncome": "decimal", "verified": true },
  "financials": "FinancialSnapshot reference"
}
```

### 4.3 LoanApplication (Root – Oracle + LIXI2 CAL)
```json
{
  "applicationId": "string",
  "status": "DRAFT|IN_PROGRESS|SUBMITTED|UNDER_ASSESSMENT|VERIFIED|DECISIONED|OFFER_ISSUED|ACCEPTED|SETTLEMENT_IN_PROGRESS|SETTLED|SERVICING",
  "productId": "string",
  "parties": [{ "partyId": "string", "role": "string" }],
  "loanDetails": {
    "purpose": "PURCHASE|REFINANCE|CONSTRUCTION",
    "requestedAmount": "decimal",
    "termMonths": "int",
    "interestType": "VARIABLE|FIXED",
    "repaymentFrequency": "MONTHLY|FORTNIGHTLY"
  },
  "securities": [{ "securityId": "string" }],
  "documents": [{ "documentId": "string" }],
  "decisionRecord": {},
  "offerDetails": {},
  "channel": "BROKER|DIRECT",
  "timestamps": { "createdAt": "datetime", "submittedAt": "datetime" }
}
```

### 4.4 Security / Collateral
```json
{
  "securityId": "string",
  "type": "RESIDENTIAL_PROPERTY",
  "address": { "fullAddress": "string", "postcode": "string" },
  "ownership": [{ "partyId": "string", "percentage": "decimal" }],
  "valuation": { "estimatedValue": "decimal", "ltv": "decimal", "valuationDate": "date" }
}
```

Supporting entities: FinancialSnapshot, Document, DecisionRecord, Offer, Valuation, LMIQuote, ConsentRecord, WorkflowEvent.

**Core Relationships**:
- LoanApplication 1→N Party (via roles)
- LoanApplication 1→N Security
- LoanApplication 1→1 Product

## 5. PlantUML Diagrams

### 5.1 Domain Class Diagram
```plantuml
@startuml
skinparam classAttributeIconSize 0

class Product {
  +productId: String
  +name: String
}

class Party {
  +partyId: String
  +partyType: Enum
  +role: Enum
}

class LoanApplication {
  +applicationId: String
  +status: Enum
  +channel: Enum
}

class Security {
  +securityId: String
  +type: String
  +valuation: Object
}

class Document {
  +documentId: String
  +status: Enum
}

LoanApplication "1" --> "1" Product
LoanApplication "1" --> "*" Party
LoanApplication "1" --> "*" Security
LoanApplication "1" --> "*" Document
@enduml
```

### 5.2 LoanApplication State Machine
```plantuml
@startuml
[*] --> DRAFT
DRAFT --> IN_PROGRESS : save data
IN_PROGRESS --> READY_FOR_SUBMISSION : validation pass
READY_FOR_SUBMISSION --> SUBMITTED : submit
SUBMITTED --> UNDER_ASSESSMENT : start checks
UNDER_ASSESSMENT --> VERIFIED : complete verification
VERIFIED --> DECISIONED : run rules
DECISIONED --> APPROVED : approved
APPROVED --> OFFER_ISSUED : issue offer
OFFER_ISSUED --> ACCEPTED : accept
ACCEPTED --> SETTLEMENT_IN_PROGRESS : settlement
SETTLEMENT_IN_PROGRESS --> SETTLED : funded
SETTLED --> SERVICING
@enduml
```

### 5.3 Sequence Diagram: Broker Application Submission (LIXI2 Flow)
```plantuml
@startuml
actor Broker
participant "Aggregator / Broker Portal" as BrokerPortal
participant "LIXI2 Adapter" as Adapter
participant "Oracle Banking Platform (OBP)" as OBP
participant "Credit Bureau" as Bureau

Broker -> BrokerPortal: Submit Application (LIXI2 JSON/XML)
BrokerPortal -> Adapter: Forward LIXI2 CAL message
Adapter -> OBP: Transform & Call Create/Update LoanApplication API
OBP -> OBP: Validate & Create LoanApplication (status = SUBMITTED)
OBP -> Bureau: Request Credit Report (async)
Bureau --> OBP: Credit Response
OBP --> Adapter: Application Accepted + applicationId
Adapter --> BrokerPortal: LIXI2 Acknowledgement
BrokerPortal --> Broker: Submission Confirmed

note right
  Oracle FAC: RPM_FA_LO_APP_ENTRY + RPM_FA_LO_APP_SUBMIT
end note
@enduml
```

### 5.4 Sequence Diagram: Digital Direct Application Flow
```plantuml
@startuml
actor Customer
participant "Westpac App / Website" as DigitalChannel
participant "Oracle Banking Platform (OBP)" as OBP
participant "External Services" as External

Customer -> DigitalChannel: Start Application + Enter Details
DigitalChannel -> OBP: POST /loanapplication (initiate + enrich)
OBP -> OBP: Create LoanApplication (status = IN_PROGRESS)
OBP -> External: Call Credit Bureau + Valuation Service (parallel)
External --> OBP: Responses
OBP -> DigitalChannel: Return Application Status + Next Steps
DigitalChannel --> Customer: Show Progress / Required Documents

note right
  Uses Oracle Banking Origination APIs directly
end note
@enduml
```

### 5.5 Sequence Diagram: Assessment & Decisioning
```plantuml
@startuml
participant "LoanApplication Service" as AppService
participant "Rules Engine" as Rules
participant "Credit Decision Service" as Decision
participant "Underwriter (Manual)" as UW

AppService -> Rules: Run Automated Assessment
Rules --> AppService: Preliminary Result
AppService -> Decision: Request Final Decision
Decision -> Decision: Score + Policy Check
alt Automated Approval
  Decision --> AppService: APPROVED
else Manual Review Required
  Decision --> UW: Escalate for Review
  UW --> Decision: Manual Decision (Approve/Decline)
end
Decision --> AppService: Final Outcome
AppService -> AppService: Update status + emit CreditDecisionMade event
@enduml
```

## 6. Domain Events
- LoanApplicationCreated
- LoanApplicationSubmitted
- ApplicationAssessed
- ValuationReceived
- CreditDecisionMade
- OfferIssued
- OfferAccepted
- SettlementCompleted
- LIXI2MessageReceived / Sent

## 7. Integration & Non-Functional Requirements
- Inbound LIXI2 transformation layer
- Real-time CDR Product consumption
- Full audit trail on every state change
- Straight-through processing target: <30s for simple cases
- Oracle-style extensibility points

## 8. Next Steps for Claude
Use the diagrams + data models to generate:
- Detailed ERD / PostgreSQL schema
- Full Swagger/OpenAPI contracts mirroring Oracle Banking APIs
- Executable state machine code
- Microservices architecture with LIXI2 adapter
- Event-driven backbone

**End of Document – Version 1.3**
```
