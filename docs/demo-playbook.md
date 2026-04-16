# Demo Playbook: LIXI2 Mortgage Platform

**Purpose**: Define the client demo experience and the specific engineering work to make it real.
**Audience**: Lenders, aggregators, fintechs evaluating the platform.
**Duration**: 20 minutes live (per track), self-serve version at dev.atheryon.ai.

---

## Two Demo Tracks

The platform serves two buying motions. Different audience, different story, different sale.

| Track | Audience | Their Problem | Duration |
|-------|----------|--------------|----------|
| **A: Origination** | Aggregators, brokers, digital lenders | "We need to process new applications faster" | 20 min |
| **B: Migration** | ADI lenders, core banking replacements, fintechs acquiring portfolios | "We have 50,000 loans in a legacy system and need to move them" | 20 min |

Track B is the bigger commercial opportunity. Every lender doing a core banking modernisation needs a migration partner. The demo must prove: **we can ingest your entire book, validate it against LIXI2, show you exactly what's wrong, fix it, and prove the migration is clean.**

---

# Track A: Origination — "Sarah's First Home"

Every demo tells one story: **Sarah Mitchell is buying her first home.**

A broker submits Sarah's application as a LIXI2 message. We follow it from raw JSON to settled loan in 20 minutes. The audience sees every layer: validation, data mapping, assessment, decision, real-time events, and the audit trail.

6 scenes, each ending with an "aha" moment.

---

## Scene 1: "Drop It In" — LIXI2 Ingestion

**What the client sees**: A broker pastes a LIXI2 CAL message and watches it validate in real time.

**Duration**: 3 minutes

**Flow**:
```
┌─────────────────────────────────────────────────────────────┐
│  ┌──────────────────────┐   ┌────────────────────────────┐  │
│  │                      │   │  VALIDATION PIPELINE        │  │
│  │   LIXI2 JSON         │   │                            │  │
│  │   (editable)         │   │  ┌──────────────────────┐  │  │
│  │                      │   │  │ ✅ Schema     4ms    │  │  │
│  │   {                  │   │  ├──────────────────────┤  │  │
│  │     "Package": {     │──►│  │ ✅ Schematron 47ms   │  │  │
│  │       "Content": {   │   │  ├──────────────────────┤  │  │
│  │         ...          │   │  │ ⚠️ Lender    3ms    │  │  │
│  │       }              │   │  ├──────────────────────┤  │  │
│  │     }                │   │  │ ✅ Domain    12ms    │  │  │
│  │   }                  │   │  └──────────────────────┘  │  │
│  │                      │   │                            │  │
│  │  [Load Sample ▼]     │   │  Overall: Valid ⚠️         │  │
│  │  [Validate]          │   │  66ms total                │  │
│  └──────────────────────┘   └────────────────────────────┘  │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  MAPPED APPLICATION PREVIEW                            │  │
│  │                                                        │  │
│  │  Sarah Mitchell  │  42 Blue Gum Dr  │  $680,000 loan  │  │
│  │  Age 41, PAYG    │  Randwick 2031   │  30yr variable  │  │
│  │  $120k income    │  Value: $850,000 │  LVR: 80%       │  │
│  │                                                        │  │
│  │  [Submit to Pipeline →]                                │  │
│  └────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

**"Aha" moment**: The audience sees their industry-standard LIXI2 message go through 1,600 Schematron rules in <100ms. The mapped preview shows it understood the message — names, addresses, loan terms, all extracted correctly.

**What exists today**:
- ✅ Validate page (`/mortgages/validate`) — 3-tier validation, 6 sample payloads, tier cards, rule-by-rule results
- ✅ Sample LIXI2 payloads (valid, warnings, invalid scenarios)
- ✅ Validation API endpoint (`/api/mortgages/validate`)

**What needs building**:

| Component | Description | Effort |
|-----------|------------|--------|
| **Mapped Preview Panel** | Below validation results, show the extracted application data as a human-readable card (borrower, property, loan) — not raw JSON | S |
| **"Submit to Pipeline" button** | Takes the validated LIXI2 message and creates an application, then navigates to Scene 2 (explorer) | S |
| **Lender selector** | Dropdown above validation: "Validating as: [CBA ▼]" — switches Tier 3 rules. Pre-load 2–3 lender profiles with different required fields | M |
| **Realistic sample** | Replace simplified samples with a full-depth LIXI2 CAL message (~200 fields) that exercises real Schematron rules | S |

**Demo script**:
> "This is a real LIXI2 CAL message — the same format every Australian broker platform uses. Watch what happens when I hit Validate."
>
> *[Click Validate — tiers animate green/amber one by one]*
>
> "Four tiers in 66 milliseconds. Schema structure, 1,600 Schematron rules from LIXI, lender-specific requirements from CBA, and our domain rules. Let me switch to Westpac..."
>
> *[Switch lender dropdown — Tier 3 changes from pass to warning]*
>
> "Same application, different lender. Westpac requires TFN for all PAYG applicants — CBA doesn't. The platform knows this because each lender publishes their requirements as a configuration profile."
>
> "Now let me submit this into the pipeline."

---

## Scene 2: "Watch It Come Alive" — Lifecycle Walkthrough

**What the client sees**: The application flows through the state machine, step by step. The diagram animates. Data grows at each step.

**Duration**: 5 minutes

**Flow**:
```
┌─────────────────────────────────────────────────────────────┐
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │              STATE MACHINE DIAGRAM                     │  │
│  │                                                        │  │
│  │    [DRAFT]──►[IN_PROGRESS]──►[READY]──►[SUBMITTED]    │  │
│  │       ●           ●             ●          ◉          │  │
│  │                                                        │  │
│  │    [ASSESSMENT]──►[VERIFIED]──►[DECISIONED]           │  │
│  │        ○              ○             ○                  │  │
│  │                                                        │  │
│  │    [APPROVED]──►[OFFER_ISSUED]──►[OFFER_ACCEPTED]     │  │
│  │        ○              ○               ○               │  │
│  │                                                        │  │
│  │    [SETTLEMENT_IN_PROGRESS]──►[SETTLED]               │  │
│  │              ○                     ○                   │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                              │
│  ◄ ① ② ③ ④ | ⑤ ⑥ ⑦ ⑧ | ⑨ ⑩ ⑪ | ⑫ ⑬ ⑭ ►  ▶ Auto-Play │
│    Setup     Assessment   Decision    Settlement            │
│                                                              │
│  ┌──────────────────────┐  ┌───────────────────────────┐    │
│  │  WHAT CHANGED         │  │  ACTIVITY LOG             │    │
│  │                       │  │                           │    │
│  │  + borrower.firstName │  │  10:41 Application        │    │
│  │  + borrower.income    │  │        submitted by       │    │
│  │  + property.address   │  │        Broker Portal      │    │
│  │  ~ status: SUBMITTED  │  │                           │    │
│  └──────────────────────┘  └───────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

