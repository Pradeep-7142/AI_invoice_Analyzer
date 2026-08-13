# ⚡ InvoiceIQ — AI-Powered Invoice & Expense Intelligence Platform

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg?logo=openjdk)](https://openjdk.org/)
[![Spring Boot 3.3](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen.svg?logo=springboot)](https://spring.io/projects/spring-boot)
[![React 18](https://img.shields.io/badge/React-18-blue.svg?logo=react)](https://react.dev/)
[![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16-336791.svg?logo=postgresql)](https://www.postgresql.org/)
[![Redis 7](https://img.shields.io/badge/Redis-7-red.svg?logo=redis)](https://redis.io/)
[![Groq Llama 3.1](https://img.shields.io/badge/AI%20Engine-Groq%20Llama%203.1-blueviolet.svg)](https://groq.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**InvoiceIQ** is an enterprise-grade, multi-tenant B2B SaaS platform that automates invoice processing, deterministic validation, role-based approval workflows, and financial analytics. It combines **LLM-powered document intelligence** (Groq / Gemini / OpenAI) with **deterministic accounting rules**, **duplicate/anomaly detection**, and a **grounded conversational Finance Copilot**.

---

## 🌟 Key Features & Capabilities

### 1. 🤖 Hybrid AI Document Intelligence Pipeline
- **Smart Extraction:** Ingests PDFs, scanned images, and photos (PNG, JPG, WEBP). Extracts vendor names, invoice numbers, dates, subtotal, taxes, GSTIN, and line items.
- **Explainable Confidence Scores:** Provides per-field certainty metrics ($0.0 \to 1.0$) so human reviewers can immediately verify low-confidence fields.
- **Multi-Engine AI Support:** Supports **Groq (`llama-3.1-8b-instant`)**, **Google Gemini**, **OpenAI**, and an offline **Deterministic Heuristic Engine** with zero external dependencies.
- **Graceful Fallback & Zero Crashes:** Corrupted files or non-invoice documents (e.g. bank statements) are gracefully marked `REJECTED` with human-readable reasons, preserving full audit history.

### 2. 🛡️ Deterministic Validation & Anomaly Risk Scoring
- **10-Rule Math Engine:** Evaluates reconciliation ($\text{Subtotal} + \text{Tax} = \text{Total}$), non-negative amounts, GSTIN patterns, and valid date sequences.
- **Duplicate Detection:** Computes duplicate likelihood across invoice numbers, amounts, and dates, flagging suspect invoices without silent data loss.
- **Statistical Anomaly Detection:** Calculates z-scores against vendor-specific historical averages to flag irregular price spikes.
- **Explainable Risk Score (0–100):** Every point added to the risk score links to an exact audit trail (`riskReasons`).

### 3. 👥 Multi-Tenant Role-Based Access Control (RBAC) & Governance
- **5 Granular Roles:** `ORGANIZATION_ADMIN`, `FINANCE_MANAGER`, `ACCOUNTANT`, `EMPLOYEE`, and `VIEWER`.
- **Tenant Isolation:** Enforced server-side via authenticated security context (`CurrentUser`) extracted from validated JWTs — no tenant ID is ever accepted in request paths.
- **Separation of Duties:** Submitters are strictly blocked from approving their own invoices, regardless of their administrative rank.
- **Dynamic Approval Thresholds:** Configurable manager and admin approval limits that route invoices automatically based on monetary value.

### 4. 💬 Grounded AI Finance Copilot & Assistant
- **Conversational Intelligence:** Ask natural language questions like *"What is our total spend on Cloud vendors?"* or *"Show invoices pending approval above ₹50,000"*.
- **100% Grounded Context:** Answers are synthesized directly from verified database records with zero hallucinated figures.
- **Proactive Cost-Saving Recommendations:** Automatically surfaces recurring pacing anomalies, unbudgeted overruns, and vendor concentration risks.

### 5. 🔍 Natural Language Search & Filtering
- Translates plain English search prompts (e.g. *"Show software subscriptions from last month over 10000"*) into structured database query criteria.

### 6. 💳 Payment Lifecycle & Cash Flow Forecasting
- **Dynamic Status Derivation:** Tracks payment tranches ($\text{SCHEDULED} \to \text{PARTIALLY\_PAID} \to \text{PAID}$) derived from actual verified transactions.
- **Committed Cash-Flow Forecast:** Projects weekly cash obligations based on active scheduled payments and outstanding invoice maturities.
- **Category Budgets:** Enforces recurring monthly department limits with real-time utilization pacing.

---

## 🏛️ System Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           React 18 + Vite SPA                           │
│     (Material UI · TanStack Query · Recharts · React Hook Form · Zod)    │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │  JWT (Bearer) + HTTPS
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                     Spring Boot 3.3 REST API (Java 21)                  │
├──────────────────────────────────┬──────────────────────────────────────┤
│  Security & Tenancy              │  Processing & Intelligence           │
│  - JwtAuthenticationFilter       │  - DocumentIntelligenceService       │
│  - Opaque Refresh Token Hash     │  - LlmExtractionService (Groq/Gemini)│
│  - @PreAuthorize Role Guards     │  - Tesseract OCR / PDFBox Parser     │
├──────────────────────────────────┼──────────────────────────────────────┤
│  Business & Finance Engine       │  Analytics & Copilot                 │
│  - InvoiceValidationService (10) │  - FinanceCopilotService             │
│  - RiskScoring & Duplicates      │  - CostSavingEngineService           │
│  - ApprovalPolicy & Workflow     │  - CashFlowForecastService           │
│  - PaymentLifecycle Engine       │  - NaturalLanguageSearchService      │
└────────────────┬─────────────────┴──────────────────┬───────────────────┘
                 │                                     │
                 ▼                                     ▼
┌─────────────────────────────────┐   ┌───────────────────────────────────┐
│        PostgreSQL 16 DB         │   │            Redis 7                │
│    (Flyway Migrations, JPA)     │   │   (Job Status, Cache Invalidation)│
└─────────────────────────────────┘   └───────────────────────────────────┘
```

---

## 🛠️ Technology Stack

| Layer | Technology | Key Highlights |
| :--- | :--- | :--- |
| **Backend** | Java 21, Spring Boot 3.3 | Spring Security, Spring Data JPA, Flyway, MapStruct, SpringDoc OpenAPI |
| **Frontend** | React 18, TypeScript, Vite | Material UI v5, TanStack Query v5, Recharts, React Router v6 |
| **Database** | PostgreSQL 16 | ACID transactions, tenant indexing, Flyway versioned migrations |
| **Cache / Queue** | Redis 7 | Background async status tracking and query caching |
| **AI / LLM** | Groq Llama 3.1 / Gemini / OpenAI | Structured JSON extraction, system prompts, grounded Copilot reasoning |
| **OCR & Docs** | Apache PDFBox, Apache Tika, Tesseract | MIME validation, selectable text extraction, scanned image OCR |
| **Infra & DevOps** | Docker, Docker Compose | Multi-container automated orchestration and isolated networking |

---

## 🚀 Quick Start (Up & Running in 60 Seconds)

### Prerequisites
- [Docker & Docker Compose](https://docs.docker.com/get-docker/)

### 1. Clone & Configure Environment
```bash
git clone https://github.com/Pradeep-7142/AI_invoice_Analyzer.git
cd AI_invoice_Analyzer

# Copy environment template
cp .env.example .env
```

### 2. Configure AI Provider (Optional)
Open `.env` in your text editor:
* **Option A (Groq - Recommended, Free & Ultra-Fast):**
  ```properties
  AI_PROVIDER=custom
  AI_BASE_URL=https://api.groq.com/openai/v1
  AI_MODEL=llama-3.1-8b-instant
  AI_API_KEY=gsk_your_groq_api_key_here
  ```
* **Option B (Offline Heuristic Mode - Zero API Keys Needed):**
  ```properties
  AI_PROVIDER=mock
  ```

### 3. Launch the Stack
```bash
docker compose up -d --build
```

### 4. Access the Application
* 🌐 **Frontend Web Portal:** [http://localhost:5173](http://localhost:5173)
* 📖 **Interactive Swagger API Docs:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
* 🩺 **Backend Health Endpoint:** [http://localhost:8080/api/health](http://localhost:8080/api/health)

---

### Run Backend Unit & Integration Tests:
```bash
cd backend
mvn test
```
*Note: Backend integration tests utilize an embedded real PostgreSQL binary (`io.zonky.test:embedded-postgres`), executing Flyway migrations and true transactional queries without requiring an external database.*

---

## 🔐 Role-Based Access Control (RBAC) Matrix

| Feature / Action | Org Admin | Finance Manager | Accountant | Employee | Viewer |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Upload Invoices** | ✅ | ✅ | ✅ | ✅ (Own Only) | ❌ |
| **Verify Extracted Fields** | ✅ | ✅ | ✅ | ❌ | ❌ |
| **Approve Invoices (< Threshold)** | ✅ | ✅ | ❌ | ❌ | ❌ |
| **Approve Invoices (High Value)** | ✅ | ❌ | ❌ | ❌ | ❌ |
| **Schedule & Settle Payments** | ✅ | ✅ | ❌ | ❌ | ❌ |
| **Manage Department Budgets** | ✅ | ✅ | ❌ | ❌ | ❌ |
| **View Executive Analytics** | ✅ | ✅ | ✅ | ❌ (403) | ✅ |
| **Configure Org Thresholds** | ✅ | ❌ (403) | ❌ (403) | ❌ (403) | ❌ (403) |
| **AI Copilot & Search** | ✅ | ✅ | ✅ | ✅ (Scoped) | ✅ |

---

## ⚙️ Environment Configuration Reference

| Variable | Description | Default |
| :--- | :--- | :--- |
| `JWT_SECRET` | 256-bit secret key for signing JWT tokens | `dev-secret-key-32-bytes-long...` |
| `AI_PROVIDER` | Active AI provider (`custom`, `gemini`, `openai`, `mock`) | `mock` |
| `AI_BASE_URL` | API Base URL for LLM requests | `https://api.groq.com/openai/v1` |
| `AI_MODEL` | LLM model identifier | `llama-3.1-8b-instant` |
| `AI_API_KEY` | Secret API key for the chosen LLM provider | - |
| `STORAGE_PATH` | Local disk root directory for stored invoice files | `./uploads` |
| `MAX_FILE_SIZE` | Maximum allowed file upload size | `10MB` |
| `CORS_ALLOWED_ORIGINS` | Comma-separated list of allowed web origins | `http://localhost:5173` |

---

## 📄 License
This project is licensed under the **MIT License** — feel free to use and adapt it for your personal projects or portfolio.
