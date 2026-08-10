# MASTER PROMPT — AI-Powered Invoice & Expense Intelligence Platform

You are a senior software architect, senior Java/Spring Boot backend engineer, senior React engineer, AI/ML engineer, database architect, DevOps engineer, security engineer, and product designer.

Your task is to **design and fully implement a production-quality full-stack application** called:

# InvoiceIQ

### AI-Powered Invoice & Expense Intelligence Platform

This is NOT a demo, toy project, CRUD exercise, AI wrapper, or static prototype.

Build it as a realistic B2B SaaS product that could actually be used by a small/medium-sized company to manage invoices, expenses, vendors, payments, financial analytics, and AI-powered financial intelligence.

The application must have:

* React frontend
* Java Spring Boot backend
* PostgreSQL database
* Redis
* Background/asynchronous processing
* Docker + Docker Compose
* AI integration
* OCR/document processing
* ML/anomaly detection
* REST APIs
* Authentication and authorization
* Multi-tenant organization support
* Audit logging
* Proper validation
* Error handling
* Automated tests
* API documentation
* Production-quality UI/UX

Do not create fake/mock functionality where real functionality can reasonably be implemented.

If an external service requires an API key, implement the integration behind a clean interface and provide a working local fallback/mock implementation so the application can still run end-to-end without paid APIs.

---

# 1. PRODUCT VISION

InvoiceIQ solves a real business problem.

Businesses receive large numbers of:

* invoices
* bills
* receipts
* expense documents
* vendor statements

Finance teams currently spend significant time:

* manually entering invoice information
* categorizing expenses
* checking duplicate invoices
* validating totals
* checking tax information
* tracking payment deadlines
* identifying unusual expenses
* monitoring vendor spending
* preparing monthly reports
* forecasting expenses
* identifying cost-saving opportunities

InvoiceIQ should automate these workflows and transform raw financial documents into actionable financial intelligence.

The product flow should be:

Document
→ Validation
→ OCR
→ AI Extraction
→ Confidence Scoring
→ Human Verification
→ Validation Rules
→ Categorization
→ Duplicate Detection
→ Anomaly Detection
→ Approval
→ Payment Tracking
→ Analytics
→ Forecasting
→ Business Insights

AI must solve meaningful problems.

Do NOT add AI merely because the project is supposed to be "AI-powered."

---

# 2. PRIMARY USERS

Support multiple organizations and users.

Roles:

1. ORGANIZATION_ADMIN
2. FINANCE_MANAGER
3. ACCOUNTANT
4. EMPLOYEE
5. VIEWER

Permissions must actually be enforced on backend APIs.

Example:

Employee:

* upload invoices
* view own submissions
* view permitted information

Accountant:

* manage invoices
* verify documents
* manage vendors
* record payments

Finance Manager:

* approve invoices
* view financial analytics
* manage budgets
* view forecasts

Admin:

* organization settings
* users
* roles
* everything

Viewer:

* read-only dashboards and reports

Never rely only on frontend permissions.

Every protected API must enforce authorization server-side.

---

# 3. MULTI-TENANCY

The system must support multiple organizations.

Example:

Organization A:

* users
* invoices
* vendors
* expenses

Organization B:

* completely separate data

Users from Organization A must NEVER be able to access Organization B's data.

Every organization-owned entity should contain an organization/tenant relationship.

Implement tenant isolation carefully at the service/repository/API layer.

Do not trust organization IDs supplied by the frontend.

Derive the organization from the authenticated user/context wherever appropriate.

Test cross-tenant access explicitly.

---

# 4. TECHNOLOGY STACK

## Frontend

Use:

* React
* TypeScript
* Vite
* React Router
* Material UI
* TanStack Query
* React Hook Form
* Zod
* Recharts or another appropriate charting library
* Axios or fetch abstraction

Use TypeScript strictly.

Do not use unnecessary libraries.

---

## Backend

Use:

* Java 21+
* Spring Boot 3+
* Spring Web
* Spring Security
* JWT
* Spring Data JPA
* Hibernate
* Bean Validation
* PostgreSQL driver
* Flyway
* Spring Actuator
* OpenAPI / Swagger
* Redis integration
* asynchronous/background processing

Use Maven.

Follow clean architecture principles.

Prefer:

Controller
→ Service
→ Repository

with DTOs separating API models from database entities.

Do not expose JPA entities directly from APIs.

---

## Database

PostgreSQL.

Use Flyway migrations.

Do not rely on Hibernate auto-generating production schema.

Create proper indexes.

Use transactions where required.

Use UUIDs for public identifiers where appropriate.

---

## Infrastructure

Dockerize everything.

Create:

docker-compose.yml

Services should include at minimum:

