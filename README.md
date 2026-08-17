# ⚡ InvoiceIQ — AI-Powered Invoice Management & Intelligence Platform

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg?logo=openjdk)](https://openjdk.org/)
[![Spring Boot 3.3](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen.svg?logo=springboot)](https://spring.io/projects/spring-boot)
[![React 18](https://img.shields.io/badge/React-18-blue.svg?logo=react)](https://react.dev/)
[![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16-336791.svg?logo=postgresql)](https://www.postgresql.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**InvoiceIQ** is a full-stack web application designed for automated invoice management, OCR data extraction, deterministic math verification, and AI-driven conversational Q&A. It combines **Spring Boot 3.3 + Spring Security + PostgreSQL** on the backend with a responsive **React 18 + TypeScript + Material UI** frontend.

---

## 🌟 Key Features

### 1. 🤖 AI Document Extraction & OCR Pipeline
- **Automated Ingestion:** Ingests PDF and image invoices (PNG, JPG, WEBP). Extracts vendor details, invoice numbers, billing dates, line items, and monetary breakdowns.
- **Confidence Scoring:** Generates per-field confidence scores ($0.0 \to 1.0$) to highlight extracted fields requiring human verification.
- **Pluggable LLM Providers:** Supports **Groq (`llama-3.1-8b-instant`)**, **Google Gemini**, **OpenAI**, and an offline deterministic heuristic fallback engine.

### 2. 🛡️ Deterministic Validation & Duplicate Detection
- **Accounting Validation Engine:** Evaluates math consistency ($\text{Subtotal} + \text{Tax} - \text{Discount} = \text{Total}$), non-negative values, and required invoice metadata.
- **Duplicate Prevention:** Detects potential duplicate submissions across invoice numbers, vendors, dates, and amounts.

### 3. 💬 Interactive AI Invoice Assistant
- **Conversational Q&A:** Query invoices using natural language (e.g. *"Summarize line items"*, *"Is tax calculated accurately?"*, *"What is the payment due date?"*).
- **Grounded Responses:** Answers are dynamically grounded in the specific invoice record.

### 4. 👥 Role-Based Access Control (RBAC) & Authentication
- **Secure JWT Auth:** Stateless token-based authentication with BCrypt password hashing.
- **Roles:**
  - `ROLE_ADMIN`: Full access to upload, verify, approve, reject, archive invoices, manage vendors, and control user roles.
  - `ROLE_EMPLOYEE`: Can upload invoices, view data, and interact with the AI assistant.

---

## 🏛️ System Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           React 18 + Vite SPA                           │
│     (Material UI · TanStack Query · Recharts · React Hook Form · Zod)    │
└────────────────────────────────────┬────────────────────────────────────┘
                                     │  JWT (Bearer) + REST API
                                     ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                     Spring Boot 3.3 REST API (Java 21)                  │
├──────────────────────────────────┬──────────────────────────────────────┤
│  Security & Auth                 │  Processing & Intelligence           │
│  - JwtAuthenticationFilter       │  - DocumentIntelligenceService       │
│  - BCrypt Password Encoder       │  - LlmExtractionService (Groq/Gemini)│
│  - @PreAuthorize Role Guards     │  - Apache PDFBox / Tesseract OCR     │
├──────────────────────────────────┼──────────────────────────────────────┤
│  Invoice Processing Engine       │  AI Assistant & Q&A                  │
│  - InvoiceValidationService      │  - AiAssistantController             │
│  - DuplicateDetectionService     │  - Context-Grounded Prompt Engine    │
│  - Invoice Lifecycle Engine      │                                      │
└──────────────────────────────────┬──────────────────────────────────────┘
                                   │
                                   ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                           PostgreSQL 16 DB                              │
│                    (Flyway Migrations, Spring Data JPA)                 │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 🛠️ Technology Stack

| Layer | Technology | Key Highlights |
| :--- | :--- | :--- |
| **Backend** | Java 21, Spring Boot 3.3 | Spring Security, Spring Data JPA, Flyway, MapStruct, SpringDoc OpenAPI |
| **Frontend** | React 18, TypeScript, Vite | Material UI v5, TanStack Query v5, Recharts, React Router v6 |
| **Database** | PostgreSQL 16 | ACID transactions, relational indexing, Flyway versioned migrations |
| **AI / LLM** | Groq Llama 3.1 / Gemini / OpenAI | Structured JSON extraction, confidence metrics, invoice Q&A |
| **OCR & Docs** | Apache PDFBox, Apache Tika | MIME validation, selectable text extraction, file storage |
| **DevOps** | Docker, Docker Compose | Multi-container automated orchestration |

---

## 🚀 Quick Start (Docker Compose)

### 1. Clone & Configure Environment
```bash
git clone https://github.com/Pradeep-7142/AI_invoice_Analyzer.git
cd AI_invoice_Analyzer

# Copy environment template
cp .env.example .env
```

### 2. Launch Stack
```bash
docker compose up -d --build
```

### 3. Access Application
- 🌐 **Frontend UI:** [http://localhost:5173](http://localhost:5173)
- 📖 **Swagger API Docs:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- 🩺 **Backend Health Check:** [http://localhost:8080/api/health](http://localhost:8080/api/health)

### 4. Demo Login Credentials
- **Admin:** `admin@invoiceiq.com` / `Password123!`
- **Employee:** `employee@invoiceiq.com` / `Password123!`

---

## 📁 Repository Structure

```
├── backend/
│   ├── src/main/java/com/invoiceiq/
│   │   ├── ai/            # LLM clients, extraction prompts & assistant service
│   │   ├── auth/          # Spring Security, JWT filters & auth controller
│   │   ├── config/        # WebMvc, CORS, Swagger & async configs
│   │   ├── dashboard/     # Aggregation metrics & summary API
│   │   ├── invoices/      # Invoices controller, entity, service & storage
│   │   ├── risk/          # Duplicate invoice detection engine
│   │   ├── user/          # User accounts & role management
│   │   ├── validation/    # Deterministic accounting rule engine
│   │   └── vendor/        # Vendor directory & management
│   └── src/main/resources/
│       └── db/migration/  # Flyway schema migrations
├── frontend/
│   ├── src/
│   │   ├── api/           # REST API client services
│   │   ├── features/auth/ # AuthContext & login/register forms
│   │   ├── layouts/       # App navigation & header layout
│   │   ├── pages/         # Dashboard, Invoices, Detail, Vendors, Users, AI Assistant
│   │   └── types/         # TypeScript interfaces & models
└── docker-compose.yml
```

---

## 📄 License
Distributed under the **MIT License**.