**"Aha" moment**: This isn't a slideshow — it's a real state machine driving a real application through 14 steps. Click any completed node to inspect that point in time. Toggle Business mode and the technical jargon becomes human language.

**What exists today**:
- ✅ Full 14-step explorer (`/mortgages/explorer`) — interactive, polished
- ✅ State machine diagram (React Flow + Dagre)
- ✅ Phase-grouped transport bar with numbered dots
- ✅ Diff panel, JSON tree viewer, workflow timeline, API call display
- ✅ Technical / Business mode toggle
- ✅ Auto-play, keyboard navigation, back nav, history inspection
- ✅ Completion banner with metrics

**What needs building**:

| Component | Description | Effort |
|-----------|------------|--------|
| **Pre-loaded LIXI2 data** | Explorer currently uses generic sample data. Seed it with the Sarah Mitchell LIXI2 data from Scene 1 so the narrative is continuous | S |
| **"Ingested from LIXI2" badge** | Show in the step banner that step 1 came from a LIXI2 message (not manual API call) | XS |
| **Link from Scene 1** | "Submit to Pipeline" button in validate page creates the app and opens explorer at step 1 | S |

**Demo script**:
> "The application I just submitted is now in our state machine. DRAFT. Watch as I step through the lifecycle."
>
> *[Click through steps 1–4: Setup phase]*
>
> "Each step shows exactly what changed in the data, what API was called, and what events fired. Let me switch to Business mode..."
>
> *[Toggle to Business — labels change to friendly language]*
>
> "Same walkthrough, but in language a product manager or compliance officer can follow. Now let me auto-play through the assessment phase."
>
> *[Click Auto-Play — steps 5-8 animate]*

---

## Scene 3: "The Numbers" — Serviceability & Decision

**What the client sees**: Sarah's financials run through the assessment engine. Gauges animate. A decision is made with clear reasons.

**Duration**: 4 minutes