* frontend
* backend
* postgres
* redis

If an additional service is genuinely useful, add it.

The entire application should start with:

docker compose up --build

The README must explain this clearly.

---

# 5. AI ARCHITECTURE

Do not tightly couple the application to a single AI provider.

Create an abstraction such as:

AIService

or

DocumentIntelligenceService

with implementations for:

* external LLM provider
* local/mock development provider

The application should remain runnable without an external AI API key.

Possible providers can include OpenAI-compatible APIs or another LLM provider.

Do not hard-code API keys.

Use environment variables.

---

# 6. DOCUMENT PROCESSING PIPELINE

This is one of the most important parts of the system.

When a user uploads a document:

DO NOT immediately trust it.

Implement this pipeline:

Upload
→ File Validation
→ Security Validation
→ File Metadata Extraction
→ Document Classification
→ OCR
→ Invoice Detection
→ AI Extraction
→ Schema Validation
→ Business Validation
→ Confidence Scoring
→ Duplicate Detection
→ Anomaly Detection
→ Human Verification if required
→ Final Processing

Every stage should have a status.

Example:

UPLOADED
VALIDATING
REJECTED
OCR_PROCESSING
EXTRACTING
EXTRACTED
VALIDATING_DATA
NEEDS_REVIEW
VERIFIED
PROCESSED
FAILED

---

# 7. FILE UPLOAD EDGE CASES

Handle all of the following.

## Invalid file type

Reject:

* executable files
* scripts
* unsupported binary files
* suspicious extensions

Allow only configured types such as:

* PDF
* JPG
* JPEG
* PNG
* WEBP if appropriate

Do not trust the file extension alone.

Validate actual MIME/content type.

---

## File size

Configure maximum file size.

For example:

10 MB per document.

Return a useful error:

"File exceeds the maximum allowed size of 10 MB."

Do not crash the backend.

---

## Empty files

Reject zero-byte files.

---

## Corrupted PDF

Detect unreadable/corrupted documents.

Show:

"Unable to process this document because the file appears to be corrupted."

---

## Password-protected PDF

Detect it.

Show:

"This PDF is password protected and cannot be processed."

Do not attempt to bypass document protection.

---

## Scanned documents

Support image-only PDFs.

Run OCR when no selectable text exists.

---

## Very low-quality scans

If OCR confidence is too low:

mark:

NEEDS_REVIEW

and explain why.

---

## Wrong document

This is extremely important.

Someone may upload:

* resume
* Aadhaar
* PAN card
* bank statement
* random photo
* screenshot
* contract
* purchase order
* delivery note
* unrelated PDF

The system must determine whether the document is actually an invoice/expense document.

Example:

"Document appears to be a resume, not an invoice."

Reject or route to manual review.

Never blindly extract random fields from unrelated documents.

---

# 8. DOCUMENT CLASSIFICATION

Classify documents into categories such as:

* INVOICE
* RECEIPT
* CREDIT_NOTE
* DEBIT_NOTE
* PURCHASE_ORDER
* STATEMENT
* OTHER
* UNKNOWN

Only invoice-compatible documents should enter the invoice processing workflow.

Keep the original classification.

---

# 9. OCR

Implement OCR behind an abstraction.

For local development, use a practical open-source OCR approach where possible.

The OCR layer should return:

* extracted text
* OCR confidence
* page count
* processing duration
* detected language if available

Store processing metadata.

Do not store unnecessary raw sensitive information in logs.

---

# 10. AI INVOICE EXTRACTION

Extract structured information.

At minimum:

## Invoice metadata

* invoice number
* invoice date
* due date
* purchase order number
* invoice type
* currency

## Vendor

* vendor name
* vendor address
* vendor email
* vendor phone
* GSTIN if applicable
* tax ID if applicable

## Buyer

* buyer name
* buyer address
* GSTIN if applicable

## Financial fields

* subtotal
* discount
* taxable amount
* CGST
* SGST
* IGST
* other taxes
* total amount

## Line items

Each line item should contain:

* description
* quantity
* unit price
* tax
* discount
* total

---

# 11. AI CONFIDENCE

Every important extracted field should have a confidence score.

Example:

invoiceNumber:
value = "INV-8291"
confidence = 0.98

totalAmount:
value = 96760
confidence = 0.97

dueDate:
value = "2026-09-04"
confidence = 0.61

If confidence is below a configurable threshold:

mark the field as requiring human verification.

Never pretend uncertain AI output is certain.

---

# 12. AI HALLUCINATION PROTECTION

AI output must NEVER be directly trusted.

Implement:

AI response
→ strict JSON schema
→ type validation
→ business validation
→ consistency validation

If AI returns malformed JSON:

* retry safely where appropriate
* otherwise mark extraction as failed
* allow manual correction

Do not silently invent missing fields.

If a field is absent from the document:

return null/unknown.

Never ask the AI to guess.

---

# 13. INVOICE VALIDATION ENGINE

Build a deterministic validation layer independent of AI.

Examples:

subtotal + tax - discount ≈ total

Line item totals should approximately match subtotal.

Invoice date should not be impossible.

Due date should not be earlier than invoice date unless explicitly allowed.

Amount must be non-negative.

Currency must be valid.

Invoice number should not be empty.

Vendor should be present.

Tax values must be mathematically consistent.

GST calculations should be checked where applicable.

Validation results should have:

* rule
* status
* severity
* explanation

Example:

PASS:
"Subtotal and total are consistent."

WARNING:
"GST amount differs slightly from calculated tax."

ERROR:
"Invoice total does not match line-item total."

---

# 14. GST/TAX VALIDATION

For India-focused functionality support:

* GSTIN extraction
* CGST
* SGST
* IGST
* taxable amount
* total tax

Perform basic consistency checks.

Do NOT present the application as a tax/legal authority.

Display:

"Validation is informational and should be verified by a qualified professional."

---

# 15. DUPLICATE DETECTION

Implement multiple levels of duplicate detection.

Exact duplicate:

same invoice number + vendor + organization

Potential duplicate:

same/similar:

* vendor
* amount
* date
* invoice number
* line items

Document similarity where practical.

Return:

duplicateProbability

Example:

94%

Explain:

"Possible duplicate of INV-1023 from ABC Technologies."

Never automatically delete duplicates.

Flag them for review.

---

# 16. EXPENSE CATEGORIZATION

Automatically categorize invoices.

Default categories:

* Software
* Cloud Infrastructure
* Office Supplies
* Travel
* Marketing
* Utilities
* Professional Services
* Equipment
* Logistics
* Rent
* Payroll-related
* Other

Allow users to create custom categories.

AI should suggest categories.

Users can override them.

Store corrections as feedback.

---

# 17. HUMAN-IN-THE-LOOP

Create a dedicated verification interface.

When an invoice requires review:

show:

LEFT:
Original document

RIGHT:
Extracted structured fields

Highlight low-confidence fields.

Example:

Invoice Number:
INV-8291
98% confidence

Due Date:
04/09/2026
61% confidence
⚠ Verify

Users can edit fields.

After correction:

mark field as human_verified.

Record who changed it and when.

---

# 18. INVOICE LIFECYCLE

Implement:

UPLOADED
→ PROCESSING
→ NEEDS_REVIEW
→ VERIFIED
→ PENDING_APPROVAL
→ APPROVED
→ PAYMENT_SCHEDULED
→ PARTIALLY_PAID
→ PAID
→ OVERDUE
→ DISPUTED
→ ARCHIVED

Transitions must be controlled.

Do not allow arbitrary invalid state transitions.

---

# 19. APPROVAL WORKFLOW

Support configurable approval thresholds.

Example:

< ₹10,000:
Accountant

₹10,000–₹100,000:
Finance Manager

> ₹100,000:
> Admin

Allow organization admins to configure thresholds.

Approvals must generate audit logs.

---

# 20. PAYMENT MANAGEMENT

For each invoice:

* payment status
* amount due
* amount paid
* remaining amount
* payment date
* payment method
* reference number

Support partial payments.

Example:

Invoice:
₹100,000

Paid:
₹60,000

Remaining:
₹40,000

Status:
PARTIALLY_PAID

---

# 21. OVERDUE MANAGEMENT

Automatically identify overdue invoices.

Example:

Invoice due:
1 August

Today:
9 August

System:

8 days overdue

Display:

🔴 Overdue by 8 days

Do not rely only on frontend calculations.

---

# 22. RECURRING EXPENSE DETECTION

Identify recurring vendor expenses.

Example:

AWS:
₹80K/month

Adobe:
₹18K/month

Slack:
₹12K/month

Calculate estimated recurring monthly expense.

Allow users to confirm or reject recurring classifications.

---

# 23. VENDOR INTELLIGENCE

Vendor page should contain:

* total spend
* invoice count
* average invoice
* highest invoice
* lowest invoice
* pending amount
* overdue amount
* payment history
* monthly trend
* category distribution
* price changes
* anomaly count

---

# 24. VENDOR PRICE CHANGE DETECTION

If historical vendor pricing exists:

compare similar products/services over time.

Example:

AWS:

January:
₹70,000

June:
₹92,000

Show:

"Spend increased approximately 31%."

Do not claim price increase if the actual quantity changed significantly.

Distinguish:

price increase
vs
quantity increase
vs
new services.

