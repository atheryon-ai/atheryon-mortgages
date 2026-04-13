# Lifecycle Walkthrough — SRS Business Process Mapping

The Test Observatory dashboard includes an interactive 14-step walkthrough that exercises the full mortgage origination lifecycle. Each step calls real service layer methods and returns the API request, response snapshot, and state transitions.

## Step Mapping

| Business Process | Step | Walkthrough Step | State After |
|---|---|---|---|
| **SRS-1** Product Enquiry / Pre-Application | — | *(product + lending rate created during session start)* | — |
| **SRS-2** Application Initiation / Capture | 1 | Create Application | DRAFT |
| | 2 | Add Borrower | DRAFT |
| | 3 | Add Property Security | DRAFT |
| | 4 | Upload Documents | DRAFT |
| **SRS-3** Application Submission | 5 | Submit Application | SUBMITTED |
| **SRS-4** Assessment & Verification | 6 | Begin Assessment | UNDER_ASSESSMENT |
| | 7 | Verify Documents | UNDER_ASSESSMENT |
| | 8 | Complete Verification | VERIFIED |
| | 9 | Add Financial Data | VERIFIED |
| **SRS-5** Decisioning & Approval | 10 | Run Decision | APPROVED |
| **SRS-6** Offer & Acceptance | 11 | Generate Offer | OFFER_ISSUED |
| | 12 | Accept Offer | OFFER_ACCEPTED |
| **SRS-7** Settlement & Funding | 13 | Begin Settlement | SETTLEMENT_IN_PROGRESS |
| | 14 | Complete Settlement | SETTLED |
| **SRS-8** Hand-Off to Servicing | — | *(not implemented)* | — |
| **SRS-W** Withdrawal | — | *(alternate path, not in happy-path walkthrough)* | — |

## Notes

- **Steps that don't change app status**: 2, 3, 4 (data enrichment in DRAFT), 7 (doc-level verification), 9 (financials + consents added in VERIFIED)
- **Multi-state transitions**: Step 5 passes through DRAFT → IN_PROGRESS → READY_FOR_SUBMISSION → SUBMITTED. Step 10 passes through VERIFIED → DECISIONED → APPROVED.
- **Step 10 decision flow**: Automated engine → REFERRED_TO_UNDERWRITER (no credit bureau / null credit score), then underwriter manual override → APPROVED
- **Test data**: $650K loan, $900K property (72% LTV), $150K gross income, 360-month term, 5.99% variable rate, borrower Sarah Mitchell

## Implementation

- Backend: `WalkthroughService.java` + `WalkthroughController.java` (`@Profile("dev")`)
- Frontend: `src/main/resources/static/index.html` (Lifecycle Walkthrough tab)
- Tests: `e2e/observatory.spec.js` (Playwright)
