# InvoiceIQ — AI-Powered Invoice & Expense Intelligence Platform

InvoiceIQ turns raw invoices, receipts, and expense documents into structured,
validated, analyzable financial data — with AI-assisted extraction, risk
scoring, duplicate/anomaly detection, and finance analytics, built as a
multi-tenant B2B SaaS product.

> **Build status:** Built incrementally, phase by phase, checked in at
> each milestone. Every phase in the [Roadmap](#roadmap) is complete —
> this covers the full core financial workflow (auth, invoices, document
> intelligence, validation/risk, approvals/payments/budgets, analytics/
> forecasting) plus a live action dashboard and CSV exports. Email
> notifications, a real LLM provider, natural-language search, and a
> Finance Copilot were deliberately cut from scope (see
> [Known limitations](#known-limitations-current-phase)) to keep the
> build finished and runnable rather than open-ended.

## Technology stack

| Layer | Technology |
|---|---|
| Frontend | React 18, TypeScript, Vite, React Router, Material UI, TanStack Query, React Hook Form, Zod, Recharts |
| Backend | Java 21, Spring Boot 3, Spring Security, Spring Data JPA, JWT, Flyway, springdoc-openapi |
| Database | PostgreSQL 16 |
| Cache / jobs | Redis 7 |
| OCR | Apache Tika (MIME sniffing) + Apache PDFBox (text/rendering) + Tesseract CLI (image OCR), behind `OcrService` — degrades to "needs review" if the `tesseract` binary isn't present |
| AI | Provider-agnostic `AiExtractionService` interface — a deterministic regex/heuristic extractor today (`MockAiExtractionService`); a real LLM-backed implementation can be added behind the same interface without touching the pipeline |
| Infra | Docker, Docker Compose |

## Project structure

```
invoiceiq/
├── backend/                  Spring Boot API (Maven)
│   └── src/main/java/com/invoiceiq/
│       ├── config/            Security, CORS, OpenAPI wiring
│       ├── controller/        REST controllers
│       ├── service/           Business logic
│       ├── repository/        Spring Data JPA repositories
│       ├── entity/            JPA entities (never exposed directly via API)
│       ├── dto/                Request/response models
│       ├── mapper/            Entity <-> DTO mapping (MapStruct)
│       ├── security/          JWT filters, auth context, tenant resolution
│       ├── exception/         ApiException hierarchy + global handler
│       ├── validation/        Deterministic invoice/tax validation engine
│       ├── ai/                AI extraction abstraction (regex/heuristic mock today)
│       ├── ocr/               OCR abstraction (Tesseract CLI, graceful degrade)
│       ├── processing/        Document intelligence pipeline (PDF text, classification, orchestration)
│       ├── risk/               Duplicate/anomaly/recurring-expense detection, explainable risk scoring
│       ├── analytics/         Expense/vendor/category spend aggregation
│       ├── forecast/          Cash-flow forecast (committed money) + naive monthly projection
│       ├── export/            CSV export formatting
│       ├── audit/             Audit logging
│       └── storage/           Document storage abstraction (local disk today)
├── frontend/                 React + TypeScript SPA (Vite)
│   └── src/
│       ├── api/                HTTP client, React Query setup
│       ├── layouts/            App shell (sidebar/topbar)
│       ├── pages/               Route-level pages
│       ├── components/          Shared UI components
│       ├── features/            Domain feature modules
│       ├── hooks/, services/, types/, utils/
├── docker-compose.yml
├── .env.example
└── README.md
```

## Why this architecture

- **DTOs, never entities, over the wire.** JPA entities are internal;
  controllers only ever see/return DTOs, so schema changes don't leak into
  the API contract.
- **AI is an interface, not a dependency.** `AIService` and `OCRService` are
  abstractions with a local, deterministic mock implementation. The whole
  pipeline — upload → OCR → extraction → validation → risk scoring — works
  end-to-end with **zero external API keys**. A real LLM provider can be
  dropped in behind the same interface later by setting `AI_PROVIDER` and
  `AI_API_KEY`.
- **Redis, not Kafka.** At this scale (single-org background job status,
  caching analytics), a message broker like Kafka would be resume-driven
  complexity with no real benefit. Redis-backed job status plus Spring's
  `@Async`/`@Scheduled` cover the actual requirement: fire-and-poll
  background processing.
- **Flyway owns the schema.** Hibernate is set to `ddl-auto: validate` —
  schema changes always go through a reviewable, versioned migration, never
  silent auto-DDL.
- **Tenant isolation is a backend concern.** Organization ID is always
  derived from the authenticated user's security context server-side
  (`CurrentUser`, populated by `JwtAuthenticationFilter` from the validated
  JWT claims), never from a client-supplied ID or request path. No
  organization-scoped endpoint even accepts an organization ID as input —
  there is structurally no way to ask for another tenant's data.
- **Refresh tokens are opaque, not JWTs.** Access tokens are short-lived
  (15 min) signed JWTs; refresh tokens are random opaque strings, hashed
  (SHA-256) before being stored, so they can be revoked server-side. Refresh
  rotates on every use; reuse of an already-rotated token is treated as
  theft and revokes every active session for that user.
- **CORS must be configured at the Spring Security layer, not just Spring
  MVC.** Spring Security's authorization filter runs before
  `DispatcherServlet`, so a `WebMvcConfigurer`-only CORS setup lets it 401
  the preflight `OPTIONS` request to any authenticated endpoint before CORS
  headers are ever added — the browser reports this as a generic CORS
  failure, not a 401. CORS is registered as a `CorsConfigurationSource`
  bean wired into `HttpSecurity.cors(...)`, with `OPTIONS` explicitly
  permitted. (Caught by driving the real login/register flow in a browser
  against the real backend, not just via `curl`/MockMvc.)
- **Document status ≠ invoice status.** `InvoiceDocument.processingStatus`
  (the file's OCR/AI pipeline state — real work starting Phase 4) and
  `Invoice.status` (the business/approval lifecycle) are two separate state
  machines on two separate entities, matching the product spec's own
  distinction between "document processing pipeline" and "invoice
  lifecycle." Collapsing them would make Phase 4/6 retrofits awkward.
- **No `CHECK` constraint on `invoices.status`.** It's a plain `VARCHAR`
  validated only by the Java enum + an explicit transition whitelist
  (`InvoiceLifecycle`). The full lifecycle enum (through `PAID`,
  `DISPUTED`, etc.) already exists even though only `NEEDS_REVIEW` →
  `VERIFIED` → `ARCHIVED` are reachable today, so Phase 6 (approvals,
  payments) adds transitions, not a migration.
- **A fresh upload starts at `NEEDS_REVIEW`, not `UPLOADED`.** There is no
  OCR/AI pipeline yet (Phase 4), so "uploaded, nothing has looked at it" and
  "needs a human to look at it" are the same true state right now — adding
  a transient `UPLOADED` status nothing ever transitions out of would be
  modeling a pipeline stage that doesn't exist yet.
- **JPA cascade PERSIST vs. re-`save()`ing a managed entity.** Adding a new
  child (e.g. an `InvoiceDocument`) to an already-managed parent's
  collection and letting the transaction flush naturally cascades the
  insert. Calling `repository.save(parent)` again afterward instead calls
  `merge()` (since the parent already has an id) — and `merge()` only
  cascades `MERGE`, not `PERSIST` — silently leaving the new child's
  foreign key `null`. Found via a real `DataIntegrityViolationException`
  in the integration tests, not by inspection.
- **The document pipeline never throws out to the controller.** Corrupted
  and password-protected PDFs, and confidently-wrong document types
  (a purchase order, a bank statement), still return `201` — the invoice
  and document rows are persisted with `processingStatus=REJECTED` and a
  human-readable `rejectionReason`. A rejected upload is a real, visible
  record a reviewer can act on (replace the file, or override and enter
  data manually), not a toast that vanishes and leaves no trace.
- **Classification and extraction are explainable heuristics, not ML.**
  `DocumentClassificationService` counts keyword matches per document type;
  `MockAiExtractionService` is regex/label-based. Both are honest about
  what they are (see `AiExtractionService`'s Javadoc) and both only ever
  return a value they can point to in the text — a missing field comes
  back `null` with no confidence entry, never a guess. This is also why a
  document with zero classification signal resolves to `UNKNOWN` and is
  let through for human review rather than confidently rejected: a wrong
  rejection blocks a real invoice, so ties resolve in favor of a human
  making the call.
- **OCR degrades to "needs review," never to a fabricated result.**
  `TesseractOcrService` shells out to the system `tesseract` binary (present
  in the Docker image, apt-installed in `backend/Dockerfile`) and reports
  itself unavailable — logged once at startup — when that binary isn't on
  `PATH`, which is exactly the situation in this sandboxed dev environment.
  Image-only PDFs and photo/scan uploads still succeed in that case; they
  just come back with no extracted text and `processingStatus=NEEDS_REVIEW`
  instead of invented field values. Only the first page of a multi-page
  scanned PDF is OCR'd today — a documented limitation, not a silent gap.
- **Vendor auto-linking is exact-match only.** The AI extractor's guessed
  vendor name is matched against existing `Vendor` rows with a case-insensitive
  *exact* match. No fuzzy matching — a slightly different rendering of the
  same vendor name (extra punctuation, "Inc." vs "Incorporated") won't
  auto-link, but it also won't silently attach the wrong vendor to an
  invoice. The raw extracted name is always kept (`vendorNameRaw`) so the
  reviewer sees what was found either way.
- **Validation, duplicates, anomalies, and risk score are computed on
  read, never persisted.** They're pure functions of data that's already
  in the database (the invoice, its line items, and its vendor's other
  invoices), so there's no snapshot to go stale — editing an invoice or a
  sibling invoice changes what the next `GET` returns, automatically. The
  cost is an extra indexed query per vendor on each invoice detail fetch;
  at this scale that's cheaper than the staleness bugs a cached copy would
  invite. It also means `verify()` and `GET .../{id}` share exactly one
  code path for "what does this invoice's validation look like," so they
  can never disagree.
- **Anomaly detection needs 3 prior invoices from the same vendor before
  it says anything.** With a small sample, a z-score baseline is noise,
  not signal — silence is more honest than a confident-sounding guess
  from two data points. This is also why duplicate/anomaly detection is
  vendor-scoped rather than org-wide: "this vendor's invoices are usually
  ₹50K" is a real baseline; "this org's invoices are usually ₹50K" mixes
  rent with software subscriptions and means nothing.
- **`verify()` is now gated by the same validation engine the UI renders.**
  Phase 3's ad-hoc "are vendor/number/date/total present" check in
  `InvoiceService.verify()` was replaced with a call to
  `InvoiceValidationService` — the exact same rule set the invoice detail
  page's "Validation checklist" shows. Only `ERROR`-severity rules block
  verification; `WARNING`s (totals not reconciling, a due date before the
  invoice date) are surfaced but don't stop an accountant who's looked at
  the numbers and is satisfied.
- **One lifecycle whitelist, even for payment-driven transitions.**
  `InvoiceLifecycle`'s transition graph now covers approval, payment, and
  dispute states too. The payment-amount-driven moves
  (`APPROVED`→`PAYMENT_SCHEDULED`→`PARTIALLY_PAID`→`PAID`) aren't a single
  discrete user action — `PaymentService.recomputeStatus` derives the
  honest status from what's actually been paid after every schedule/
  complete/cancel — but that derived target still has to land on an edge
  in the same whitelist `verify()`/`approve()`/`dispute()` use, so the
  legal-transition graph is never duplicated or allowed to drift.
- **Approval thresholds are policy, recomputed live — never persisted
  on the invoice.** `ApprovalPolicy.requiredApproverRole` reads the
  organization's current manager/admin thresholds against the invoice's
  total every time it's asked (on submit, on approve/reject, and on every
  `GET`), so changing a threshold in Finance Settings immediately changes
  what a *pending* invoice requires — there's no stale "required role"
  snapshot to reconcile.
- **Separation of duties is enforced in code, not just UI.** Whoever
  submitted an invoice for approval cannot approve or reject it themselves,
  even if they hold `FINANCE_MANAGER`/`ORGANIZATION_ADMIN` — checked
  server-side in `InvoiceService.assertCanDecide`, not just by hiding the
  button. An amount at or above the admin threshold requires an
  `ORGANIZATION_ADMIN` specifically; a `FINANCE_MANAGER` is turned back
  with a clear 422, not a silent no-op.
- **A budget is a recurring monthly cap, not a per-month allocation
  calendar.** One `Budget` row per vendor category applies to whichever
  month you ask `GET /api/budgets/status?month=` about — matching how SMB
  finance teams actually set budgets ("marketing gets ₹50k/month") without
  needing to pre-plan every month. Actual spend is recomputed from real
  invoice totals on every read, the same "never cache a derived number"
  choice Phase 5's risk signals made.
- **Recurring-expense detection is deliberately conservative.** It only
  speaks up when every consecutive gap between a vendor's invoices lands
  in a 25–35 day window *and* every amount is within 25% of the average —
  one irregular gap or one outlier amount and it stays silent rather than
  guessing at a pattern that isn't really there yet.
- **A category's spend-vs-budget comparison uses an average-per-month,
  never the raw period total.** `AnalyticsService.categorySpend` sums
  spend across however many months were requested, but `Budget.monthlyLimit`
  is a *single* month's cap — comparing a 6-month total against a 1-month
  number would show almost every category as "over budget" the moment the
  period is longer than a month. The Analytics page divides the period
  total by the number of months before comparing it to the cap, so
  "Last 3 months" and "Last 6 months" give the same read on whether a
  category is actually trending over its monthly limit. Caught by looking
  at the live chart, not by inspection — the numbers were individually
  correct and still told the wrong story together.
- **The cash-flow forecast is built only from committed money.**
  `ForecastService.cashFlow` sums payments that are actually `SCHEDULED`
  and outstanding balances on invoices that are actually due (minus
  whatever's already been scheduled against them, so a partially-scheduled
  invoice isn't double-counted), bucketed by week. A past-due amount still
  shows up — clamped into the current week's bucket rather than a
  negative-index week that would never render — instead of quietly
  disappearing. The only *projected* (as opposed to committed) number,
  `monthlyProjection`, is a plain historical average and is labeled as
  exactly that in its own response, never blended into the committed-money
  chart.
- **The home dashboard is a live query, not a notification feed.**
  `DashboardService.actionCenter` needed no new table: "pending your
  approval," "your rejected/disputed invoices," "overdue," and
  "over-budget" are all computed straight from `Invoice`/`Budget` rows on
  every load. A stored notification would need its own read/unread state
  and would drift the moment the underlying invoice changed out from under
  it (approved after the notification fired, disputed then resolved,
  etc.); a live query can't drift because there's nothing to go stale.
  Never accidentally exposed to the wrong person: the same `ApprovalPolicy`
  and self-approval rules `InvoiceService` enforces when you actually try
  to approve something are reused here to decide what even *shows up* as
  "pending your approval."
- **Never linked a `<TableRow>` with `component={RouterLink}`.** MUI
  renders that as `<a><td>...</td></a>` — an anchor wrapping table cells,
  which is invalid HTML (`React` logs a DOM-nesting warning, and the
  markup isn't real table markup to a screen reader). Every clickable
  table row in this app instead sets `onClick={() => navigate(...)}` on a
  plain `<TableRow>`, the same pattern `InvoicesPage` already used —
  caught by an actual browser console warning during verification, not by
  reading the JSX.

## Running locally

### Prerequisites
- Docker + Docker Compose
- (For local non-Docker dev) Java 21, Maven, Node 20+

### With Docker (recommended)

```bash
cp .env.example .env
# edit .env if needed — defaults work out of the box with the mock AI provider
docker compose up --build
```

- Frontend: http://localhost:5173
- Backend API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- Backend health: http://localhost:8080/api/health

Inside Docker, the backend connects to Postgres/Redis via their **service
names** (`postgres`, `redis`), not `localhost` — see `docker-compose.yml`.

### Without Docker

Backend:
```bash
cd backend
mvn spring-boot:run
# requires a local Postgres + Redis; see application.yml for the env vars it reads
```

Frontend:
```bash
cd frontend
cp .env.example .env
npm install
npm run dev
```

## Running tests

```bash
# Backend
cd backend && mvn test

# Frontend
cd frontend && npm test
```

Backend integration tests (`AuthAndTenancyIntegrationTest`,
`InvoiceCoreIntegrationTest`, `DocumentIntelligenceIntegrationTest`,
`RiskAndValidationIntegrationTest`, `FinanceWorkflowIntegrationTest`,
`AnalyticsIntegrationTest`, `ForecastIntegrationTest`,
`DashboardIntegrationTest`, `ExportIntegrationTest`) run against a
**real PostgreSQL** via
[`io.zonky.test:embedded-postgres`](https://github.com/zonkyio/embedded-postgres)
(the `ZONKY` native-binary provider, not Docker) — the same Flyway
migrations and JPA entities used in production run for real, including
role-based access control and explicit cross-tenant access attempts, not
mocked-repository unit tests. This also means `mvn test` does not require
Docker or a running Postgres instance to pass. All integration test classes
share a common harness (`AbstractIntegrationTest`) with register/login/
upload helpers, including generating real (valid, corrupted, and
password-protected) PDFs on the fly with Apache PDFBox — no fixture files
checked into the repo. `FinanceWorkflowIntegrationTest` alone covers
threshold-based auto-approval, manager- and admin-level approval routing,
the self-approval block, rejection, the full schedule→complete/cancel
payment lifecycle (including over-payment rejection), dispute/resolve,
budget status math, finance-settings admin-gating and validation, and the
overdue sweep job. `AnalyticsIntegrationTest` and `ForecastIntegrationTest`
cover spend/vendor/category aggregation, budget history, RBAC (an
`EMPLOYEE` gets a 403), and the cash-flow forecast's bucketing rules —
including that a past-due amount lands in the current week rather than
being dropped, and that scheduling an invoice's full outstanding balance
removes it from the "due, unscheduled" figure. `DashboardIntegrationTest`
covers the action center's role-scoping (a finance manager never sees
their own submission in their own approval queue; an employee's view is
scoped to their own invoices only) and that a rejected invoice actually
surfaces in "needs my attention." `ExportIntegrationTest` covers CSV
content and the same employee-scoping the list endpoints use.
`PdfTextExtractionServiceTest`,
`DocumentClassificationServiceTest`, `MockAiExtractionServiceTest`,
`InvoiceValidationServiceTest`, `DuplicateDetectionServiceTest`,
`AnomalyDetectionServiceTest`, `RiskScoringServiceTest`,
`RecurringExpenseDetectionServiceTest`, and `ApprovalPolicyTest` are fast
unit tests with no database — they build `Invoice`/`Vendor`/`Organization`
entities directly and assert on the pure business logic.

## Environment variables

See [.env.example](.env.example) for the full list. Notable ones:

| Variable | Purpose |
|---|---|
| `JWT_SECRET` | HMAC signing key for access/refresh tokens. Must be overridden outside of dev. |
| `AI_PROVIDER` | `mock` (default, no key needed) or a real provider name |
| `AI_API_KEY` | Only required when `AI_PROVIDER` is not `mock` |
| `STORAGE_PATH` | Root path for document storage inside the backend container |
| `MAX_FILE_SIZE` | Upload size cap (default 10MB), enforced by the multipart resolver |
| `CORS_ALLOWED_ORIGINS` | Comma-separated list of allowed frontend origins |

## Demo credentials

There is no seeded demo account yet — registration is self-serve. Visit
`/register`, create an organization (you become its `ORGANIZATION_ADMIN`),
and use `/settings/users` → "Add member" to create additional users with
other roles (`FINANCE_MANAGER`, `ACCOUNTANT`, `EMPLOYEE`, `VIEWER`) to
exercise role-based access. Coherent seed data with pre-built
organizations/roles is planned for Phase 3+ once there's invoice/vendor
data worth seeding alongside it.

## Roadmap

- [x] **Phase 1 — Project setup:** repo structure, Docker Compose, Spring
      Boot skeleton (health check, security baseline, global error handling,
      OpenAPI), React skeleton (routing, MUI theme, API client wired to
      backend health check), Postgres, Redis, Flyway baseline.
- [x] **Phase 2 — Authentication & multi-tenancy:** registration (creates
      org + `ORGANIZATION_ADMIN`), login, JWT access tokens + rotating
      opaque refresh tokens with theft detection, role-based authorization
      (`@PreAuthorize`) across 5 roles, organization member management
      (invite/list/change role/remove) with last-administrator protection,
      audit logging of auth/membership events, backend tests against a real
      embedded Postgres covering tenant isolation and RBAC, and a working
      React login/register/logout flow with silent access-token refresh.
- [x] **Phase 3 — Invoice core:** vendor CRUD (soft-archive, search),
      invoice upload (real file-type sniffing via Apache Tika + empty-file
      rejection — not just trusting the browser's declared type),
      organization-local document storage with tenant/invoice-scoped
      paths, invoice/line-item CRUD, an enforced lifecycle transition
      whitelist (`NEEDS_REVIEW` → `VERIFIED` → `ARCHIVED`, with editing a
      verified invoice honestly reopening it for review), role-based
      upload/edit/verify/archive permissions plus employee-scoped
      visibility (employees see only their own submissions), backend tests
      against a real embedded Postgres, and a working React vendor list +
      invoice list/upload/detail UI with a real document viewer (fetched
      as an authenticated blob, not a public link).
- [x] **Phase 4 — Document intelligence:** real PDF text extraction
      (Apache PDFBox) with genuine corrupted/password-protected detection;
      OCR via the Tesseract CLI for scanned PDFs and photo uploads, with
      honest graceful degradation (not a fake result) when the binary isn't
      present; explainable keyword-based document classification that
      rejects confidently-wrong document types (purchase orders, bank
      statements) while giving ambiguous documents the benefit of the
      doubt; regex/heuristic AI field extraction (invoice number, dates,
      subtotal/tax/total, GSTIN, vendor name) with a per-field confidence
      score that never guesses a value it can't point to in the text;
      exact-match vendor auto-linking; and a React UI that highlights
      low-confidence fields, explains what AI found, and surfaces document
      rejections as a persistent, actionable banner rather than a toast.
- [x] **Phase 5 — Validation & risk:** a deterministic, AI-independent
      validation engine (10 rules — required fields, non-negative amounts,
      valid currency/GSTIN format, totals reconciling within tolerance,
      line items summing to the subtotal, sane dates) that now actually
      gates `verify()` instead of an ad-hoc field check; duplicate
      detection (exact invoice-number match plus amount/date-similarity
      scoring) that flags but never auto-deletes; vendor-history-based
      anomaly detection (z-score against the vendor's own prior invoices,
      silent below 3 data points rather than guessing); and an explainable
      0–100 risk score that shows its work — every point traces to a
      specific validation failure, duplicate match, or anomaly in
      `riskReasons`. All four are computed fresh on every read (no
      snapshot to go stale) and rendered in a "Risk & validation" panel on
      the invoice detail page, including a "View invoice" link straight
      to the suspected duplicate.
- [x] **Phase 6 — Finance workflows:** configurable manager/admin approval
      thresholds (`submitForApproval` auto-approves below every threshold,
      else routes to `PENDING_APPROVAL` with the exact role required);
      approve/reject with server-enforced separation of duties (no one
      approves or rejects their own submission, admin-level amounts require
      an admin specifically) and a queryable approval-history audit trail;
      a payment engine (schedule → mark completed/cancel) where invoice
      status (`PAYMENT_SCHEDULED`/`PARTIALLY_PAID`/`PAID`/`OVERDUE`) is
      *derived* from what's actually been paid rather than chosen, guarded
      against scheduling more than the outstanding balance, plus a daily
      overdue sweep job; dispute/resolve-dispute with a recorded reason;
      recurring monthly budgets per vendor category with real-time
      actual-spend/over-budget status; and vendor-history-based recurring
      expense detection (conservative: needs a consistent ~30-day cadence
      and amounts within 25% of each other). All new invoice-lifecycle
      states route through one shared `InvoiceLifecycle` transition
      whitelist alongside Phase 3's rules. New "Payments" and "Budgets"
      pages, a "Finance Settings" admin page for the thresholds, and a full
      payment/approval/dispute action set on the invoice detail page.
- [x] **Phase 7 — Analytics:** an `AnalyticsController` (spend summary,
      monthly spend trend, top vendors by spend, spend-by-category with
      each category's budget cap alongside it) and a `ForecastController`
      — a cash-flow forecast built entirely from money already committed
      (payments actually scheduled, plus outstanding balances on invoices
      already due with no scheduled payment yet, bucketed by week) and a
      clearly-labeled "average of the last N months" projection that never
      pretends to be a predictive model. `BudgetService` gained a
      `history()` endpoint (per-category actual-vs-limit for each of the
      last N months) reusing the exact same monthly aggregation `status()`
      already used. Every number is aggregated in Java from real
      invoice/payment rows on every request — no materialized reporting
      table, the same "recompute, never cache a derived number" choice
      every prior phase made. A new "Analytics" page (Recharts) replaces
      the Phase 1 placeholder with a spend-trend bar chart, a status-
      breakdown pie chart, a top-vendors chart, a category table with a
      fair (average-per-month, not raw-period-total) budget comparison,
      a stacked cash-flow forecast chart, and a per-category budget-
      history line chart — gated to every role except `EMPLOYEE`, who
      only ever sees their own submissions elsewhere in the app.
- [x] **Phase 8 — Action dashboard & CSV exports:** the Phase 1
      health-check placeholder is now a real home dashboard — a live
      "action center" (invoices pending your approval, your own
      submissions that were rejected or disputed, overdue invoices, and
      over-budget categories) computed fresh from existing tables on every
      load, deliberately *not* a stored notification feed with its own
      read/unread state. CSV export for invoices and payments (respecting
      the same employee-scoping and status filter as their list views).
      Also fixed a real, pre-existing gap while in the area: the invoice
      status filter dropdown still only listed Phase 3's three statuses
      and hadn't been updated across Phases 4–6 as the lifecycle grew to
      twelve.
      **Deliberately out of scope for this build** (trimmed to keep the
      remaining work to what's necessary for a complete, locally-runnable
      product): email/SMTP notifications, a real LLM provider behind
      `AiExtractionService`, natural-language search, and the Finance
      Copilot. The AI/OCR abstractions (`AiExtractionService`, `OcrService`)
      are still interfaces specifically so a real provider can be dropped
      in later without touching the pipeline that calls them — that work
      was simply never done here.

## Known limitations (current phase)

- **OCR requires the `tesseract` binary**, which is apt-installed in
  `backend/Dockerfile` but is *not* present in this sandboxed dev
  environment. Real PDFs with selectable text still extract perfectly
  (PDFBox doesn't need OCR for those); scanned/image-only documents come
  back with no text and `NEEDS_REVIEW` until run somewhere with Tesseract
  installed (e.g. via Docker). This was verified directly: the startup log
  shows `tesseract binary not found on PATH — OCR is disabled`.
- Classification and extraction are **deterministic heuristics**
  (keyword counting; regex/label matching), not a trained ML model or LLM
  call — by design, per the "mock provider" scope decided for this build.
  `AiExtractionService` is an interface specifically so a real LLM-backed
  implementation can be dropped in later without changing the pipeline
  that calls it.
- Only the **first page** of a multi-page scanned PDF is OCR'd. Selectable-text
  PDFs use PDFBox's full-document text extraction regardless of page count.
- Vendor name auto-linking is **exact-match only** (case-insensitive) — no
  fuzzy matching, so near-duplicate vendor names won't auto-link (the raw
  extracted name is preserved either way so nothing is lost).
- **Duplicate and anomaly detection are vendor-scoped and per-invoice**,
  computed on read from the vendor's other invoices in the same
  organization. There's still no org-wide or category-wide anomaly
  baseline (Phase 6's budgets check category *totals* against a cap, not
  category-level statistical anomalies), and no frequency-based anomaly
  check (e.g. "too many invoices this week") — only amount-based, matching
  the spec's most concrete example.
- The currency check is a **format check** (`[A-Z]{3}`), not a real ISO
  4217 lookup — there's no maintained currency list, so "XYZ" would pass
  the same as "USD". Tightening this to a real list is cheap future work,
  not a design constraint.
- GST validation is **format-only** (does the GSTIN look shaped right),
  not a checksum/registry lookup — and is explicitly labeled informational
  in the UI, not a substitute for a tax professional, per the product
  spec's own caveat.
- A budget's `category` must **exactly match** (case-insensitive) a
  vendor's `category` field — there's no controlled vocabulary or
  autocomplete tying the two together yet, so a typo in either place
  silently produces a budget that never matches any spend.
- Recurring expense detection only recognizes a **monthly** cadence
  (25–35 day gaps) — weekly/quarterly subscriptions aren't flagged, and
  it only *detects* a pattern for review; it never auto-creates the next
  expected invoice.
- The **overdue sweep runs once daily** (cron, 00:15) rather than
  real-time; an invoice that crosses its due date won't show `OVERDUE`
  until the next scheduled run (or immediately, on-demand, the next time
  a payment is scheduled/completed against it, since that recomputes the
  status from the same rule).
- Payments are recorded in the **invoice's own currency only** — there's
  no multi-currency conversion, and a completed payment can't be reversed
  (only a still-`SCHEDULED` payment can be cancelled); correcting an
  overpayment or a wrong completed payment today means disputing the
  invoice and re-reviewing it, not editing the payment record.
- Approval thresholds are a **single global pair per organization**
  (manager, admin) — there's no per-vendor-category or per-department
  threshold yet, matching the spec's simplest concrete example.
- **Analytics and forecasting are not currency-aware** — every sum adds
  raw `totalAmount` values regardless of each invoice's `currency`. An
  organization that only ever invoices in one currency (the common case)
  gets correct numbers; one that mixes currencies would get a nominal sum
  that silently treats them as equivalent. There's no FX conversion
  anywhere in this build.
- Analytics/forecast **recompute from every invoice/payment row in the
  organization on every request** — no pagination, no materialized
  aggregate, no cache. Consistent with every other phase's "recompute,
  don't persist" choice, and fine at SMB scale, but a real scale limit:
  this would need to move to a proper aggregate table before it holds up
  at high invoice volume.
- The cash-flow forecast chart only draws the requested `weeks` window
  (default 8) — a scheduled payment or due invoice far beyond that
  horizon still counts in the response's `totalScheduled`/
  `totalDueUnscheduled` figures, it just won't appear in any weekly bar.
- A user can belong to exactly one organization today. The schema
  (`organization_members` as a join table, independent of `users`) supports
  multi-organization membership later without a rewrite, but registration
  and invites both currently assume one membership per user.
- "Add member" issues the new member a password directly (returned to the
  admin to share) rather than an email invite flow. **No outbound email
  exists anywhere in this build, by choice** — approval requests, overdue
  invoices, and budget breaches all surface through the live "action
  center" on the dashboard instead of an email/SMTP notification system,
  which was deliberately cut from this build's scope to keep everything
  runnable with zero external service dependencies.
- The frontend's `VITE_API_BASE_URL` is baked in at Docker build time (Vite
  env vars are compile-time for a static SPA build); changing it requires a
  rebuild of the frontend image.
- This sandboxed dev environment has no Docker daemon access, so
  `docker compose up --build` itself has not been run/verified here end to
  end — instead, the backend (packaged jar), a real standalone PostgreSQL,
  and the frontend dev server were run directly and driven through a real
  browser to verify the full register/login/logout flow and the
  Docker-equivalent env-var wiring. `docker compose config` was used to
  confirm the compose file itself parses and resolves correctly. If you
  have Docker available locally, `docker compose up --build` should work
  as-is from a copy of `.env.example` — that path is exercised by every
  phase's env-var wiring but not by a real `docker compose up` in this
  sandbox specifically.
- **Exports are CSV only** — no PDF invoice or PDF report generation.
  CSV covers the real accounting workflow (reconciliation in a
  spreadsheet); PDF generation was cut as a nice-to-have, not a
  necessary-for-local-use feature.
- CSV export loads every matching row into memory and builds the file in
  one pass — the same "no pagination on the full-dataset read" limitation
  already true of Analytics/Forecast, now also true here. Fine at SMB
  invoice volumes, not something that scales to millions of rows.
- The dashboard's "needs your attention" section flags a `NEEDS_REVIEW`
  invoice you submitted as bounced-back using a simple heuristic (it has
  *any* prior approval decision) rather than checking that the *most
  recent* decision was specifically a rejection. In practice this only
  differs if an invoice were rejected, fixed, resubmitted, and approved,
  then somehow returned to `NEEDS_REVIEW` by another path — not reachable
  through any flow this build exposes today, but worth knowing if that
  changes later.
- **No real AI provider, natural-language search, or Finance Copilot** —
  all three were explicitly cut from this build's scope to keep it
  runnable with zero external API keys and no added complexity beyond
  what a local financial workflow tool actually needs. `AiExtractionService`
  and `OcrService` remain interfaces specifically so a real provider could
  be added later without touching the pipeline that calls them; that work
  was simply never done here.