---

# 25. ANOMALY DETECTION

Implement a real anomaly detection layer.

Potential approaches:

* statistical z-score
* IQR
* moving average
* historical vendor baseline
* category baseline

Do not overcomplicate with deep ML if the data doesn't justify it.

The system should detect:

* unusually high invoice
* unusual vendor activity
* unusual category spending
* unusual frequency
* sudden spending spikes

Example:

Normal vendor amount:
₹40K–₹60K

Current:
₹2.2L

Result:

HIGH ANOMALY

Explain the reason.

---

# 26. RISK SCORE

Generate invoice risk score from explainable signals.

Possible signals:

* duplicate probability
* amount anomaly
* missing fields
* validation failures
* vendor history
* unusual frequency
* suspicious document classification
* tax inconsistency

Example:

Risk:
82/100

Reasons:

1. Amount is 3.2× vendor average.
2. Similar invoice already exists.
3. GST calculation mismatch.

Do not create a black-box number without explanation.

---

# 27. EXPENSE ANALYTICS

Dashboard must answer:

"Where is our money going?"

Provide:

* spending by category
* spending by vendor
* spending over time
* month-over-month comparison
* year-over-year comparison
* average invoice value
* invoice count
* top vendors
* top categories
* unusual spending

Charts should be interactive.

Users should be able to:

* change date range
* filter category
* filter vendor
* drill down into invoices

---

# 28. BUDGET MANAGEMENT

Users can create budgets:

Category:
Software

Monthly:
₹300,000

Track:

budget
actual
remaining
variance
utilization %

Show warnings:

70%:
normal

90%:
warning

100%+:
exceeded

---

# 29. BUDGET FORECASTING

Use historical spending to estimate whether a budget will be exceeded.

Example:

Current:
₹270K

Budget:
₹300K

Forecast:
₹340K

Display:

"Based on current spending, this category is likely to exceed the monthly budget by approximately ₹40K."

---

# 30. CASH FLOW FORECAST

Estimate upcoming outgoing cash using:

* unpaid invoices
* due dates
* recurring expenses
* historical patterns

Provide:

7-day forecast
30-day forecast
90-day forecast

Example:

Next 7 days:
₹1.2L

Next 30 days:
₹8.4L

Next 90 days:
₹24.7L

Clearly distinguish actual obligations from predictions.

---

# 31. EXPENSE FORECASTING

Forecast future category spending.

Use a reasonable statistical/time-series approach.

Do not use an unnecessarily complicated ML model simply for appearance.

Show:

historical data
forecast
confidence/range where possible

---

# 32. COST-SAVING ENGINE

Generate actionable recommendations.

Examples:

"Software spending increased 24% over three months."

"Vendor X is 18% more expensive than the historical average for comparable purchases."

"Recurring expenses represent ₹4.2L/month."

"Category X is projected to exceed budget by ₹70K."

Each recommendation must contain:

* title
* evidence
* estimated impact
* confidence
* recommended action

Do not invent savings.

If savings cannot be reliably estimated, say:

"Potential saving requires further review."

---

# 33. FINANCE AI ASSISTANT

Create a Finance Copilot.

It should answer questions using actual application data.

Examples:

"How much did we spend on software this month?"

"Which vendors have the highest outstanding amounts?"

"Why did expenses increase?"

"Which invoices are overdue?"

"Which vendors have unusual spending?"

"What categories are likely to exceed budget?"

"How much cash is expected to go out in the next 30 days?"

The assistant must NOT fabricate data.

It should query the actual backend data.

Implement a safe architecture:

User question
→ intent/query interpretation
→ allowed analytics operations
→ backend data
→ answer generation

Do NOT allow arbitrary raw SQL generated by an LLM to execute directly against production data.

---

# 34. DASHBOARDS

Create separate dashboards.

## Executive Dashboard

Show:

* total expenses
* monthly change
* outstanding invoices
* overdue amount
* cash-flow forecast
* budget status
* top vendors
* top categories
* critical alerts
* AI insights

## Finance Dashboard

Show:

* invoices awaiting review
* approval queue
* payment obligations
* overdue invoices
* tax summary
* duplicate alerts
* anomaly alerts

## AI Insights Dashboard

Show:

* anomalies
* duplicate candidates
* high-risk invoices
* forecast changes
* cost-saving recommendations

---

# 35. ALERT CENTER

Centralized alert system.

Alert types:

* duplicate
* anomaly
* overdue
* budget exceeded
* forecast risk
* document failure
* approval required

Allow:

* read/unread
* severity
* filtering
* navigation to source record

---

# 36. NOTIFICATIONS

Implement in-app notifications.

Email notification architecture should be implemented behind an abstraction.