**Flow**:
```
┌─────────────────────────────────────────────────────────────┐
│  SERVICEABILITY ASSESSMENT                                   │
│                                                              │
│  ┌───────────┐  ┌───────────┐  ┌───────────┐               │
│  │    UMI    │  │    DSR    │  │    LTV    │               │
│  │           │  │           │  │           │               │
│  │   ┌───┐  │  │   ┌───┐  │  │   ┌───┐  │               │
│  │   │   │  │  │   │   │  │  │   │   │  │               │
│  │   │ █ │  │  │   │ █ │  │  │   │ █ │  │               │
│  │   │ █ │  │  │   │ █ │  │  │   │ █ │  │               │
│  │   │ █ │  │  │   │ █ │  │  │   │ █ │  │               │
│  │   └───┘  │  │   └───┘  │  │   └───┘  │               │
│  │  $1,247  │  │   32.1%  │  │    80%   │               │
│  │   PASS   │  │   PASS   │  │  No LMI  │               │
│  └───────────┘  └───────────┘  └───────────┘               │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  INCOME BREAKDOWN                                      │  │
│  │                                                        │  │
│  │  PAYG Salary          $120,000    shaded 100% │  │
│  │  Rental Income         $18,000    shaded  80% │  │
│  │  ─────────────────────────────────────────────         │  │
│  │  Gross Annual         $138,000                         │  │
│  │  Net Monthly (shaded)  $10,900                         │  │
│  │                                                        │  │
│  │  EXPENSES                                              │  │
│  │  Declared              $3,200/mo                       │  │
│  │  HEM Benchmark         $3,450/mo  ◄── HEM applied     │  │
│  │  Assessed (higher of)  $3,450/mo                       │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  DECISION: APPROVED                                    │  │
│  │                                                        │  │
│  │  ✅ Serviceability PASS (UMI > $500)                   │  │
│  │  ✅ LTV ≤ 80% (no LMI required)                       │  │
│  │  ✅ Credit score 745 (≥ 700 threshold)                 │  │
│  │  ✅ All documents verified                              │  │
│  │  ✅ Delegated authority: AUTO (Level 1)                 │  │
│  │                                                        │  │
│  │  Decision made automatically — no underwriter required  │  │
│  └────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

**"Aha" moment**: The platform doesn't just store data — it calculates. Income shading, HEM lookup, APRA buffer rates, auto-decision with transparent rules. No black box. Every number is traceable.

**What exists today**:
- ✅ ServiceabilityCalculator (backend — income shading, assessment rate, UMI, DSR)
- ✅ LtvCalculator (backend — bands, LMI threshold)
- ✅ DecisionEngine (backend — auto-approve, auto-decline, referral logic)
- ✅ Explorer shows the *results* of these calculations in the JSON tree
- ❌ No dedicated assessment visualization

**What needs building**:

| Component | Description | Effort |
|-----------|------------|--------|
| **Assessment Dashboard** | New page or panel: 3 gauge meters (UMI, DSR, LTV) + income breakdown table + expense comparison (declared vs HEM) + decision result with checklist | M |
| **Gauge component** | Reusable radial or bar gauge — value, threshold, pass/fail coloring. Use for UMI ($), DSR (%), LTV (%) | S |
| **Income shading table** | Show each income line, its type, gross amount, shading factor, and shaded amount | S |
| **HEM comparison** | Side-by-side: declared expenses vs HEM benchmark, highlight which was used | S |
| **Decision checklist** | Green/red checks for each auto-decision criterion, with the threshold values | S |
| **"What if" slider** | Optional: let the demo operator adjust loan amount or income and watch gauges update live. Instant recomputation. | M |

**Demo script**:
> "Now the assessment. Sarah earns $120k PAYG and $18k in rental income. Watch what happens to the numbers."
>
> *[Assessment dashboard animates — gauges fill up]*
>
> "Income shading: salary at 100%, rental at 80% — that's APRA's conservative view. Her declared expenses are $3,200 a month, but the HEM benchmark for her household is $3,450, so we use the higher number."
>
> "UMI: $1,247 surplus after all commitments. DSR: 32%. LTV: exactly 80%, so no LMI. All green."
>
> "The decision engine says: auto-approve at Level 1 delegated authority. No underwriter needed. Let me show you why..."
>
> *[Point to decision checklist — all 5 criteria green]*
>
> *[Optional: drag loan amount slider to $800k — LTV jumps to 94%, UMI drops, decision flips to REFERRED]*
>
> "Now it's referred to a senior underwriter. The rules are transparent — nothing hidden."

---

## Scene 4: "Your Rules, Your Way" — Lender Configuration

**What the client sees**: Switch between lender profiles and watch requirements, validation, and products change.

**Duration**: 3 minutes

**Flow**:
```
┌─────────────────────────────────────────────────────────────┐
│  LENDER CONFIGURATION                                        │
│                                                              │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐                       │
│  │   CBA   │ │   WBC   │ │  Macq   │  ← active profiles   │
│  │  ● ──── │ │  ○ ──── │ │  ○ ──── │                       │
│  └─────────┘ └─────────┘ └─────────┘                       │
│                                                              │
│  ┌──────────────────────┐  ┌───────────────────────────┐    │
│  │  REQUIRED FIELDS      │  │  PRODUCT RULES            │    │
│  │                       │  │                           │    │
│  │  ✅ TFN              │  │  Max LVR: 95%             │    │
│  │  ✅ Employment 2yr   │  │  Min loan: $100,000       │    │
│  │  ✅ 3 months payslips│  │  Max loan: $2,500,000     │    │
│  │  ⬚ ABN (if self-emp) │  │  LMI above: 80%          │    │
│  │  ✅ Centrelink CRN   │  │  Max term: 30 years       │    │
│  │                       │  │  Allowed: Owner-occ,      │    │
│  │  17 required fields   │  │           Investment      │    │
│  │  4 conditional fields │  │                           │    │
│  └──────────────────────┘  └───────────────────────────┘    │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  VALIDATION IMPACT (Sarah's application)               │  │
│  │                                                        │  │
│  │  CBA: ✅ All fields present — ready to submit          │  │
│  │  WBC: ⚠️ Missing: TFN, Centrelink CRN                 │  │
│  │  Macquarie: ❌ Property type not accepted               │  │
│  └────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

**"Aha" moment**: The same application succeeds or fails depending on the lender. The platform handles this per-lender divergence as configuration, not code. New lender = new JSON profile, not a new deployment.

**What exists today**:
- ✅ Validation pipeline supports configurable rules
- ❌ No lender profile UI
- ❌ No lender comparison view

**What needs building**:

| Component | Description | Effort |
|-----------|------------|--------|
| **Lender Config page** | `/mortgages/lenders` — list profiles, click to edit, field requirement CRUD | M |
| **Lender comparison panel** | Given an application, show pass/fail against 2–3 lenders side by side | M |
| **Seed data** | 3 pre-built lender profiles (CBA, WBC, Macquarie) with realistic field requirements and product rules | S |
| **Tier 3 lender rules** | Backend: load lender profile, evaluate against message (field presence + conditions) | M |

**Demo script**:
> "Every lender has different requirements. CBA wants Tax File Numbers for all applicants. Westpac wants 2 years of employment history. Macquarie won't lend on certain property types."
>
> "These aren't hardcoded — they're configuration profiles. Let me show you Sarah's application against all three..."
>
> *[Click each lender card — validation impact section updates]*
>
> "CBA: green. WBC: two missing fields. Macquarie: rejected outright because it's a studio apartment. The broker sees this before they submit — no more wasted lodgements."

---

## Scene 5: "It's Alive" — Real-Time Events

**What the client sees**: Two screens side by side. Lender approves, broker's screen updates instantly.

**Duration**: 3 minutes

**Flow**:
```
┌──── LENDER VIEW ────────────────┐  ┌──── BROKER VIEW ────────────────┐
│                                  │  │                                  │
│  Application: ATH-2026-000847   │  │  Application: ATH-2026-000847   │
│  Status: UNDER_ASSESSMENT       │  │  Status: UNDER_ASSESSMENT       │
│                                  │  │                                  │
│  [Complete Verification]         │  │  ┌──────────────────────────┐   │
│  [Run Decision]                  │  │  │  EVENT FEED (live)       │   │
│  [Generate Offer]                │  │  │                          │   │
│                                  │  │  │  ● Waiting for updates   │   │
│     * clicks "Run Decision" *    │  │  │    ...                   │   │
│                                  │  │  │                          │   │
│  ✅ Decision: APPROVED           │  │  │  ● 10:42:03 Decision     │   │
│     Auto, Level 1 authority     │  │  │    APPROVED               │   │
│                                  │  │  │    Auto, Level 1          │   │
│     * clicks "Generate Offer" *  │  │  │                          │   │
│                                  │  │  │  ● 10:42:08 Offer        │   │
│  📄 Offer generated:            │  │  │    $680,000 at 5.89%     │   │
│     $680,000 @ 5.89%, 30yr      │  │  │    30 year variable      │   │
│                                  │  │  │                          │   │
│                                  │  │  │  ● 10:42:08 Status       │   │
│                                  │  │  │    → OFFER_ISSUED        │   │
│                                  │  │  └──────────────────────────┘   │
│                                  │  │                                  │
│                                  │  │  [Accept Offer]                 │
└──────────────────────────────────┘  └──────────────────────────────────┘
```

**"Aha" moment**: Real-time. No polling, no refresh, no "check back in 24 hours." The broker sees state transitions the moment they happen.

**What exists today**:
- ✅ Explorer shows workflow events per step
- ✅ WorkflowEvent model exists in backend
- ❌ No SSE endpoint
- ❌ No live event feed component
- ❌ No split-screen demo mode

**What needs building**:

| Component | Description | Effort |
|-----------|------------|--------|
| **SSE endpoint** | `GET /api/v1/events/stream?applicationId=X` — Spring SseEmitter pushing domain events | M |
| **Event feed component** | Live-updating event list: timestamp, event type, detail. Pill animation on new events. | M |
| **Broker view page** | `/mortgages/broker/{id}` — simplified view: status badge, event feed, action buttons (accept offer) | M |
| **Split-screen demo layout** | Side-by-side iframe or route layout for presenting lender + broker simultaneously | S |

**Demo script**:
> "Now I'll show you what the broker sees. I'm opening two windows — lender on the left, broker on the right."
>
> *[Side-by-side layout]*
>
> "The broker's event feed is connected via Server-Sent Events. Watch the right side."
>
> *[Click "Run Decision" on lender side — broker side animates new event]*
>
> "Instant. No API polling. No webhook delay. The broker knew Sarah was approved before the lender finished clicking."
>
> *[Click "Generate Offer" — broker sees offer details appear]*
>
> "And now the broker can accept on Sarah's behalf."

---

## Scene 6: "The Audit Trail" — Compliance & Completeness

**What the client sees**: Full event history, round-trip LIXI2 fidelity, compliance dashboard.

**Duration**: 2 minutes

**Flow**:
```
┌─────────────────────────────────────────────────────────────┐
│  AUDIT TRAIL — ATH-2026-000847                               │
│                                                              │
│  14 state transitions │ 23 events │ 4 LIXI2 messages         │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  LIXI2 ROUND-TRIP                                      │  │
│  │                                                        │  │
│  │  Inbound CAL (broker)          Outbound CAL (emitted)  │  │
│  │  ┌──────────────────┐          ┌──────────────────┐    │  │
│  │  │ 247 fields       │   DIFF   │ 247 fields       │    │  │
│  │  │ submitted        │◄────────►│ emitted          │    │  │
│  │  └──────────────────┘          └──────────────────┘    │  │
│  │                                                        │  │
│  │  Field fidelity: 100% (0 fields lost)                  │  │
│  │  Added by platform: 12 fields (decision, offer, etc.)  │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  EVENT TIMELINE                                        │  │
│  │                                                        │  │
│  │  10:40:01  LIXI2 CAL received from NextGen             │  │
│  │  10:40:01  Validated (4 tiers, 66ms)                   │  │
│  │  10:40:02  Application created: DRAFT                  │  │
│  │  10:40:02  Borrower added: Sarah Mitchell              │  │
│  │  10:40:03  Property added: 42 Blue Gum Dr              │  │
│  │    ...                                                  │  │
│  │  10:42:03  Decision: APPROVED (auto, Level 1)          │  │
│  │  10:42:08  Offer issued: $680k @ 5.89%                 │  │
│  │  10:42:15  Offer accepted by broker                    │  │
│  │  10:43:01  Settlement completed                        │  │
│  │  10:43:02  LIXI2 status message emitted                │  │
│  └────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

**"Aha" moment**: Every field that came in went out. Every state transition is recorded. Every decision is traceable. This is what APRA and ASIC want to see.

**What exists today**:
- ✅ Explorer shows workflow events and state diffs per step
- ✅ WorkflowEvent entities with actor, timestamps, before/after state
- ❌ No round-trip diff viewer (inbound vs outbound LIXI2)
- ❌ No audit timeline view (cross-step)

**What needs building**:

| Component | Description | Effort |
|-----------|------------|--------|
| **Round-trip diff viewer** | Side-by-side: original inbound LIXI2 vs emitted LIXI2, with field-level diff highlighting | M |
| **Full audit timeline** | Single scrollable timeline across all 14 steps — every event, every actor, every timestamp. Filterable by event type. | M |
| **Compliance summary** | Top-line metrics: total transitions, total events, LIXI2 messages in/out, field fidelity percentage | S |

---

---

# Track B: Migration — "Move the Book"

Different story, different protagonist. This time it's **the CTO of a mid-tier lender** who has 47,000 active loans in a 20-year-old core banking system and needs to migrate to a modern platform.

Their fears:
- "Will we lose data?"
- "How do we know the migrated data is correct?"
- "What percentage of our book won't conform to LIXI2?"
- "How long will remediation take?"
- "What do we show the auditors?"

5 scenes. Each one directly addresses a fear.

---

## Scene M1: "Pour It In" — Bulk Ingest

**What the client sees**: Upload a CSV of 10,000 loans. Watch them parse, validate, and classify in real time.

**Duration**: 3 minutes

**Flow**:
```
┌─────────────────────────────────────────────────────────────┐
│  MIGRATION WORKSPACE                                         │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  ┌──────┐                                              │  │
│  │  │ CSV  │  legacy-loan-extract.csv                     │  │
│  │  │      │  47,312 rows × 83 columns × 14.2 MB         │  │
│  │  └──────┘                                              │  │
│  │                                                        │  │
│  │  [Upload & Parse]                                      │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  PIPELINE PROGRESS                                     │  │
│  │                                                        │  │
│  │  ① Ingest    ████████████████████████████████ 47,312   │  │
│  │  ② Parse     ████████████████████████████░░░░ 43,891   │  │
│  │  ③ Map       ████████████████████░░░░░░░░░░░ 31,204   │  │
│  │  ④ Validate  ██████████████░░░░░░░░░░░░░░░░░ 22,107   │  │
│  │  ⑤ Classify  running...                                │  │
│  │                                                        │  │
│  │  Speed: 1,847 loans/sec │ ETA: 24 seconds              │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  FIELD MAPPING PREVIEW (auto-detected)                 │  │
│  │                                                        │  │
│  │  Legacy Column        → LIXI2 Field           Match    │  │
│  │  ───────────────────────────────────────────────────── │  │
│  │  LOAN_ACCT_NUM        → LoanDetails/@UniqueID    98%   │  │
│  │  BORROWER_NAME        → PersonApplicant/Name     95%   │  │
│  │  PROP_ADDRESS         → RealEstateAsset/Address  91%   │  │
│  │  ORIG_AMOUNT          → LoanDetails/LoanAmount   99%   │  │
│  │  CURRENT_RATE         → LoanDetails/InterestRate 97%   │  │
│  │  SETTLEMENT_DT        → LoanDetails/SettledDate  94%   │  │
│  │  ⚠ LMI_FLAG           → (unmapped — 3 candidates) ??   │  │
│  │  ⚠ INTERNAL_SCORE      → (no match)                ❌   │  │
│  └────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

**"Aha" moment**: It didn't just load the file — it *understood* it. Auto-detected 81 of 83 columns and mapped them to LIXI2 fields. The 2 unmapped columns are flagged for the operator.

**What exists today**:
- ✅ CSV parser with smart type detection (numerics, dates, booleans) — `lib/pipeline/parsers/csv.ts`
- ✅ Bulk upload endpoint (`/api/trades/upload`) — validates, previews, reports errors
- ✅ Bulk import endpoint (`/api/trades/import`) — writes to database
- ✅ Pipeline store with 5-step flow state (Zustand)
- ✅ Pipeline type definitions with `NormalizedRecord`, `TransformResult`, `CoverageReport`
- ⚠️ All wired to trades/derivatives, not mortgages

**What needs building**:

| Component | Description | Effort |
|-----------|------------|--------|
| **Mortgage CSV parser profile** | Extend CSV parser with mortgage-specific column detection (loan_amount, lvr, borrower_name, property_address, etc.) | S |
| **LIXI2 field auto-mapper** | Given a CSV header, suggest the best LIXI2 CAL field match using fuzzy matching + known synonyms | M |
| **Migration workspace page** | `/mortgages/migrate` — upload, pipeline progress, field mapping preview | M |
| **Pipeline progress component** | Animated bars showing rows processed per stage, speed, ETA | S |
| **Seed data** | Generate a realistic 10,000-loan CSV with known quality issues (missing fields, outliers, format inconsistencies) | S |

**Demo script**:
> "Your legacy system can export a CSV. Maybe it's from a 20-year-old core banking platform, maybe it's an Excel dump from a broker aggregation tool. Either way, we can take it."
>
> *[Drag CSV file into upload zone]*
>
> "47,000 loans. Watch the pipeline."
>
> *[Progress bars animate — 1,800 loans/sec]*
>
> "Auto-field-mapping: it matched 81 of 83 columns to LIXI2 fields. The two it couldn't match — your internal credit score and an LMI flag with a non-standard format — are flagged for you. Nothing silently dropped."

---

## Scene M2: "The Truth" — Quality Dashboard

**What the client sees**: Aggregate quality metrics across the entire book. Where the data is clean, where it's broken, and exactly how broken.

**Duration**: 4 minutes

**Flow**:
```
┌─────────────────────────────────────────────────────────────┐
│  MIGRATION QUALITY DASHBOARD                                 │
│                                                              │
│  47,312 loans │ $8.7B notional │ Migrated: 15 Apr 2026      │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  OVERALL QUALITY                                      │   │
│  │                                                       │   │
│  │  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐   │   │
│  │  │  87.3%  │ │  94.1%  │ │  91.6%  │ │  98.2%  │   │   │
│  │  │Complete │ │Accurate │ │Consist. │ │ Valid  │   │   │
│  │  │ ness   │ │  ness   │ │  ency   │ │  ity   │   │   │
│  │  └─────────┘ └─────────┘ └─────────┘ └─────────┘   │   │
│  │                                                       │   │
│  │  Composite score: 92.8%                               │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  CLASSIFICATION                                       │   │
│  │                                                       │   │
│  │  ██████████████████████████████████  38,412  Clean    │   │
│  │  ██████████░░░░░░░░░░░░░░░░░░░░░░░   6,203  Warning  │   │
│  │  ███░░░░░░░░░░░░░░░░░░░░░░░░░░░░░   2,697  Failed   │   │
│  │                                                       │   │
│  │  81.2% ready to go │ 13.1% fixable │ 5.7% need review│   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  FIELD COVERAGE HEATMAP                               │   │
│  │                                                       │   │
│  │  Field                     Coverage  Status            │   │
│  │  ────────────────────────────────────────────────────  │   │
│  │  LoanDetails/LoanAmount     100.0%   ████████████ ✅  │   │
│  │  LoanDetails/InterestRate    99.8%   ███████████░ ✅  │   │
│  │  PersonApplicant/Name        99.2%   ███████████░ ✅  │   │
│  │  PersonApplicant/DOB         97.1%   ██████████░░ ✅  │   │
│  │  PersonApplicant/Income      91.4%   █████████░░░ ⚠️  │   │
│  │  RealEstateAsset/Address     88.7%   ████████░░░░ ⚠️  │   │
│  │  PersonApplicant/TFN         72.3%   ███████░░░░░ ⚠️  │   │
│  │  Insurance/LMI               45.1%   ████░░░░░░░░ ❌  │   │
│  │  PersonApplicant/Employment  41.8%   ████░░░░░░░░ ❌  │   │
│  │  LoanDetails/RepayFreq       38.2%   ███░░░░░░░░░ ❌  │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  TOP RULE FAILURES                                    │   │
│  │                                                       │   │
│  │  MR-008  LMI consistency (LVR>80 but no LMI)  4,219  │   │
│  │  MR-007  No borrower income data               3,891  │   │
│  │  MR-002  Rate out of bounds                    1,247  │   │
│  │  MR-003  Variable rate missing index/margin      893  │   │
│  │  MR-001  Principal ≤ 0                            12  │   │
│  │                                                       │   │
│  │  [Drill into failures →]                              │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

**"Aha" moment**: In 30 seconds, the CTO sees exactly how healthy their book is. 81% is clean. 13% has fixable issues. 5.7% needs manual review. They know the remediation effort before it starts.

**What exists today**:
- ✅ Quality dashboard page (`/analyse/quality`) — 5 quality dimensions with color-coded gauges
- ✅ Issue tracker by field — severity levels, affected percentages
- ✅ Dashboard stats page (`/analyse/dashboard`) — metrics, trends, breakdown charts
- ✅ Analytics hooks (`useDashboardStats`, `useDataQuality`) — memoized data fetching
- ✅ 15-rule quality engine (`lib/validation/quality-rules.ts`)
- ✅ Recharts for charting
- ⚠️ All wired to trade/derivatives quality dimensions, not mortgage rules

**What needs building**:

| Component | Description | Effort |
|-----------|------------|--------|
| **Mortgage quality rules** | Port MR-001..MR-010 into the quality engine. Add mortgage-specific: LVR consistency, income completeness, address validation, employment history depth | M |
| **Field coverage heatmap** | For each LIXI2 field, calculate % of loans that have it populated. Ranked list with coverage bars. | S |
| **Classification summary** | Clean / Warning / Failed breakdown with counts and percentages. Stacked bar chart. | S |
| **Top failures view** | Aggregate rule failures across the book, ranked by frequency. Clickable → drill into Scene M3. | S |
| **Migration quality page** | `/mortgages/migrate/quality` — compose the above into a single dashboard, connected to the migration workspace | M |

**Demo script**:
> "47,000 loans just landed. Here's the truth about your data."
>
> *[Quality dashboard animates — gauges fill]*
>
> "87.3% completeness. That means 12.7% of your loans are missing at least one field that LIXI2 considers important. Let's see which fields."
>
> *[Scroll to field coverage heatmap]*
>
> "Loan amount: 100%. Good. Borrower name: 99.2%. Fine. But employment history: 41.8%. That's a problem — most lenders require it for serviced loans. And LMI: 45% — your legacy system probably didn't track this consistently."
>
> *[Point to top rule failures]*
>
> "4,219 loans have LVR above 80% but no LMI flag. That's either missing data or a real compliance issue. Let me drill in."

---

## Scene M3: "The Drill-Down" — Failure Analysis

**What the client sees**: Click a rule failure, see the exact loans, understand the pattern, decide the fix.

**Duration**: 4 minutes

**Flow**:
```
┌─────────────────────────────────────────────────────────────┐
│  RULE FAILURE: MR-008 — LMI Consistency                      │
│  4,219 loans │ Severity: Warning │ Remediation: AUTO possible│
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  PATTERN ANALYSIS                                      │  │
│  │                                                        │  │
│  │  LVR Distribution of Failing Loans:                    │  │
│  │                                                        │  │
│  │  80-85%  ██████████████████████████  2,847 (67%)       │  │
│  │  85-90%  ██████████░░░░░░░░░░░░░░░░  1,102 (26%)       │  │
│  │  90-95%  ███░░░░░░░░░░░░░░░░░░░░░░    258 (6%)        │  │
│  │  95%+    █░░░░░░░░░░░░░░░░░░░░░░░░     12 (0.3%)      │  │
│  │                                                        │  │
│  │  Hypothesis: Legacy system didn't track LMI for loans  │  │
│  │  originated before 2018 core upgrade.                  │  │
│  │                                                        │  │
│  │  Originated before 2018:  3,974 / 4,219 (94.2%)       │  │
│  │  Originated after 2018:     245 / 4,219 (5.8%)        │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  SAMPLE LOANS                                          │  │
│  │                                                        │  │
│  │  Loan ID       Borrower        LVR    LMI    Origin   │  │
│  │  ──────────────────────────────────────────────────── │  │
│  │  ACT-0042817   J. Thompson     82%    null   2016     │  │
│  │  ACT-0042923   M. Fernandez    87%    null   2015     │  │
│  │  ACT-0043101   S. Park         91%    null   2017     │  │
│  │  ACT-0044256   R. Williams     84%    null   2019  ⚠️ │  │
│  │  ...                                                   │  │
│  │                                  Showing 10 of 4,219   │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  REMEDIATION OPTIONS                                   │  │
│  │                                                        │  │
│  │  ○ Set LMI = 'Y' for all 4,219 loans (bulk)           │  │
│  │  ○ Set LMI = 'Y' where LVR > 80% AND origin < 2018   │  │
│  │  ● Set LMI = 'Y' where LVR > 80%, flag post-2018     │  │
│  │    for manual review (245 loans)                       │  │
│  │  ○ Export to CSV for offline review                    │  │
│  │                                                        │  │
│  │  [Preview Impact]  [Apply Fix]                         │  │
│  └────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

**"Aha" moment**: It's not just flagging problems — it's *diagnosing* them. The pattern analysis found that 94% of failures are pre-2018 originations, suggesting a legacy system gap. And it offers a smart fix: bulk remediate the obvious ones, flag the outliers for human review.

**What exists today**:
- ✅ Quality rules engine returns per-loan, per-rule results
- ✅ Issue tracker shows affected fields
- ❌ No pattern analysis (distribution charts, temporal correlation)
- ❌ No remediation workflow
- ❌ No bulk fix + re-validate cycle

**What needs building**:

| Component | Description | Effort |
|-----------|------------|--------|
| **Failure drill-down page** | `/mortgages/migrate/failures/{ruleId}` — pattern analysis chart + sample table + remediation options | M |
| **Pattern analysis** | For a given rule failure set: LVR distribution, origination date distribution, lender distribution. Auto-generate hypothesis. | M |
| **Remediation engine** | Apply bulk transforms (set field, conditional set, flag for review). Re-validate affected loans. Show before/after quality delta. | L |
| **Sample loan table** | Paginated, sortable, filterable table of failing loans. Click to see full loan detail. | S |

**Demo script**:
> "4,219 loans failing MR-008: they have LVR above 80% but no LMI flag. Let's understand why."
>
> *[Click into MR-008 drill-down]*
>
> "Look at the distribution: 67% are in the 80-85% LVR band. And 94% were originated before 2018. Your core banking upgrade in 2018 started tracking LMI — before that, it wasn't captured."
>
> "So the fix is straightforward: set LMI = Y for the pre-2018 loans, and flag the 245 post-2018 loans for manual review."
>
> *[Select option 3, click "Preview Impact"]*
>
> "If I apply this fix, MR-008 failures drop from 4,219 to 245. Quality score goes from 92.8% to 96.1%. Let me apply it."

---

## Scene M4: "Before and After" — Remediation Impact

**What the client sees**: Apply the fix, re-validate, watch the quality dashboard update.

**Duration**: 2 minutes

**Flow**:
```
┌─────────────────────────────────────────────────────────────┐
│  REMEDIATION APPLIED                                         │
│                                                              │
│  Rule: MR-008 (LMI Consistency)                              │
│  Action: Set LMI='Y' where LVR>80% AND origin<2018          │
│  Affected: 3,974 loans                                       │
│  Flagged for review: 245 loans                               │
│                                                              │
│  ┌──────────────────────┐  ┌──────────────────────┐         │
│  │  BEFORE               │  │  AFTER                │         │
│  │                       │  │                       │         │
│  │  Clean:    38,412     │  │  Clean:    42,141     │         │
│  │  Warning:   6,203     │  │  Warning:   2,474     │         │
│  │  Failed:    2,697     │  │  Failed:    2,697     │         │
│  │                       │  │                       │         │
│  │  Quality:   92.8%     │  │  Quality:   96.1%     │         │
│  │                       │  │                       │         │
│  │  MR-008:    4,219  ❌ │  │  MR-008:      245  ⚠️│         │
│  └──────────────────────┘  └──────────────────────┘         │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  REMEDIATION LOG (audit trail)                         │  │
│  │                                                        │  │
│  │  15 Apr 2026 10:52:03  Bulk fix applied by T.Tsakiris │  │
│  │  Rule: MR-008 │ Loans: 3,974 │ Action: SET LMI='Y'    │  │
│  │  Condition: LVR>80% AND origination_date<2018-01-01    │  │
│  │  245 loans flagged for manual review                   │  │
│  │                                                        │  │
│  │  [Download remediation report (PDF)]                   │  │
│  └────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

**"Aha" moment**: Before/after side by side. Quality jumped from 92.8% to 96.1%. Every fix is logged with who, when, what, and why — the audit trail writes itself.

**What exists today**:
- ❌ No before/after comparison
- ❌ No remediation log

**What needs building**:

| Component | Description | Effort |
|-----------|------------|--------|
| **Before/after comparison** | Snapshot quality metrics before fix, re-run after, show delta side by side | M |
| **Remediation audit log** | Append-only log of all bulk fixes: who, when, rule, condition, count. Downloadable PDF. | S |

---

## Scene M5: "The Proof" — Reconciliation Report

**What the client sees**: Source vs target reconciliation. Every number matches. Auditor-ready PDF.

**Duration**: 3 minutes

**Flow**:
```
┌─────────────────────────────────────────────────────────────┐
│  MIGRATION RECONCILIATION                                    │
│                                                              │
│  Source: legacy-loan-extract.csv                             │
│  Target: Atheryon Mortgage Platform                          │
│  Date: 15 April 2026                                         │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  RECORD-LEVEL                                          │  │
│  │                                                        │  │
│  │  Source records:     47,312                             │  │
│  │  Target records:     47,312       ✅ 100% match        │  │
│  │  Rejected:               0                             │  │
│  │  Duplicates removed:     0                             │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  FINANCIAL RECONCILIATION                              │  │
│  │                                                        │  │
│  │  Metric           Source          Target       Delta   │  │
│  │  ────────────────────────────────────────────────────  │  │
│  │  Total notional    $8,714,302,450  $8,714,302,450  $0  │  │
│  │  Avg loan amount   $184,178        $184,178        $0  │  │
│  │  Avg interest rate  5.41%          5.41%          0bp  │  │
│  │  Avg LVR            71.3%          71.3%         0.0%  │  │
│  │  LMI count          12,847         16,821      +3,974  │  │
│  │                     (remediated — see audit log)    ✅  │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  FIELD-LEVEL RECONCILIATION                            │  │
│  │                                                        │  │
│  │  Fields mapped:        81/83 (97.6%)                   │  │
│  │  Fields 100% match:    78/81 (96.3%)                   │  │
│  │  Fields transformed:    3/81 (3.7%)                    │  │
│  │    - InterestRate: converted from monthly → annual     │  │
│  │    - Address: normalized to LIXI2 structured format    │  │
│  │    - Date: converted from DD/MM/YYYY → ISO 8601        │  │
│  │  Fields unmapped:       2/83 (not in LIXI2 standard)   │  │
│  │    - INTERNAL_SCORE → preserved in metadata.legacy     │  │
│  │    - BRANCH_CODE → preserved in metadata.legacy        │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  LIXI2 COMPLIANCE                                      │  │
│  │                                                        │  │
│  │  Loans fully LIXI2 compliant:     42,141 (89.1%)      │  │
│  │  Loans compliant with warnings:    2,474 (5.2%)       │  │
│  │  Loans pending manual review:      2,697 (5.7%)       │  │
│  │                                                        │  │
│  │  [Download Reconciliation Report (PDF)]                │  │
│  │  [Export Clean Book as LIXI2 CAL (JSON)]               │  │
│  └────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

**"Aha" moment**: The numbers match. Record count: exact. Total notional: to the cent. The 3 field transformations are documented. The 2 unmapped fields aren't lost — they're preserved in metadata. This is what the auditor signs off on.

**What exists today**:
- ✅ Trade import pipeline with record counts
- ❌ No reconciliation engine
- ❌ No financial reconciliation (sum/avg matching)
- ❌ No field-level reconciliation
- ❌ No PDF report generation

**What needs building**:

| Component | Description | Effort |
|-----------|------------|--------|
| **Reconciliation engine** | Compare source CSV aggregates (count, sum, avg) against target database. Field-level: source column vs LIXI2 field, exact match vs transformed. | M |
| **Reconciliation page** | `/mortgages/migrate/reconciliation` — record-level, financial, field-level, compliance sections | M |
| **PDF report generator** | Export reconciliation + remediation log as a branded PDF. Auditor-ready. | M |
| **LIXI2 bulk export** | Export N loans as LIXI2 CAL JSON (one per file or NDJSON stream) | S |

**Demo script**:
> "The migration is done. Now prove it."
>
> "Record count: 47,312 in, 47,312 out. Not one lost."
>
> "Total notional: $8.71 billion. Matches to the cent."
>
> *[Scroll to field reconciliation]*
>
> "3 fields were transformed: rates from monthly to annual, addresses to LIXI2 structured format, dates to ISO 8601. All documented. 2 fields don't exist in LIXI2 — your internal credit score and branch code — but they're not lost. They're preserved in a legacy metadata field."
>
> *[Click "Download Reconciliation Report"]*
>
> "Hand this PDF to your auditor. Every number, every transform, every remediation decision — traceable."

---

## Track B: Build Priority Matrix

| Priority | Component | Scene | Effort | Impact | Notes |
|----------|-----------|-------|--------|--------|-------|
| **1** | Mortgage quality rules + field coverage heatmap | M2 | M | Critical | This is what they buy. Quality visibility over their book. |
| **2** | Migration workspace (upload + pipeline progress) | M1 | M | Critical | The entry point. Must be smooth. |
| **3** | Classification summary (clean/warn/fail) | M2 | S | Critical | The single most important number: "81.2% ready" |
| **4** | Failure drill-down + sample table | M3 | M | High | Proves depth, not just surface metrics |
| **5** | Reconciliation page (record + financial) | M5 | M | High | The auditor story. Closes the compliance-conscious buyer. |
| **6** | Before/after comparison | M4 | M | High | Proves remediation works |
| **7** | Remediation engine (bulk fix + re-validate) | M3→M4 | L | High | The "fix" story, not just the "find" story |
| **8** | LIXI2 field auto-mapper | M1 | M | Medium | Impressive but can be faked with pre-mapped seed data |
| **9** | PDF report generator | M5 | M | Medium | Important for self-serve; can do manually in demos |
| **10** | Pattern analysis (distribution, temporal) | M3 | M | Medium | Smart diagnostics — differentiator |
| **11** | LIXI2 bulk export | M5 | S | Low | Straightforward engineering |
| **12** | Seed data (10k loans with known issues) | All | S | Critical | Can't demo without it |

### Minimum Viable Migration Demo (Items 1–6 + 12)

~5 weeks of engineering. After this, the demo flow is:
1. Upload CSV → watch pipeline parse 10,000 loans
2. Quality dashboard: 87% complete, 94% accurate, field coverage heatmap
3. Click top failure → see failing loans, understand the pattern
4. Apply bulk fix → before/after quality improvement
5. Reconciliation: every number matches
6. "Here's the PDF for your auditor"

---

## Shared Infrastructure (Both Tracks)

Components that serve both origination and migration demos:

| Component | Track A | Track B |
|-----------|---------|---------|
| LIXI2 validation pipeline (4 tiers) | Scene 1 | Scene M2 (at scale) |
| Quality rules engine (MR-001..MR-010) | Scene 3 | Scene M2, M3 |
| Lender profiles (per-lender requirements) | Scene 4 | Scene M2 (lender-specific quality) |
| LIXI2 field mapping | Scene 1 (single) | Scene M1 (bulk auto-map) |
| Event store / audit trail | Scene 5, 6 | Scene M4 (remediation log) |

Build the shared pieces first. They pay off in both tracks.

---

## Combined Build Priority (Both Tracks)

| Week | Track A | Track B | Shared |
|------|---------|---------|--------|
| 1–2 | — | Seed data (10k CSV) | Mortgage quality rules, field coverage calculation |
| 3–4 | Assessment dashboard (gauges) | Migration workspace + pipeline progress | — |
| 5–6 | Lender profiles + comparison | Quality dashboard (mortgage-specific) + classification | — |
| 7–8 | SSE + event feed | Failure drill-down + sample table | — |
| 9–10 | Mapped preview + broker view | Reconciliation page + before/after | — |
| 11–12 | — | Remediation engine + PDF export | — |

**Week 6**: Both tracks are demo-able.
**Week 10**: Both tracks are polished.
**Week 12**: Migration track has full remediation + audit-ready reporting.

---

## Demo Environment Checklist

| Item | Status | Action |
|------|--------|--------|
| dev.atheryon.ai accessible | ✅ | Deployed via GitHub Actions |
| mortgages-platform running | ⚠️ | Need stable backend on dev VM (currently local-only) |
| Sample data: Sarah Mitchell | ❌ | Seed realistic LIXI2 CAL message + 3 lender profiles |
| Sample data: 10k loan CSV | ❌ | Generate with known quality issues (MR-008, MR-007 failures, missing fields) |
| Network resilience | ❌ | Add loading states, offline fallback, graceful error messages |
| Mobile responsive | ✅ | Explorer and validate page already responsive |
| Dark mode | ✅ | Already dark-themed (Atheryon design system) |

---

## Self-Serve Demo Mode

For prospects exploring without a live presenter:

```
/mortgages/demo          → Track A (origination)
/mortgages/demo/migrate  → Track B (migration)
```

Guided walkthrough with narrated annotations, tooltips, and a CTA at the end.

**Effort**: L (builds on all the above, adds tutorial overlay)
**Priority**: After minimum viable demos for both tracks are solid.
