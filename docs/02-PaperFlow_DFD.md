# PaperFlow — Design Specification Document
## Digital Newspaper and Magazine Publishing, Selling, and Buying Platform

---

## Table of Contents

1.  [Introduction](#1-introduction)
2.  [Architecture Decisions](#2-architecture-decisions)
3.  [Technology Stack](#3-technology-stack)
4.  [System Architecture Overview](#4-system-architecture-overview)
5.  [Stakeholders and Roles](#5-stakeholders-and-roles)
6.  [API Design Overview](#6-api-design-overview)
7.  [DFD Level 0 — Context Diagram](#7-dfd-level-0--context-diagram)
8.  [DFD Level 1 — Main Processes](#8-dfd-level-1--main-processes)
9.  [DFD Level 2 — Detailed Sub-Processes](#9-dfd-level-2--detailed-sub-processes)
10. [Use Case Diagrams](#10-use-case-diagrams)
11. [Security Design](#11-security-design)
12. [Appendix](#12-appendix)

---
## 1. Introduction

### 1.1 Purpose
This document defines the complete design specification for PaperFlow — a digital newspaper and magazine publishing, selling, and buying platform built for the Sri Lankan publication industry.

### 1.2 Background
Publishers currently distribute digital editions informally via Telegram and WhatsApp, resulting in piracy and revenue loss. PaperFlow provides a centralized, secure marketplace with IP protection, commission tracking, and role-specific reporting.

### 1.3 Scope
- Secure edition publishing, purchasing, and reading
- Server-side watermarking and signed token delivery
- PayPal-integrated marketplace with commission management
- Role-based dashboards for publishers, admins, and finance auditors
- Archive management with scheduled edition lifecycle transitions

---

## 2. Architecture Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Backend | Spring Boot (Monolithic) | Single deployable unit, suitable for current scale |
| Frontend | React  | Component-based UI, integrates with PayPal SDK |
| Database | MySQL (single instance) | Relational integrity, single source of truth |
| PDF Processing | In-process async thread executor | No external queue needed, bounded concurrency |
| Page Image Storage | Local filesystem | Simplest viable storage; no cloud dependency |
| Authentication | JWT — stateless, Bearer header | No session store required |
| Secure Reader | Signed HMAC tokens, 60s TTL | Prevents URL sharing, ties page access to user |
| Payment | PayPal SDK — server-side order + capture | PCI-DSS compliance, no card data on platform |
| Notifications | SMTP email + Spring WebSocket | Standard email delivery; real-time upload status |

---

## 3. Technology Stack

### 3.1 Backend — Spring Boot
| Component | Technology |
|---|---|
| Framework | Spring Boot  |
| Security | Spring Security + JWT (JJWT) |
| ORM | Spring Data JPA + Hibernate |
| Async Processing | Spring @Async + ThreadPoolTaskExecutor |
| Scheduling | Spring @Scheduled (midnight archive job) |
| WebSocket | Spring WebSocket (STOMP) |
| PDF Processing | Apache PDFBox (page splitting + image rendering) |
| Image Watermarking | Java AWT and ImageIO |
| Email | Spring Mail (JavaMailSender) |
| Build | Maven |

### 3.2 Frontend — React
| Component | Technology |
|---|---|
| Framework | React  |
| HTTP Client | Axios |
| Payment UI | PayPal React SDK |
| WebSocket Client | SockJS + STOMP.js |
| State Management | React Context / useState |
| Build Tool | Vite |

### 3.3 Infrastructure
| Component | Technology |
|---|---|
| Database | MySQL  |
| File Storage | Local filesystem (server-mounted) |
| Email Delivery | SMTP server (external) |
| Payment Gateway | PayPal REST API  |

---

## 4. System Architecture Overview

```
  ┌─────────────────────────────────────────────────────────────────────┐
  │                        CLIENT LAYER                                 │
  │                                                                     │
  │   ┌──────────────────────────────────────────────────────────┐      │
  │   │               React SPA (Browser)                        │      │
  │   │   Customer UI │ Publisher Portal │ Admin Dashboard       │      │
  │   │   PayPal SDK  │ WebSocket Client │ Secure Page Reader    │      │
  │   └──────────────────────────┬───────────────────────────────┘      │
  └──────────────────────────────│──────────────────────────────────────┘
                                 │  HTTPS / TLS 
                                 │  JWT Bearer Header
                                 │  WebSocket (STOMP over SockJS)
  ┌──────────────────────────────▼──────────────────────────────────────┐
  │                     SPRING BOOT MONOLITH                            │
  │                                                                     │
  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌──────────┐    │
  │  │  Auth &     │  │  Publisher  │  │  Marketplace│  │  Secure  │    │
  │  │  RBAC       │  │  Portal     │  │  & Payment  │  │  Reader  │    │
  │  │  Controller │  │  Controller │  │  Controller │  │Controller│    │
  │  └─────────────┘  └─────────────┘  └─────────────┘  └──────────┘    │
  │                                                                     │
  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌──────────┐    │
  │  │  Platform   │  │  Finance    │  │  Async PDF  │  │ WebSocket│    │
  │  │  Admin      │  │  Admin      │  │  Thread     │  │ Notifier │    │
  │  │  Controller │  │  Controller │  │  Executor   │  │          │    │
  │  └─────────────┘  └─────────────┘  └─────────────┘  └──────────┘    │
  │                                                                     │
  │  ┌──────────────────────────────────────────────────────────────┐   │
  │  │              Spring Security Filter Chain                    │   │
  │  │         JWT Validation → RBAC → Route to Controller          │   │
  │  └──────────────────────────────────────────────────────────────┘   │
  └───────┬──────────────────┬──────────────────┬───────────────────────┘
          │                  │                  │
          ▼                  ▼                  ▼
  ┌──────────────┐  ┌──────────────────┐  ┌──────────────────────┐
  │   MySQL      │  │ Local Filesystem │  │  External Services   │
  │              │  │                  │  │                      │
  │All relational│  │  /editions/{id}/ │  │  PayPal REST API v2  │
  │  data        │  │  source.pdf      │  │  SMTP Server         │
  │              │  │  pages/          │  │                      │
  └──────────────┘  │  page_001.webp   │  └──────────────────────┘
                    │  page_NNN.webp   │
                    └──────────────────┘
```

### 4.1 Key Architectural Patterns

| Pattern | Description |
|---|---|
| Monolith | Single Spring Boot deployable; all modules co-located |
| RBAC | Spring Security enforces role access on every endpoint |
| Async processing | PDF conversion runs on ThreadPoolTaskExecutor; does not block HTTP response |
| Zero PDF exposure | Active edition PDFs never leave the server; only watermarked page images are delivered |
| Signed token delivery | Every page request requires a fresh HMAC-signed token (60s TTL) |
| Server-side capture | PayPal orders created and captured server-side; client only interacts with PayPal UI |
| Webhook redundancy | PayPal webhook confirms payment independently of client-side flow |
| Scheduled archiving | Spring @Scheduled job runs at midnight to transition eligible editions to ARCHIVED |

---
## 5. Stakeholders and Roles

| Role | Access Level | Key Permissions |
|---|---|---|
| System Administrator | Superuser | All platform operations, account management, security logs |
| Platform Administrator | Operational | Publisher approval, catalog moderation, commission config, archive threshold |
| Publication Company | Publisher portal | Upload editions, manage bundles, view own sales |
| Finance Administrator | Read-only dashboards | Transaction audit log, financial report export |
| Customer | Consumer catalog | Register, browse, purchase, read editions, download archives |

---

## 6. API Design Overview

### 6.1 Authentication

| Method | Endpoint | Role | Description |
|---|---|---|---|
| POST | /api/auth/register | Public | Customer self-registration |
| GET | /api/auth/verify | Public | Email verification via token link |
| POST | /api/auth/login | Public | Returns JWT on valid credentials |
| POST | /api/auth/password-reset | Public | Triggers reset email |

### 6.2 Publisher Portal

| Method | Endpoint | Role | Description |
|---|---|---|---|
| POST | /api/publishers/apply | Public | Submit publisher application |
| PUT | /api/publishers/profile | PUBLISHER | Update company profile |
| POST | /api/editions | PUBLISHER | Upload edition (multipart: PDF + metadata) |
| GET | /api/editions | PUBLISHER | List own editions |
| POST | /api/bundles | PUBLISHER | Create bundle |
| PUT | /api/bundles/{id} | PUBLISHER | Edit bundle |
| GET | /api/publishers/sales | PUBLISHER | Sales dashboard data |

### 6.3 Marketplace

| Method | Endpoint | Role | Description |
|---|---|---|---|
| GET | /api/catalog | Public | Browse catalog with filters |
| POST | /api/orders | CUSTOMER | Create order (single / bundle / archive) |
| POST | /api/orders/{id}/capture | CUSTOMER | Capture PayPal payment |
| POST | /api/orders/webhook | System | PayPal webhook handler |
| GET | /api/orders/my | CUSTOMER | List own purchases |

### 6.4 Secure Reader

| Method | Endpoint | Role | Description |
|---|---|---|---|
| GET | /api/reader/{editionId}/token | CUSTOMER | Get signed page token |
| GET | /api/reader/page | CUSTOMER | Deliver watermarked page image (token required) |
| GET | /api/reader/{editionId}/download | CUSTOMER | Download archive PDF |

### 6.5 Platform Administration

| Method | Endpoint | Role | Description |
|---|---|---|---|
| GET | /api/admin/publishers/pending | PLATFORM_ADMIN | List pending publisher applications |
| PUT | /api/admin/publishers/{id}/approve | PLATFORM_ADMIN | Approve publisher |
| PUT | /api/admin/publishers/{id}/reject | PLATFORM_ADMIN | Reject publisher |
| PUT | /api/admin/publishers/{id}/suspend | PLATFORM_ADMIN | Suspend publisher |
| DELETE | /api/admin/editions/{id} | PLATFORM_ADMIN | Remove edition |
| PUT | /api/admin/config | PLATFORM_ADMIN | Update platform config |
| PUT | /api/admin/commission | PLATFORM_ADMIN | Set commission rates |

### 6.6 Finance Administration

| Method | Endpoint | Role | Description |
|---|---|---|---|
| GET | /api/finance/transactions | FINANCE_ADMIN | List transactions with filters |
| GET | /api/finance/reports/export | FINANCE_ADMIN | Export report as PDF or CSV |

---

## 7. DFD Level 0 — Context Diagram

The entire PaperFlow system shown as a single process with all external actors and data flows.

```
                    ┌──────────────────────────────────────────────────┐
                    │                                                  │
  CUSTOMER          │                                                  │      PAYPAL API
  ─────────         │                                                  │      ──────────
  Registration ────>│                                                  │<──── Webhook
  Login ───────────>│                                                  │────> Order Request
  Browse ──────────>│                                                  │────> Capture Request
  Purchase ────────>│              P A P E R F L O W                   │
  Read page ───────>│                                                  │
  <── Page images   │       Spring Boot + React                        │
  <── PDF download  │                                                  │      SMTP SERVER
  <── Receipt email │                                                  │      ───────────
                    │                                                  │────> Verification
  PUBLICATION CO.   │                                                  │────> Receipts
  ───────────────   │                                                  │────> Alerts
  Apply ───────────>│                                                  │
  Upload PDF ──────>│                                                  │
  Manage bundles ──>│                                                  │
  View sales ──────>│                                                  │
  <── Sales reports │                                                  │
  <── WS status     │                                                  │
                    │                                                  │
  ADMINISTRATORS    │                                                  │
  ───────────────   │                                                  │
  Approve ─────────>│                                                  │
  Configure ───────>│                                                  │
  Audit ───────────>│                                                  │
  <── Dashboards    │                                                  │
  <── Audit logs    │                                                  │
                    │                                                  │
                    └──────────────────────────────────────────────────┘
```
---

## 8. DFD Level 1 — Main Processes

Six primary process areas sharing a central MySQL database and local filesystem.

```
  CUSTOMER         PUBLICATION CO.       ADMINISTRATORS       PAYPAL API    SMTP SERVER
      │                   │                    │                  │               │
      ▼                   ▼                    ▼                  │               │
 ┌─────────┐       ┌────────────┐       ┌────────────┐            │               │
 │  P1     │       │  P2        │       │  P3        │            │               │
 │ Access  │       │ Publisher  │       │ Platform   │            │               │
 │ Control │       │ Portal     │       │ Admin      │            │               │
 └────┬────┘       └─────┬──────┘       └─────┬──────┘            │               │
      │                  │                    │                   │               │
      └──────────────────┴────────────────────┘                   │               │
                         │                                        │               │
                         ▼                                        │               │
          ┌───────────────────────────────────────┐               │               │
          │           MySQL Database              │               │               │
          │  users │ publishers │ editions        │               │               │
          │  bundles │ orders │ transactions      │               │               │
          │  user_edition_access │ page_tokens    │               │               │
          │  audit_log │ commission_config        │               │               │
          │  platform_config                      │               │               │
          └───────────────────────────────────────┘               │               │
                         │                                        │               │
          ┌──────────────┴──────────────┐                         │               │
          ▼                             ▼                         │               │
    ┌────────────┐               ┌────────────┐                   │               │
    │  P4        │               │  P5        │                   │               │
    │ Newspaper  │               │ Financial  │                   │               │
    │ Marketplace│               │ Management │                   │               │
    └─────┬──────┘               └─────┬──────┘                   │               │
          │                            │                          │               │
          │  Order / Capture ──────────┼─────────────────────────>│               │
          │                            │<── Webhook ──────────────│               │
          │                            │                          │               │
          └──────────────┬─────────────┘                                          │
                         │  Email triggers ──────────────────────────────────────>│
                         │
                         ▼
                   ┌────────────┐        ┌──────────────────────────┐
                   │  P6        │        │  Local Filesystem        │
                   │ Secure     │<──────>│  /editions/{id}/pages/   │
                   │ Reader     │        │  page_001.webp           │
                   └────────────┘        └──────────────────────────┘
```

| ID | Process | Description |
|---|---|---|
| P1 | Access Control & Auth | Registration, email verification, JWT issuance, RBAC enforcement |
| P2 | Publisher Portal | Edition upload, async PDF processing, bundle config, sales reporting |
| P3 | Platform Administration | Publisher approval, moderation, commission config, archive scheduler |
| P4 | Newspaper Marketplace | Catalog browsing, checkout, PayPal order creation |
| P5 | Financial Management | Webhook handling, transaction audit logging, report export |
| P6 | Secure Reader | Signed token issuance, watermark application, page delivery, archive download |

---

## 9. DFD Level 2 — Detailed Sub-Processes

### 9.1 P1 — Access Control & Authentication

```
  USER
   │
   │── Registration data
   ▼
 ┌──────────────┐
 │ P1.1         │──────────────────────────> MySQL: INSERT users (UNVERIFIED)
 │ Register     │──────────────────────────> SMTP: Send verification email
 └──────┬───────┘
        │── User clicks email link
        ▼
 ┌──────────────┐
 │ P1.2         │──────────────────────────> MySQL: UPDATE users (ACTIVE)
 │ Verify Email │
 └──────┬───────┘
        │── Login credentials
        ▼
 ┌──────────────┐
 │ P1.3         │──────────────────────────> MySQL: SELECT users, verify BCrypt
 │ Authenticate │──────────────────────────> Return: JWT (userId, role, exp)
 └──────┬───────┘
        │── JWT on every request
        ▼
 ┌──────────────┐
 │ P1.4         │──────────────────────────> Spring Security: validate JWT
 │ RBAC Filter  │                            Route to controller or 403 Forbidden
 └──────────────┘
```

---

### 9.2 P2 — Publisher Portal

```
  PUBLISHER
   │
   │── PDF + metadata
   ▼
 ┌──────────────┐
 │ P2.1         │──────────────────────────> Local FS: store source.pdf
 │ Upload       │──────────────────────────> MySQL: INSERT edition (PROCESSING)
 │ Edition      │──────────────────────────> Return: 202 Accepted
 └──────┬───────┘
        │── Async thread triggered
        ▼
 ┌──────────────────────────────────────┐
 │ P2.2  Async Thread Executor          │
 │  Read PDF → split pages              │──> Local FS: page_001.webp ... page_N.webp
 │  Render WebP/JPEG                    │──> MySQL: UPDATE edition (ACTIVE)
 │                                      │──> WebSocket: notify publisher SUCCESS/FAILED
 └──────────────────────────────────────┘
        │
        │── Bundle configuration
        ▼
 ┌──────────────┐
 │ P2.3         │──────────────────────────> MySQL: INSERT/UPDATE bundles
 │ Bundle Setup │                            Calculate discounted total price
 └──────┬───────┘
        │── Sales view request
        ▼
 ┌──────────────┐
 │ P2.4         │──────────────────────────> MySQL: SELECT transactions by publisher
 │ Sales Report │                            Aggregate: gross, commission, net payout
 └──────────────┘
```

---

### 9.3 P3 — Platform Administration

```
  PLATFORM ADMIN
   │
   │── View pending publishers
   ▼
 ┌──────────────┐
 │ P3.1         │──────────────────────────> MySQL: SELECT publishers (PENDING)
 │ Publisher    │──────────────────────────> MySQL: UPDATE publishers (APPROVED/REJECTED)
 │ Approval     │──────────────────────────> MySQL: INSERT audit_log
 └──────┬───────┘                            SMTP: Notify publisher
        │── Commission config
        ▼
 ┌──────────────┐
 │ P3.2         │──────────────────────────> MySQL: UPDATE commission_config
 │ Commission   │                            (SINGLE / BUNDLE / ARCHIVE rates)
 │ Config       │
 └──────┬───────┘
        │── Archive threshold / discount max
        ▼
 ┌──────────────┐
 │ P3.3         │──────────────────────────> MySQL: UPDATE platform_config
 │ Platform     │
 │ Settings     │
 └──────┬───────┘
        │── Spring @Scheduled — midnight
        ▼
 ┌──────────────┐
 │ P3.4         │──────────────────────────> MySQL: SELECT editions WHERE
 │ Archive      │                            publication_date < (NOW - threshold)
 │ Scheduler    │──────────────────────────> MySQL: UPDATE editions (ARCHIVED)
 └──────┬───────┘
        │── Moderation action
        ▼
 ┌──────────────┐
 │ P3.5         │──────────────────────────> MySQL: UPDATE editions/publishers (SUSPENDED)
 │ Moderation   │──────────────────────────> MySQL: INSERT audit_log (actor, IP, payload)
 └──────────────┘
```

---

### 9.4 P4 — Newspaper Marketplace

```
  CUSTOMER
   │
   │── Search / filter
   ▼
 ┌──────────────┐
 │ P4.1         │──────────────────────────> MySQL: SELECT editions (filters: lang/date/pub/title)
 │ Catalog      │                            Return: editions with ACTIVE/ARCHIVED badge
 │ Browse       │
 └──────┬───────┘
        │── Purchase intent
        ▼
 ┌──────────────┐
 │ P4.2         │──────────────────────────> MySQL: SELECT commission_config
 │ Checkout     │                            Calculate: gross, commission, net
 │ & Pricing    │                            Apply bundle discount if applicable
 └──────┬───────┘
        │── Confirm purchase
        ▼
 ┌──────────────┐
 │ P4.3         │──────────────────────────> PayPal API: Create order (server-side)
 │ Order        │<── order_id from PayPal
 │ Creation     │──────────────────────────> MySQL: INSERT orders (PENDING)
 └──────┬───────┘                            Return order_id to React
        │── Customer approves in PayPal UI
        ▼
 ┌──────────────┐
 │ P4.4         │──────────────────────────> PayPal API: Capture order (server-side)
 │ Payment      │──────────────────────────> MySQL: UPDATE orders (COMPLETED)
 │ Capture      │──────────────────────────> MySQL: INSERT transactions
 └──────┬───────┘                            MySQL: INSERT user_edition_access
        │                                    SMTP: Send receipt
        ▼
  Access granted to purchased editions
```

---

### 9.5 P5 — Financial Management

```
  FINANCE ADMIN / SYSTEM
   │
   │── PayPal webhook (payment.captured)
   ▼
 ┌──────────────┐
 │ P5.1         │──────────────────────────> Validate PayPal webhook signature
 │ Webhook      │──────────────────────────> MySQL: SELECT orders (match payment ref)
 │ Handler      │──────────────────────────> MySQL: UPDATE orders (COMPLETED)
 └──────┬───────┘                            (redundancy for client disconnect)
        │── Confirmed transaction
        ▼
 ┌──────────────┐
 │ P5.2         │──────────────────────────> MySQL: INSERT transactions
 │ Audit Log    │                            (gross, commission_rate_snapshot,
 │ Entry        │                             net_amount, paypal_reference, timestamp)
 └──────┬───────┘
        │── Finance admin requests report
        ▼
 ┌──────────────┐
 │ P5.3         │──────────────────────────> MySQL: SELECT transactions (filters)
 │ Report       │──────────────────────────> Export: PDF or CSV
 │ Export       │
 └──────────────┘
```

---

### 9.6 P6 — Secure Reader

```
  CUSTOMER (authenticated, edition purchased)
   │
   │── Open edition
   ▼
 ┌──────────────┐
 │ P6.1         │──────────────────────────> MySQL: SELECT user_edition_access (verify ownership)
 │ Access       │──────────────────────────> MySQL: SELECT editions (verify ACTIVE/ARCHIVED)
 │ Verification │
 └──────┬───────┘
        │── Active edition confirmed
        ▼
 ┌──────────────┐
 │ P6.2         │──────────────────────────> Generate HMAC-signed token:
 │ Token        │                            { userId, editionId, pageNumber, exp: now+60s }
 │ Issuance     │──────────────────────────> MySQL: INSERT page_tokens
 └──────┬───────┘
        │── Token + page request
        ▼
 ┌──────────────┐
 │ P6.3         │──────────────────────────> Validate token (not expired, user-bound)
 │ Page Image   │──────────────────────────> Local FS: Read page_XXX.webp
 │ Delivery     │──────────────────────────> Apply watermark (userId, email, timestamp)
 └──────┬───────┘                            Return watermarked WebP/JPEG
        │                                    Cache-Control: no-store
        │
        │   React client-side protections:
        │   right-click disabled, drag disabled
        │
        │── Archive edition confirmed
        ▼
 ┌──────────────┐
 │ P6.4         │──────────────────────────> Local FS: Retrieve source.pdf
 │ Archive PDF  │──────────────────────────> Stream PDF directly to browser
 │ Download     │
 └──────────────┘
```

---


## 10. Use Case Diagrams

### 10.1 Customer

```
                   ┌─────────────────────────────────────────────┐
                   │               PaperFlow System              │
  ┌──────────┐     │                                             │
  │          │────>│  UC-C-01  Register account                  │
  │          │     │           (includes email verification)     │
  │          │────>│  UC-C-02  Login                             │
  │          │     │                                             │
  │          │────>│  UC-C-03  Browse and search catalog         │
  │ CUSTOMER │     │                                             │
  │          │────>│  UC-C-04  Purchase single edition           │
  │          │     │           <<extends>> UC-C-07 PayPal flow   │
  │          │────>│  UC-C-05  Purchase bundle                   │
  │          │     │           <<extends>> UC-C-07 PayPal flow   │
  │          │────>│  UC-C-06  Purchase archive edition          │
  │          │     │           <<extends>> UC-C-07 PayPal flow   │
  │          │────>│  UC-C-07  Complete PayPal payment           │
  │          │     │                                             │
  │          │────>│  UC-C-08  Read active edition               │
  │          │     │           (secure page-by-page reader)      │
  │          │────>│  UC-C-09  Download archive PDF              │
  │          │────>│  UC-C-10  Reset password                    │
  └──────────┘     │                                             │
                   └─────────────────────────────────────────────┘
```

---

### 10.2 Publication Company

```
                       ┌─────────────────────────────────────────────────┐
                       │               PaperFlow System                  │
  ┌──────────────┐     │                                                 │
  │ PUBLICATION  │     │                                                 │
  │ COMPANY      │────>│  UC-P-01  Apply for publisher account           │
  │              │     │                                                 │
  │              │────>│  UC-P-02  Configure publisher profile           │
  │              │     │              (reg. no., bank, email)            │
  │              │────>│  UC-P-03  Upload edition                        │
  │              │     │              (PDF + metadata)                   │
  │              │────>│  UC-P-04  Monitor PDF conversion status         │
  │              │     │              (WebSocket notification)           │
  │              │────>│  UC-P-05  Manage multiple titles                │
  │              │     │                                                 │
  │              │────>│  UC-P-06  Create and configure bundle           │
  │              │────>│  UC-P-07  View sales dashboard                  │
  └──────────────┘     │                                                 │
                       └─────────────────────────────────────────────────┘
```

---

### 10.3 Administrators

```
                       ┌─────────────────────────────────────────────────┐
                       │               PaperFlow System                  │
  ┌──────────────┐     │                                                 │
  │ PLATFORM     │────>│ UC-A-01  Review and approve publisher           │
  │ ADMIN        │────>│ UC-A-02  Reject publisher application           │
  │              │────>│ UC-A-03  Configure commission rates             │
  │              │────>│  UC-A-04  Configure archive threshold           │
  │              │────>│  UC-A-05  Configure max bundle discount         │
  │              │────>│  UC-A-06  Remove non-compliant edition          │
  │              │────>│  UC-A-07  Suspend publisher account             │
  └──────────────┘     │                                                 │
                       │                                                 │
  ┌──────────────┐     │                                                 │
  │ FINANCE      │────>│  UC-F-01  View transaction audit log            │
  │ ADMIN        │────>│  UC-F-02  Filter transactions                   │
  │              │────>│  UC-F-03  Export financial report (PDF/CSV)     │
  └──────────────┘     │                                                 │
                       │                                                 │
  ┌──────────────┐     │                                                 │
  │ SYSTEM       │────>│  UC-S-01  Manage all user accounts              │
  │ ADMIN        │────>│  UC-S-02  View security and audit logs          │
  │              │────>│  UC-S-03  Full platform configuration           │
  └──────────────┘     │                                                 │
                       │                                                 │
  ┌──────────────┐     │                                                 │
  │ PAYPAL API   │────>│  UC-EX-01 Trigger payment webhook               │
  │ (external)   │     │                                                 │
  └──────────────┘     │                                                 │ 
                       │                                                 │
  ┌──────────────┐     │                                                 │
  │ SMTP SERVER  │<────│  UC-EX-02 Receive and deliver emails            │
  │ (external)   │     │                                                 │
  └──────────────┘     │                                                 │
                       └─────────────────────────────────────────────────┘
```



## 11. Security Design

### 11.1 Authentication and Authorization

| Layer | Mechanism |
|---|---|
| Password storage | BCrypt hashing (Spring Security default) |
| Authentication | JWT — stateless, signed, Authorization: Bearer header on every request |
| Authorization | Spring Security RBAC — role checked per endpoint |
| Token expiry | JWT configured with expiry; short-lived signed page tokens (60s TTL) |
| Transport | TLS 1.3 on all connections |

### 11.2 Secure Reader Design

| Control | Implementation |
|---|---|
| Zero PDF exposure | Active PDFs stored only on local FS; never streamed to browser |
| Page token binding | HMAC-signed token bound to userId + editionId + pageNumber + expiry |
| Token TTL | 60 seconds — prevents URL reuse or sharing |
| Watermarking | Server-side overlay applied before delivery: userId, email, timestamp |
| Client protections | React disables right-click, drag, and sets Cache-Control: no-store |

### 11.3 Payment Security

| Control | Implementation |
|---|---|
| PCI-DSS compliance | All card entry delegated to PayPal SDK — no card data on PaperFlow |
| Server-side capture | Order creation and capture happen in Spring Boot, not browser |
| Webhook verification | PayPal webhook signature validated before processing |
| Redundancy | Webhook provides secondary confirmation in case of client disconnect |

### 11.4 Audit Trail

Every administrative action is recorded in the `audit_log` table with:

- Actor user ID
- IP address
- Timestamp
- Action type
- Target entity type and ID
- Full request payload (JSON)

---

## 12. Appendix

### A — Data Store Reference

| Store | Type | Contents |
|---|---|---|
| MySQL | Relational DB | users, publishers, editions, bundles, orders, transactions, user_edition_access, page_tokens, commission_config, platform_config, audit_log |
| Local Filesystem | File storage | /editions/{id}/source.pdf, /editions/{id}/pages/page_001.webp … page_N.webp |

### B — External System Interfaces

| System | Protocol | Direction | Purpose |
|---|---|---|---|
| PayPal REST API v2 | HTTPS | Outbound | Order creation and payment capture |
| PayPal Webhook | HTTPS POST | Inbound | Payment confirmation (redundancy) |
| SMTP Server | SMTP / TLS | Outbound | Registration verification, receipts, publisher alerts, admin notifications |

### C — Commission Model

| Transaction Type | Rate Source | Applied At |
|---|---|---|
| Single edition | commission_config (SINGLE) | Checkout — deducted from gross |
| Bundle | commission_config (BUNDLE) | Checkout — deducted after discount applied |
| Archive edition | commission_config (ARCHIVE) | Checkout — deducted from archive price |

Commission rate snapshot is recorded with every transaction in `transactions.commission_rate_snapshot` for audit accuracy even if rates change later.

### D — Notification Events

| Event | Channel | Recipient |
|---|---|---|
| Customer registration | Email (SMTP) | Customer |
| Email verification | Email (SMTP) | Customer |
| Purchase receipt | Email (SMTP) | Customer |
| Password reset | Email (SMTP) | Customer |
| Publisher application submitted | Email (SMTP) | Platform Admin |
| Publisher approved | Email (SMTP) | Publisher |
| Publisher rejected | Email (SMTP) | Publisher |
| PDF conversion success | WebSocket | Publisher |
| PDF conversion failed | WebSocket | Publisher |

---

*© 2026 PaperFlow. All rights reserved. — End of Document —*