Examples:

Invoice overdue.

Invoice requires approval.

Duplicate detected.

Budget likely to be exceeded.

Document processing failed.

Avoid notification spam.

---

# 37. BULK UPLOAD

Support multiple documents.

Example:

100 invoices uploaded.

Display:

Processed: 87
Processing: 10
Needs Review: 2
Failed: 1

Processing must happen asynchronously.

Do not block the HTTP request until all AI/OCR processing finishes.

---

# 38. BACKGROUND JOBS

Implement asynchronous processing.

Example:

POST /api/invoices/upload

returns:

202 Accepted

with job ID.

Background processing:

Upload
→ OCR
→ AI
→ validation
→ duplicate detection
→ anomaly detection

Frontend can poll job status or use WebSockets/SSE if appropriate.

Use Redis-backed job/status mechanisms where useful.

Do not introduce Kafka merely for buzzwords.

If RabbitMQ is more appropriate for this scale, use it.

Explain the architectural decision in README.

---

# 39. DOCUMENT STORAGE

Do not store large files directly in PostgreSQL.

Create a storage abstraction.

For development:

local Docker volume.

Structure:

organization/
year/
month/
invoice-id/

The application should be designed so storage can later be moved to:

S3/GCS/Azure Blob.

Do not expose storage paths directly to users.

---

# 40. SEARCH

Implement powerful invoice search.

Search:

* invoice number
* vendor
* amount
* date
* category
* status

Filters:

* date range
* amount range
* vendor
* category
* payment status
* risk
* processing status

---

# 41. NATURAL LANGUAGE SEARCH

Add optional AI-powered search.

Example:

"Show invoices over ₹50,000 from cloud vendors during the last quarter."

The system should translate this into a safe structured filter.

Do not execute arbitrary LLM-generated SQL.

---

# 42. AUDIT LOG

Log important events:

* login
* invoice uploaded
* document rejected
* AI extraction completed
* field modified
* invoice approved
* invoice rejected
* payment recorded
* vendor edited
* budget changed
* user role changed

Store:

* actor
* action
* entity
* timestamp
* relevant metadata
* organization

Do not store passwords/tokens/secrets.

---

# 43. SECURITY REQUIREMENTS

Implement:

* BCrypt password hashing
* JWT authentication
* refresh-token strategy if appropriate
* role-based authorization
* organization-level authorization
* CORS configuration
* secure headers
* request validation
* file upload validation
* rate limiting where appropriate
* secure error responses
* no secrets in source code
* environment variables
* safe logging

Never expose stack traces to normal users.

---

# 44. API DESIGN

Create clean REST APIs.

Examples:

POST /api/auth/register

POST /api/auth/login

GET /api/dashboard

POST /api/invoices/upload

GET /api/invoices

GET /api/invoices/{id}

PUT /api/invoices/{id}

POST /api/invoices/{id}/verify

POST /api/invoices/{id}/approve

POST /api/invoices/{id}/reject

POST /api/invoices/{id}/payments

GET /api/vendors

GET /api/vendors/{id}

GET /api/analytics/expenses

GET /api/analytics/vendors

GET /api/analytics/cash-flow

GET /api/analytics/forecast

GET /api/alerts

GET /api/audit-logs

POST /api/budgets

GET /api/budgets

POST /api/ai/query

Use consistent response/error structures.

---

# 45. ERROR HANDLING

Create a global exception handler.

Return consistent errors.

Example:

{
"timestamp": "...",
"status": 400,
"error": "VALIDATION_ERROR",
"message": "Invoice total does not match extracted line items.",
"path": "/api/invoices/123"
}

Handle:

* invalid input
* unauthorized
* forbidden
* missing entity
* duplicate entity
* file errors
* AI errors
* OCR errors
* database errors
* external API errors
* rate limits
* timeout

Never return generic:

"Something went wrong."

when a useful message can be provided.

---

# 46. DATABASE DESIGN

Create a normalized schema with appropriate indexes.

At minimum consider:

users
organizations
organization_members
roles
permissions
refresh_tokens
vendors
invoices
invoice_line_items
invoice_documents
invoice_extractions
invoice_validation_results
invoice_duplicates
invoice_anomalies
expense_categories
payments
budgets
budget_categories
notifications
audit_logs
processing_jobs
ai_predictions
ai_feedback
recurring_expenses
approval_rules
approval_requests

Do not blindly create all tables if a better design exists.

Use relationships properly.

Add indexes for common query paths.

---

# 47. DATA CONSISTENCY

Use database transactions for operations such as:

invoice approval
payment recording
organization membership changes
budget updates

Avoid partial state updates.

---

# 48. FRONTEND UI/UX

The frontend should look like a real SaaS finance product.

