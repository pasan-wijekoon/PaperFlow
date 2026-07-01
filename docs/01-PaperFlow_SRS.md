# PaperFlow — System Requirements Specification (SRS)
## Digital Newspaper and Magazine Publishing, Selling, and Buying Platform


## Table of Contents

1. [Introduction](#1-introduction)
2. [System Overview](#2-system-overview)
3. [Stakeholders and Access Levels](#3-stakeholders-and-access-levels)
4. [Functional Requirements](#4-functional-requirements)
5. [Non-Functional Requirements](#5-non-functional-requirements)
6. [Limitations and Assumptions](#6-limitations-and-assumptions)

---

## 1. Introduction

### 1.1 Purpose
This System Requirements Specification (SRS) defines the functional and non-functional requirements for **PaperFlow**, a digital newspaper and magazine publishing, selling, and buying platform. It serves as the single source of truth for engineering, product, and business stakeholders throughout design, development, and validation.

### 1.2 Background
Digital newspaper editions in the Sri Lankan publication industry are currently distributed informally through Telegram and WhatsApp groups. This practice results in significant revenue loss for publishers due to unauthorized sharing and piracy. PaperFlow addresses this gap by providing a centralized distribution channel, a secure reading experience, and a direct marketplace connecting publishers and readers.

### 1.3 Scope and Value Proposition
- **Publishing and Sales**: Publication companies upload, manage, and sell newspaper editions, either individually or as discounted custom bundles.
- **IP Protection**: Active editions are rendered through a secure, page-by-page browser interface that prevents downloading or extraction.
- **Commission Tracking**: Platform commissions are automatically calculated and deducted from each transaction, based on configurable rates.
- **Reporting**: Role-specific dashboards provide administrators, publishers, and finance auditors with relevant operational and financial visibility.

### 1.4 Definitions and Abbreviations

| Term / Acronym | Description |
| :--- | :--- |
| **Publication Company** | A registered local newspaper or magazine publisher on PaperFlow. |
| **Secure Page Reader** | An in-browser viewer that renders pages as server-generated WebP/JPEG images; no PDF file is ever sent to the browser. |
| **Signed Page Token** | A short-lived (60-second TTL), user-bound token used to authorize retrieval of individual page images. |
| **Watermark** | A visible overlay on rendered page images containing the buyer's identifying metadata, used to discourage screenshot piracy. |
| **Archive Edition** | A past publication, older than the configured threshold, made available at a reduced price and as a downloadable PDF. |

---

## 2. System Overview

### 2.1 System Description
PaperFlow connects readers and publication houses across Sri Lanka through a single platform built around a **Single Content Model**.

> **Single Content Model Rule**
> Active editions are strictly read-only within the platform. Once an edition exceeds the configured archive age, it transitions to a downloadable file. This allows publishers to monetize their back catalog while protecting active-issue print revenue from unauthorized redistribution.

**External Interactions**

| Actor | Interaction |
| :--- | :--- |
| Publication Company | Uploads and sells editions; manages bundles |
| Customer | Purchases, browses, and securely reads editions; downloads archives |
| PayPal API | Processes payments; triggers payment-success webhooks |
| SMTP Server | Sends receipts, notifications, and account alerts |

### 2.2 Core Business Rules

> **Key Architectural Policies**
> 1. **Zero PDF Exposure** — Active newspaper PDFs are retained on private filesystem storage only. Readers receive page-by-page, server-watermarked WebP/JPEG images delivered via short-lived signed tokens.
> 2. **Asynchronous PDF Processing** — PDF-to-image conversion runs asynchronously through a background thread executor, without blocking user-facing requests.
> 3. **PayPal Order Validation** — Payments are initialized and captured server-side; webhook delivery provides redundancy in case of client-side disconnects.
> 4. **Archive Thresholds** — A scheduled job archives eligible editions at midnight, updating access rights to enable PDF downloads.

---

## 3. Stakeholders and Access Levels

| Role | Primary Responsibilities | Access Level |
| :--- | :--- | :--- |
| **System Administrator** | Account management, platform configuration, security logs | Superuser (full access) |
| **Platform Administrator** | Publisher approval, catalog curation, commission configuration | Operational control |
| **Publication Company** | Profile setup, edition uploads, bundle configuration, sales reporting | Publisher portal |
| **Finance Administrator** | Transaction auditing, payout management, financial report export | Read-only dashboards |
| **Customer** | Account registration, catalog browsing, edition/archive purchase and reading | Consumer catalog |

---

## 4. Functional Requirements

### 4.1 Access Control and Authentication
| ID | Priority | Requirement |
| :--- | :--- | :--- |
| FR-AC-001 | High | Enforce role-based access control (RBAC) across all roles: Customer, Publisher, Platform Admin, Finance Admin, and System Admin. |
| FR-AC-002 | High | Support customer self-registration with SMTP-based email verification. |
| FR-AC-003 | High | Require Platform Admin review and approval for all publisher applications. |
| FR-AC-004 | High | Authenticate all HTTP requests using JWT, supplied via the `Authorization: Bearer` header. |
| FR-AC-005 | Medium | Enforce multi-device session checks and rate-limiting on authentication attempts. |

### 4.2 Publisher Portal
| ID | Priority | Requirement |
| :--- | :--- | :--- |
| FR-PP-001 | High | Allow publishers to configure profile details, including company registration number, bank details, and official email. |
| FR-PP-002 | High | Allow publishers to upload newspaper editions with metadata: publication date, language, pricing, and source PDF. |
| FR-PP-003 | High | Allow a single publisher account to manage multiple newspaper or magazine titles. |
| FR-PP-004 | High | Allow publishers to configure discounted multi-edition bundles with dynamic pricing calculation. |
| FR-PP-005 | High | Provide publishers with visibility into sales figures, applied commission deductions, and net payouts. |

### 4.3 Content Management
| ID | Priority | Requirement |
| :--- | :--- | :--- |
| FR-CM-001 | High | Provide a searchable catalog, filterable by language, date, publisher, and title. |
| FR-CM-002 | High | Run a scheduled job at midnight to reclassify eligible editions from "active" to "archive" status. |
| FR-CM-003 | High | Display a clear visual indicator distinguishing active editions from archive editions in the catalog. |

### 4.4 Newspaper Marketplace
| ID | Priority | Requirement |
| :--- | :--- | :--- |
| FR-NM-001 | High | Support one-time purchase of a single edition. |
| FR-NM-002 | High | Support purchase of custom bundles, with discounts applied in real time at checkout. |
| FR-NM-003 | High | Support purchase of archive editions at the reduced price set by the publisher. |

### 4.5 Financial Management
| ID | Priority | Requirement |
| :--- | :--- | :--- |
| FR-FM-001 | High | Integrate with PayPal for order creation and payment capture. |
| FR-FM-002 | High | Maintain an audit log recording gross amount, commission rate snapshot, net amount, and payment reference for each transaction. |
| FR-FM-003 | High | Support configurable commission rates by transaction type (single, bundle, archive). |
| FR-FM-004 | High | Provide filterable financial reports, exportable as PDF and CSV, for auditing and tax reconciliation. |

### 4.6 Secure Reader Experience
| ID | Priority | Requirement |
| :--- | :--- | :--- |
| FR-RE-001 | High | Render active editions exclusively page-by-page, in WebP/JPEG image format. |
| FR-RE-002 | High | Apply server-side watermarking with user-identifiable data to each page image prior to delivery. |
| FR-RE-003 | High | Deliver page images via signed tokens with a 60-second time-to-live. |
| FR-RE-004 | High | Apply client-side protections, including disabling right-click context menus, drag actions, and browser caching of rendered pages. |
| FR-RE-005 | High | Allow direct PDF download for purchased archive editions. |

### 4.7 Platform Administration
| ID | Priority | Requirement |
| :--- | :--- | :--- |
| FR-PA-001 | High | Allow administrators to manage platform-wide settings, including archive age threshold and maximum bundle discount limits. |
| FR-PA-002 | High | Allow administrators to remove non-compliant editions or suspend publisher accounts. |
| FR-PA-003 | High | Maintain centralized audit logging of administrative actions, capturing IP address, timestamp, and request payload. |

### 4.8 Notifications
| ID | Priority | Requirement |
| :--- | :--- | :--- |
| FR-NO-001 | High | Send email notifications for registration, purchases, receipts, and password resets. |
| FR-NO-002 | Medium | Provide real-time WebSocket notifications for background task status, such as PDF conversion success or failure. |

---

## 5. Non-Functional Requirements

### 5.1 Performance
| ID | Priority | Requirement |
| :--- | :--- | :--- |
| NFR-PERF-001 | High | Reader pages must load within 1.5 seconds. |
| NFR-PERF-002 | High | The system must support 500 concurrent sessions without measurable latency degradation. |

### 5.2 Security
| ID | Priority | Requirement |
| :--- | :--- | :--- |
| NFR-SEC-001 | High | Encrypt all data in transit using TLS 1.3; store passwords using BCrypt hashing. |
| NFR-SEC-002 | High | Bind signed tokens to the requesting user, session, and specific page parameters. |
| NFR-SEC-003 | High | Maintain PCI-DSS compliance by delegating all payment detail entry to the PayPal SDK. |

### 5.3 Availability and Usability
| ID | Priority | Requirement |
| :--- | :--- | :--- |
| NFR-AVAIL-001 | High | Maintain 99.5% uptime, with database backups scheduled every 24 hours. |
| NFR-USE-001 | Medium | Optimize the UI for Chrome, Safari, Firefox, Edge, and tablet viewport sizes. |

---

## 6. Limitations and Assumptions

**Limitations**
- PaperFlow operates solely as a web application; native iOS and Android readers are out of scope.
- Watermarking deters casual screenshot sharing but cannot fully prevent native OS-level screen capture.

**Assumptions**
- Registered publishers hold valid legal copyright for all uploaded content.

---


*© 2026 PaperFlow. All rights reserved. — End of Document —*