Do NOT create a generic student dashboard.

Use:

* clean sidebar
* top navigation
* cards
* charts
* tables
* filters
* status chips
* meaningful empty states
* skeleton loading
* error states
* confirmation dialogs
* responsive design

Use Material UI consistently.

---

# 49. MAIN FRONTEND PAGES

Create:

/login

/register

/dashboard

/invoices

/invoices/:id

/invoices/upload

/invoices/review/:id

/vendors

/vendors/:id

/expenses

/budgets

/analytics

/analytics/cash-flow

/analytics/forecast

/alerts

/approvals

/reports

/ai-assistant

/audit-logs

/settings

/settings/users

/settings/organization

---

# 50. INVOICE DETAIL UI

Invoice detail should be a professional split view.

Left:

Document viewer.

Right:

Invoice information.

Example:

Invoice:
INV-8291

Vendor:
ABC Technologies

Amount:
₹96,760

Risk:
82/100

Status:
Needs Review

Then:

Extraction confidence

Validation results

Duplicate warnings

Anomaly explanation

Approval history

Payment history

Audit history

---

# 51. UPLOAD EXPERIENCE

Drag-and-drop upload.

Before upload:

* file type
* size
* preview

During processing:

Show pipeline:

✓ File validated

✓ OCR complete

⏳ AI extraction

○ Validation

○ Duplicate detection

○ Risk analysis

After completion:

Show:

"Invoice processed successfully."

or:

"Invoice requires manual review."

---

# 52. FAILED DOCUMENT EXPERIENCE

Never just show:

"Upload failed."

Instead:

Document rejected.

Reason:

"Document appears to be a bank statement rather than an invoice."

or:

"PDF is corrupted."

or:

"Unable to extract sufficient text."

Provide:

* retry
* replace document
* manual entry where appropriate

---

# 53. EMPTY STATES

Every page must have useful empty states.

Example:

"No invoices yet."

"Upload your first invoice to start tracking expenses."

Not blank screens.

---

# 54. LOADING STATES

Use skeletons/spinners appropriately.

Never make the application look frozen during:

* AI processing
* analytics
* uploads

---

# 55. RESPONSIVENESS

Support:

* desktop
* tablet
* mobile

Finance dashboards should prioritize desktop but remain usable on smaller screens.

---

# 56. ACCESSIBILITY

Implement reasonable:

* keyboard navigation
* labels
* semantic HTML
* accessible form controls
* sufficient contrast
* meaningful error messages

---

# 57. ANALYTICS THAT ACTUALLY MATTER

Do NOT fill the dashboard with random charts.

Every visualization must answer a business question.

Examples:

### "Where are we spending money?"

→ Category spending

### "Who are we spending money with?"

→ Vendor ranking

### "Are expenses increasing?"

→ Spending trend

### "Are we overspending?"

→ Budget vs actual

### "What needs attention?"

→ Alerts

### "What payments are coming?"

→ Cash-flow forecast

### "Where might we lose money?"

→ Duplicate/anomaly/risk dashboard

### "Where can we save?"

→ Cost-saving recommendations

---

# 58. REPORTING

Generate:

Monthly Expense Report

Vendor Spend Report

Outstanding Invoice Report

Budget Variance Report

Tax Summary

Anomaly Report

AI Insights Report

Allow export to:

CSV
XLSX
PDF

---

# 59. OBSERVABILITY

Implement:

* Spring Boot Actuator
* health endpoint
* readiness/liveness concepts
* structured logging
* correlation/request IDs
* processing metrics
* AI processing duration
* OCR processing duration

Do not log sensitive document contents.

---

# 60. PERFORMANCE

Design for reasonable scale.

Avoid N+1 database queries.

Use pagination.

Do not load thousands of invoices into frontend memory.

Use server-side filtering.

Use database indexes.

Cache appropriate analytics or reference data with Redis where beneficial.

Process documents asynchronously.

---

# 61. IDEMPOTENCY

Important for financial systems.

Prevent accidental duplicate operations.

For example:

If the same payment request is submitted twice because of a network retry, it must not create two payments.

Use appropriate idempotency mechanisms.

---

# 62. RETRY STRATEGY

External AI/OCR calls can fail.

Implement safe retries with limits.

Do not retry forever.

Example:

Attempt 1
→ failure

Attempt 2
→ failure

Attempt 3
→ mark FAILED

Store failure reason.

Allow manual retry.

---

# 63. AI COST CONTROL

AI calls should not happen unnecessarily.

Examples:

* don't reprocess an already verified invoice
* cache reusable extraction results where appropriate
* use deterministic validation before calling AI
* avoid sending unnecessary document text
* use smaller models for simple classification where appropriate

Track AI usage:

* requests
* failures
* processing time
* estimated token/cost metadata if provider supports it

---

# 64. TESTING

Create real tests.

Backend:

* unit tests
* service tests
* repository tests where useful
* controller/API tests
* security tests
* tenant isolation tests
* validation tests
* duplicate detection tests
* anomaly detection tests

Frontend:

* important component tests
* form validation tests
* API state tests

Integration tests should verify:

Upload
→ processing
→ extraction
→ validation
→ invoice creation

---

# 65. CRITICAL EDGE CASE TESTS

Explicitly test:

1. Empty file
2. Oversized file
3. Wrong MIME type
4. Corrupted PDF
5. Password-protected PDF
6. Image-only PDF
7. Poor OCR
8. Non-invoice document
9. Missing invoice number
10. Missing vendor
11. Missing total
12. Invalid dates
13. Negative amount
14. Tax mismatch
15. Duplicate invoice
16. Near-duplicate invoice
17. AI malformed response
18. AI timeout
19. AI provider unavailable
20. OCR unavailable
21. Database unavailable
22. Redis unavailable
23. Duplicate payment request
24. Unauthorized invoice access
25. Cross-tenant access attempt
26. Invalid role
27. Concurrent invoice updates
28. Partial payment
29. Overpayment
30. Invoice already paid
31. Invoice approval after rejection
32. Invalid status transition
33. Large bulk upload
34. Same document uploaded twice
35. User removed from organization
36. Deleted vendor with historical invoices

The application must fail gracefully.

---

# 66. SEED DATA

Provide realistic demo data.

Create:

2–3 organizations

Several users with different roles.

At least:

50+ vendors

200+ invoices

Multiple categories

Historical payments

Budgets

Recurring expenses

Anomalies

Duplicate candidates

Overdue invoices

Forecast data

This should make the dashboard look meaningful immediately.

Do not use fake random data that produces nonsensical analytics.

Create coherent financial relationships.

---

# 67. DEMO ACCOUNT

Provide development demo credentials in README.

For example:

[admin@example.com](mailto:admin@example.com)

[finance@example.com](mailto:finance@example.com)

[accountant@example.com](mailto:accountant@example.com)

[viewer@example.com](mailto:viewer@example.com)

Clearly state these are development/demo accounts.

Never use real credentials.

---

# 68. DOCKER

Create:

Dockerfile for backend

Dockerfile for frontend

docker-compose.yml

.env.example

Services:

frontend
backend
postgres
redis

Use health checks.

Ensure service startup order is robust.

Do not hardcode localhost incorrectly between Docker containers.

Inside Docker:

backend must connect to:

postgres

not localhost.

---

# 69. ENVIRONMENT CONFIGURATION

Create:

.env.example

Include variables such as:

DATABASE_URL
DATABASE_USERNAME
DATABASE_PASSWORD

JWT_SECRET

REDIS_HOST
REDIS_PORT

AI_PROVIDER
AI_API_KEY

STORAGE_PATH

MAX_FILE_SIZE

OCR configuration

CORS configuration

Never commit actual secrets.

---

# 70. API DOCUMENTATION

Add Swagger/OpenAPI.

Document:

* authentication
* request parameters
* request bodies
* responses
* errors
* authorization requirements

The README should tell me where Swagger is available.

---

# 71. README

Create an excellent README.

Include:

1. Product overview
2. Features
3. Architecture diagram
4. Technology stack
5. Project structure
6. Database architecture
7. AI pipeline
8. Document processing pipeline
9. Security architecture
10. Docker setup
11. Environment variables
12. Running locally
13. Running tests
14. Swagger
15. Demo credentials
16. Screenshots/placeholders
17. Design decisions
18. Known limitations
19. Future improvements

Explain WHY important architectural decisions were made.

---

# 72. PROJECT STRUCTURE

Use a clean structure.

Backend example:

src/main/java/.../

config/
controller/
service/
repository/
entity/
dto/
mapper/
security/
exception/
validation/
ai/
ocr/
processing/
analytics/
forecast/
notification/
audit/
storage/

Frontend:

src/

components/
pages/
layouts/
hooks/
services/
api/
types/
utils/
features/
auth/
dashboard/
invoices/
vendors/
analytics/
budgets/
notifications/

Keep domain logic organized.

---

# 73. CODE QUALITY

Follow:

* SOLID principles
* meaningful naming
* small focused methods
* reusable components
* proper error handling
* no giant classes
* no duplicated business logic
* no magic numbers
* configuration through properties/environment
* comments only where they explain WHY

Do not over-engineer.

---

# 74. DO NOT USE THESE SHORTCUTS

Do NOT:

* create fake analytics
* hardcode dashboard numbers
* hardcode AI responses
* use static JSON instead of backend APIs
* store everything in localStorage
* put business logic in React
* expose database entities directly
* disable authentication for convenience
* ignore authorization
* use frontend-only RBAC
* create placeholder APIs that don't work
* create buttons that do nothing
* create fake AI functionality
* claim ML exists when it doesn't
* use Kafka/Redis/etc. only to put them on the resume

Every major UI action must connect to actual backend functionality.

---

# 75. DEVELOPMENT STRATEGY

Build the application incrementally.

Do NOT attempt to generate everything as disconnected files.

Follow this order:

## Phase 1

Project setup

* repository structure
* Docker
* Spring Boot
* React
* PostgreSQL
* Redis
* Flyway
* environment configuration

## Phase 2

Authentication

* registration
* login
* JWT
* roles
* organization
* tenant isolation

## Phase 3

Invoice core

* upload
* storage
* invoice CRUD
* vendor CRUD
* invoice lifecycle

## Phase 4

Document intelligence

* file validation
* OCR
* classification
* AI extraction
* confidence scoring

## Phase 5

Validation

* business validation
* tax validation
* duplicate detection
* anomaly detection
* risk scoring

## Phase 6

Finance

* payments
* approvals
* budgets
* recurring expenses

## Phase 7

Analytics

* expense analytics
* vendor analytics
* budget analytics
* cash flow
* forecasting

## Phase 8

AI intelligence

* recommendations
* Finance Copilot
* natural-language analytics

## Phase 9

Notifications

* alerts
* email abstraction
* in-app notifications

## Phase 10

Testing + hardening

* unit tests
* integration tests
* security tests
* edge cases

## Phase 11

UI polish

* responsive design
* loading states
* error states
* empty states
* accessibility

## Phase 12

Final Docker/README verification

---

# 76. IMPORTANT: ACTUALLY IMPLEMENT THE PROJECT

Do not stop at:

"Here's the architecture."

I need the actual project.

Create the files.

Implement the code.

Connect frontend and backend.

Create database migrations.

Create Docker configuration.

Create tests.

Create seed data.

Make the application runnable.

If you cannot complete everything in one context window, continue from the existing project rather than replacing completed work.

Keep a clear TODO list and prioritize functional features over superficial UI.

---

# 77. BEFORE FINISHING

Perform a full verification.

Check:

Frontend builds successfully.

Backend builds successfully.

Docker Compose starts.

Database migrations run.

Backend connects to PostgreSQL.

Backend connects to Redis.

Frontend connects to backend.

Authentication works.

Authorization works.

Tenant isolation works.

Invoice upload works.

Invalid files are rejected.

Valid documents enter processing.

Processing status works.

AI extraction works when API key is configured.

Local fallback works without AI key.

Invoice data reaches PostgreSQL.

Dashboard uses actual API data.

Analytics use actual database data.

Payments work.

Approval workflow works.

Duplicate detection works.

Anomaly detection works.

Notifications work.

Audit logs work.

Tests pass.

No obvious console errors.

No broken buttons.

No hardcoded production secrets.

---

# 78. FINAL PRODUCT QUALITY BAR

When I run:

docker compose up --build

I should get a working application.

The experience should feel like:

**a real financial SaaS platform**

rather than:

**a college project demonstrating CRUD + ChatGPT.**

The most important principle is:

> Every AI feature must lead to a useful business action.

For example:

Bad:

"AI generated invoice summary."

Good:

"AI detected that this invoice is 3.2× higher than the vendor's historical average and flagged it for finance review."

Bad:

"AI categorizes invoices."

Good:

"AI categorizes invoices, learns from human corrections, and improves future classification."

Bad:

"AI chatbot."

Good:

"Finance Copilot analyzes actual organizational financial data and explains why spending changed."

---

# 79. FINAL OUTPUT EXPECTED FROM YOU

After implementation, provide:

1. Complete project structure
2. Setup instructions
3. Docker commands
4. Environment variables
5. Database migration explanation
6. API documentation location
7. Demo credentials
8. Architecture explanation
9. AI architecture explanation
10. ML/anomaly detection explanation
11. Important design decisions
12. Testing instructions
13. Known limitations
14. Future improvements
15. Suggested resume bullet points
16. Suggested interview discussion points

Do not claim a feature is implemented unless it actually works.

If a feature requires an external paid API, clearly mark the API dependency and ensure a local development fallback exists.

Build InvoiceIQ as a project that I can confidently put on a software engineering resume and discuss deeply in interviews.

The final standard should be:

**Production-minded architecture + meaningful AI + strong Java backend + modern React frontend + real financial workflows + analytics + security + Docker + testing.**
