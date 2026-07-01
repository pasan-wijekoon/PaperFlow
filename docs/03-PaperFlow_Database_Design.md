# PaperFlow — Database Design Document
## Digital Newspaper and Magazine Publishing, Selling, and Buying Platform

---

## Table of Contents

1. [Introduction](#1-introduction)
2. [Design Principles](#2-design-principles)
3. [Entity Relationship Diagram](#3-entity-relationship-diagram)
4. [Table Definitions](#4-table-definitions)
   - 4.1 [users](#41-users)
   - 4.2 [publishers](#42-publishers)
   - 4.3 [editions](#43-editions)
   - 4.4 [bundles](#44-bundles)
   - 4.5 [bundle_editions](#45-bundle_editions)
   - 4.6 [orders](#46-orders)
   - 4.7 [order_items](#47-order_items)
   - 4.8 [transactions](#48-transactions)
   - 4.9 [user_edition_access](#49-user_edition_access)
   - 4.10 [page_tokens](#410-page_tokens)
   - 4.11 [commission_config](#411-commission_config)
   - 4.12 [platform_config](#412-platform_config)
   - 4.13 [audit_log](#413-audit_log)
   - 4.14 [notifications](#414-notifications)
   - 4.15 [refresh_tokens / login_attempts](#415-refresh_tokens--login_attempts)
5. [Relationship Summary](#5-relationship-summary)
6. [Normalization Notes](#6-normalization-notes)
7. [Indexing Strategy](#7-indexing-strategy)
8. [Data Integrity Rules](#8-data-integrity-rules)
9. [Storage and Retention](#9-storage-and-retention)
10. [Appendix — DDL Reference](#10-appendix--ddl-reference)

---

## 1. Introduction

### 1.1 Purpose
This document defines the physical database design for **PaperFlow**, derived from the functional and non-functional requirements in the System Requirements Specification (SRS) and the conceptual data model outlined in the Design Specification Document. It specifies tables, columns, data types, keys, constraints, and relationships needed to implement the platform on **MySQL 8.x**.

### 1.2 Scope
The schema supports:
- User accounts and role-based access control (Customer, Publisher, Platform Admin, Finance Admin, System Admin)
- Publisher onboarding and profile management
- Edition upload, lifecycle status, and archive transition
- Bundle creation with discounted multi-edition pricing
- Orders, PayPal-backed payment capture, and commission-tracked transactions
- Secure page-by-page reader access via short-lived signed tokens
- Platform configuration, commission configuration, and administrative audit logging
- Email/WebSocket notification tracking

### 1.3 Source Traceability
Every table in this design traces back to a functional requirement (FR) or non-functional requirement (NFR) in the SRS, and to the conceptual entities first sketched in Section 6 of the Design Specification Document. Traceability is noted per table in Section 4.

---

## 2. Design Principles

| Principle | Application |
|---|---|
| **Single Content Model** | `editions.status` enforces that an edition is either securely streamed (`ACTIVE`) or downloadable (`ARCHIVED`), never both, per FR-RE-001 / FR-RE-005. |
| **Zero PDF Exposure** | `editions.pdf_path` is a private filesystem reference only; no column or API ever returns raw PDF bytes for an `ACTIVE` edition. |
| **Commission Snapshotting** | `transactions.commission_rate_snapshot` freezes the rate at time of sale so later changes to `commission_config` do not alter historical financial records (FR-FM-002). |
| **Least Privilege by Role** | Table design separates publisher-owned data (`publishers`, `editions`, `bundles`) from platform-owned configuration (`platform_config`, `commission_config`) to simplify RBAC enforcement at the application layer. |
| **Auditability** | All administrative mutations are captured in `audit_log` with actor, IP, timestamp, and payload (FR-PA-003). |
| **Token Ephemerality** | `page_tokens` rows are short-lived (60s TTL) and single-use, minimizing the window for replay or sharing (FR-RE-003, NFR-SEC-002). |
| **Normalization with pragmatic denormalization** | Schema is normalized to 3NF; the only deliberate denormalization is the commission/price snapshot fields on `transactions` and `orders`, justified by audit requirements (see Section 6). |

---

## 3. Entity Relationship Diagram

```
┌──────────────────┐      ┌────────────────────────────┐      ┌─────────────────────┐
│ users            │      │ publishers                 │      │ commission_config   │
├──────────────────┤      ├────────────────────────────┤      ├─────────────────────┤
│ PK id            │      │ PK id                      │      │ PK id               │
│    email (UNQ)   │      │ FK user_id (UNQ)           │      │    transaction_type │
│    password_hash │      │    company_name            │      │    rate_percent     │
│    full_name     │      │    registration_number     │      │    effective_from   │
│    role          │      │    bank_account_number_enc │      │ FK set_by           │
│    status        │      │    status                  │      │    created_at       │
│    created_at    │      │    created_at              │      └─────────────────────┘
└──────────────────┘      └────────────────────────────┘

users.id ──1:1── publishers.user_id        users.id ──1:N── commission_config.set_by

┌──────────────────────────┐          ┌─────────────────────┐
│ editions                 │          │ bundles             │
├──────────────────────────┤          ├─────────────────────┤
│ PK id                    │          │ PK id               │
│ FK publisher_id          │          │ FK publisher_id     │
│    title                 │          │    name             │
│    publication_date      │          │    discount_percent │
│    language              │          │    total_price      │
│    price / archive_price │          │    status           │
│    page_count            │          │    created_at       │
│    status                │          └─────────────────────┘
│    pdf_path / pages_dir  │
│    created_at            │
└──────────────────────────┘

publishers.id ──1:N── editions.publisher_id        publishers.id ──1:N── bundles.publisher_id

┌──────────────────┐
│ bundle_editions  │
├──────────────────┤
│ PK FK bundle_id  │
│ PK FK edition_id │
└──────────────────┘

editions.id ──N:M── bundles.id  (join table: bundle_editions)

┌─────────────────────┐      ┌────────────────┐      ┌──────────────────────────┐
│ user_edition_access │      │ page_tokens    │      │ order_items              │
├─────────────────────┤      ├────────────────┤      ├──────────────────────────┤
│ PK id               │      │ PK id          │      │ PK id                    │
│ FK user_id          │      │ FK user_id     │      │ FK order_id              │
│ FK edition_id       │      │ FK edition_id  │      │ FK edition_id (nullable) │
│ FK order_id         │      │    page_number │      │ FK bundle_id (nullable)  │
│    access_source    │      │    token_hash  │      │    line_type             │
│    granted_at       │      │    expires_at  │      │    unit_price            │
└─────────────────────┘      │    used        │      └──────────────────────────┘
                             └────────────────┘

editions.id feeds: user_edition_access.edition_id, page_tokens.edition_id, order_items.edition_id

┌────────────────────┐        ┌─────────────────────────────┐
│ orders             │        │ transactions                │
├────────────────────┤        ├─────────────────────────────┤
│ PK id              │        │ PK id                       │
│ FK user_id         │        │ FK order_id (UNQ)           │
│    order_type      │        │    gross_amount             │
│    gross_amount    │        │    commission_rate_snapshot │
│    discount_amount │        │    commission_amount        │
│    paypal_order_id │        │    net_amount               │
│    status          │        │    paypal_reference         │
│    created_at      │        │    created_at               │
└────────────────────┘        └─────────────────────────────┘

orders.id ──1:1── transactions.order_id (created only when orders.status = COMPLETED)
orders.id ──1:N── order_items.order_id        orders.id ──1:N── user_edition_access.order_id

┌────────────────────────────┐      ┌─────────────────┐      ┌───────────────┐
│ audit_log                  │      │ platform_config │      │ notifications │
├────────────────────────────┤      ├─────────────────┤      ├───────────────┤
│ PK id                      │      │ PK config_key   │      │ PK id         │
│ FK actor_id                │      │    config_value │      │ FK user_id    │
│    action                  │      │ FK updated_by   │      │    channel    │
│    target_type / target_id │      │    updated_at   │      │    event_type │
│    ip_address              │      └─────────────────┘      │    status     │
│    payload (JSON)          │                               │    sent_at    │
│    created_at              │                               └───────────────┘
└────────────────────────────┘

users.id ──1:N── audit_log.actor_id   users.id ──1:N── platform_config.updated_by   users.id ──1:N── notifications.user_id
```

> The diagram above represents logical cardinality only. Exact column-level constraints are defined per table in Section 4.

---

## 4. Table Definitions

### 4.1 `users`

Traces to: FR-AC-001 to FR-AC-005, NFR-SEC-001.

Stores all platform identities regardless of role. A single table is used for all five roles to keep authentication and RBAC logic centralized (per the design's stateless JWT approach).

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `BIGINT UNSIGNED` | PK, AUTO_INCREMENT | Unique user identifier |
| `email` | `VARCHAR(255)` | UNIQUE, NOT NULL | Login identifier; also used for receipts and watermarking |
| `password_hash` | `VARCHAR(255)` | NOT NULL | BCrypt hash (NFR-SEC-001) |
| `full_name` | `VARCHAR(150)` | NOT NULL | Display name |
| `role` | `ENUM('CUSTOMER','PUBLISHER','PLATFORM_ADMIN','FINANCE_ADMIN','SYSTEM_ADMIN')` | NOT NULL | Drives RBAC (FR-AC-001) |
| `status` | `ENUM('UNVERIFIED','ACTIVE','SUSPENDED')` | NOT NULL, DEFAULT `'UNVERIFIED'` | Account state; gates login |
| `email_verified_at` | `DATETIME` | NULL | Set on successful verification link click (FR-AC-002) |
| `failed_login_count` | `SMALLINT UNSIGNED` | NOT NULL, DEFAULT `0` | Supports auth rate-limiting (FR-AC-005) |
| `locked_until` | `DATETIME` | NULL | Temporary lockout timestamp |
| `created_at` | `DATETIME` | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | Account creation time |
| `updated_at` | `DATETIME` | NOT NULL, DEFAULT `CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | Last modification time |

**Indexes:** `UNIQUE (email)`, `INDEX (role, status)`

---

### 4.2 `publishers`

Traces to: FR-PP-001, FR-AC-003.

One-to-one extension of `users` for accounts with `role = 'PUBLISHER'`. Kept separate from `users` to avoid nullable publisher-only columns on every account row, and because publisher application status differs from general account status.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `BIGINT UNSIGNED` | PK, AUTO_INCREMENT | Unique publisher identifier |
| `user_id` | `BIGINT UNSIGNED` | UNIQUE, NOT NULL, FK → `users.id` | Owning login account |
| `company_name` | `VARCHAR(200)` | NOT NULL | Publication company name |
| `registration_number` | `VARCHAR(100)` | NOT NULL | Legal company registration number |
| `official_email` | `VARCHAR(255)` | NOT NULL | Business contact email (distinct from login email) |
| `bank_account_name` | `VARCHAR(150)` | NOT NULL | Payout account holder name |
| `bank_account_number_enc` | `VARCHAR(255)` | NOT NULL | Encrypted-at-rest account number |
| `bank_name` | `VARCHAR(150)` | NOT NULL | Bank for payouts |
| `bank_branch` | `VARCHAR(150)` | NULL | Branch detail |
| `status` | `ENUM('PENDING','APPROVED','REJECTED','SUSPENDED')` | NOT NULL, DEFAULT `'PENDING'` | Application/operational state (FR-AC-003) |
| `approved_by` | `BIGINT UNSIGNED` | NULL, FK → `users.id` | Platform Admin who approved/rejected |
| `approved_at` | `DATETIME` | NULL | Approval timestamp |
| `created_at` | `DATETIME` | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | Application submission time |
| `updated_at` | `DATETIME` | NOT NULL, DEFAULT `CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | Last update |

**Indexes:** `UNIQUE (user_id)`, `INDEX (status)`

**Note:** FR-PP-003 ("a single publisher account to manage multiple newspaper or magazine titles") is satisfied at the `editions` level — a `publisher` may own many `editions` with differing `title` values; no separate "title/masthead" table is required since title is an edition attribute, not a separately governed entity in current scope.

---

### 4.3 `editions`

Traces to: FR-PP-002, FR-CM-001 to FR-CM-003, FR-RE-001, FR-RE-005, core business rule (Single Content Model).

The central content entity. Status drives both the secure-reader pathway and the archive-download pathway, ensuring an edition is never simultaneously exposed via both.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `BIGINT UNSIGNED` | PK, AUTO_INCREMENT | Unique edition identifier |
| `publisher_id` | `BIGINT UNSIGNED` | NOT NULL, FK → `publishers.id` | Owning publisher |
| `title` | `VARCHAR(200)` | NOT NULL | Newspaper/magazine title |
| `publication_date` | `DATE` | NOT NULL | Issue date |
| `language` | `VARCHAR(50)` | NOT NULL | e.g., Sinhala, Tamil, English |
| `price` | `DECIMAL(10,2)` | NOT NULL, CHECK (`price >= 0`) | Active-edition single-purchase price |
| `archive_price` | `DECIMAL(10,2)` | NULL, CHECK (`archive_price >= 0`) | Reduced price once archived (FR-NM-003) |
| `page_count` | `SMALLINT UNSIGNED` | NULL | Populated after PDF conversion completes |
| `status` | `ENUM('PROCESSING','ACTIVE','ARCHIVED','SUSPENDED','FAILED')` | NOT NULL, DEFAULT `'PROCESSING'` | Lifecycle state (FR-CM-002) |
| `pdf_path` | `VARCHAR(500)` | NOT NULL | Private filesystem path to source PDF; never exposed via API while `ACTIVE` |
| `pages_dir` | `VARCHAR(500)` | NULL | Filesystem directory of rendered page images |
| `archived_at` | `DATETIME` | NULL | Set by midnight scheduler (FR-CM-002) |
| `created_at` | `DATETIME` | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | Upload time |
| `updated_at` | `DATETIME` | NOT NULL, DEFAULT `CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | Last status/metadata change |

**Indexes:** `INDEX (publisher_id)`, `INDEX (status)`, `INDEX (language, publication_date)`, `FULLTEXT (title)` — supports the searchable/filterable catalog (FR-CM-001).

---

### 4.4 `bundles`

Traces to: FR-PP-004, FR-NM-002.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `BIGINT UNSIGNED` | PK, AUTO_INCREMENT | Unique bundle identifier |
| `publisher_id` | `BIGINT UNSIGNED` | NOT NULL, FK → `publishers.id` | Owning publisher |
| `name` | `VARCHAR(150)` | NOT NULL | Bundle display name |
| `discount_percent` | `DECIMAL(5,2)` | NOT NULL, CHECK (`discount_percent >= 0 AND discount_percent <= 100`) | Discount applied off the sum of included edition prices; capped by `platform_config` max bundle discount (FR-PA-001) |
| `total_price` | `DECIMAL(10,2)` | NOT NULL | Calculated, cached price at last save (recalculated at checkout for accuracy — see Section 8) |
| `status` | `ENUM('ACTIVE','INACTIVE')` | NOT NULL, DEFAULT `'ACTIVE'` | Whether the bundle is currently purchasable |
| `created_at` | `DATETIME` | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | Creation time |
| `updated_at` | `DATETIME` | NOT NULL, DEFAULT `CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | Last update |

**Indexes:** `INDEX (publisher_id)`, `INDEX (status)`

---

### 4.5 `bundle_editions`

Traces to: FR-PP-004 (many-to-many join between bundles and editions).

| Column | Type | Constraints | Description |
|---|---|---|---|
| `bundle_id` | `BIGINT UNSIGNED` | PK (composite), NOT NULL, FK → `bundles.id` | Bundle reference |
| `edition_id` | `BIGINT UNSIGNED` | PK (composite), NOT NULL, FK → `editions.id` | Edition included in bundle |

**Primary Key:** `(bundle_id, edition_id)` — composite, prevents duplicate inclusion of the same edition in a bundle.

**Constraint:** Application-layer rule restricts all `edition_id` values within one bundle to share the same `publisher_id` as the bundle (publishers may only bundle their own titles).

---

### 4.6 `orders`

Traces to: FR-NM-001 to FR-NM-003, FR-FM-001.

Represents a customer's purchase intent and its PayPal payment lifecycle, independent of what was purchased (single edition, bundle, or archive — itemized in `order_items`).

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `BIGINT UNSIGNED` | PK, AUTO_INCREMENT | Unique order identifier |
| `user_id` | `BIGINT UNSIGNED` | NOT NULL, FK → `users.id` | Purchasing customer |
| `order_type` | `ENUM('SINGLE','BUNDLE','ARCHIVE')` | NOT NULL | Drives commission rate lookup (FR-FM-003) |
| `gross_amount` | `DECIMAL(10,2)` | NOT NULL, CHECK (`gross_amount >= 0`) | Total charged before commission deduction |
| `discount_amount` | `DECIMAL(10,2)` | NOT NULL, DEFAULT `0.00` | Bundle discount applied at checkout (FR-NM-002) |
| `paypal_order_id` | `VARCHAR(100)` | NULL, UNIQUE | PayPal-issued order identifier, set on order creation |
| `status` | `ENUM('PENDING','CAPTURE_PENDING','COMPLETED','CANCELLED','FAILED','REFUNDED')` | NOT NULL, DEFAULT `'PENDING'` | Payment lifecycle state |
| `created_at` | `DATETIME` | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | Order creation time |
| `updated_at` | `DATETIME` | NOT NULL, DEFAULT `CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | Last status change |

**Indexes:** `UNIQUE (paypal_order_id)`, `INDEX (user_id)`, `INDEX (status)`

---

### 4.7 `order_items`

Traces to: FR-NM-001 to FR-NM-003 (itemization within an order).

Added beyond the original sketch to correctly support bundles, which span multiple editions per order, and to keep `orders` free of repeating groups (3NF compliance — see Section 6).

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `BIGINT UNSIGNED` | PK, AUTO_INCREMENT | Unique line item identifier |
| `order_id` | `BIGINT UNSIGNED` | NOT NULL, FK → `orders.id` | Parent order |
| `edition_id` | `BIGINT UNSIGNED` | NULL, FK → `editions.id` | Set when `line_type = 'EDITION'` or `'ARCHIVE'` |
| `bundle_id` | `BIGINT UNSIGNED` | NULL, FK → `bundles.id` | Set when `line_type = 'BUNDLE'` |
| `line_type` | `ENUM('EDITION','ARCHIVE','BUNDLE')` | NOT NULL | What this line represents |
| `unit_price` | `DECIMAL(10,2)` | NOT NULL | Price at time of purchase (snapshot, independent of later price edits) |

**Indexes:** `INDEX (order_id)`, `INDEX (edition_id)`, `INDEX (bundle_id)`

**Constraint:** Exactly one of `edition_id` / `bundle_id` must be non-null, enforced via application logic (and optionally a `CHECK` constraint on MySQL 8.0.16+).

---

### 4.8 `transactions`

Traces to: FR-FM-001, FR-FM-002, FR-FM-003, Core Business Rule "PayPal Order Validation".

One-to-one with a `COMPLETED` order. Created only after successful PayPal capture (confirmed either by the client-side capture call or the redundant webhook).

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `BIGINT UNSIGNED` | PK, AUTO_INCREMENT | Unique transaction identifier |
| `order_id` | `BIGINT UNSIGNED` | UNIQUE, NOT NULL, FK → `orders.id` | Completed order this transaction settles |
| `gross_amount` | `DECIMAL(10,2)` | NOT NULL | Copied from `orders.gross_amount` at capture time |
| `commission_rate_snapshot` | `DECIMAL(5,2)` | NOT NULL | Rate (%) in effect at capture time, frozen from `commission_config` |
| `commission_amount` | `DECIMAL(10,2)` | NOT NULL | `gross_amount * commission_rate_snapshot / 100` |
| `net_amount` | `DECIMAL(10,2)` | NOT NULL | `gross_amount - commission_amount`; owed to publisher |
| `paypal_reference` | `VARCHAR(100)` | NOT NULL | PayPal capture/transaction ID, used for reconciliation |
| `confirmed_via` | `ENUM('CLIENT_CAPTURE','WEBHOOK')` | NOT NULL | Records which path confirmed payment (redundancy tracking) |
| `created_at` | `DATETIME` | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | Settlement time |

**Indexes:** `UNIQUE (order_id)`, `INDEX (paypal_reference)`, `INDEX (created_at)` — supports filterable financial reports by date (FR-FM-004).

---

### 4.9 `user_edition_access`

Traces to: FR-RE-001, FR-RE-005 (entitlement after purchase).

Many-to-many grant table. An access row is created per purchased edition once an order completes — including each individual edition inside a purchased bundle.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `BIGINT UNSIGNED` | PK, AUTO_INCREMENT | Unique grant identifier |
| `user_id` | `BIGINT UNSIGNED` | NOT NULL, FK → `users.id` | Customer granted access |
| `edition_id` | `BIGINT UNSIGNED` | NOT NULL, FK → `editions.id` | Edition the access applies to |
| `order_id` | `BIGINT UNSIGNED` | NOT NULL, FK → `orders.id` | Originating order (traceability) |
| `access_source` | `ENUM('SINGLE','BUNDLE','ARCHIVE')` | NOT NULL | How access was obtained |
| `granted_at` | `DATETIME` | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | Grant time |
| `revoked_at` | `DATETIME` | NULL | Set if a refund revokes access |

**Indexes:** `UNIQUE (user_id, edition_id)`, `INDEX (edition_id)` — uniqueness prevents duplicate grants if a customer somehow purchases overlapping bundle/single combinations for the same edition.

---

### 4.10 `page_tokens`

Traces to: FR-RE-003, NFR-SEC-002, Core Business Rule "Zero PDF Exposure".

Ephemeral table — rows are short-lived and may be purged by a periodic cleanup job once expired.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `BIGINT UNSIGNED` | PK, AUTO_INCREMENT | Unique token record identifier |
| `user_id` | `BIGINT UNSIGNED` | NOT NULL, FK → `users.id` | Token bound to this user (NFR-SEC-002) |
| `edition_id` | `BIGINT UNSIGNED` | NOT NULL, FK → `editions.id` | Token bound to this edition |
| `page_number` | `SMALLINT UNSIGNED` | NOT NULL | Token bound to this specific page |
| `token_hash` | `VARCHAR(255)` | NOT NULL, UNIQUE | HMAC-signed token, stored hashed (not reversible) |
| `issued_at` | `DATETIME` | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | Issue time |
| `expires_at` | `DATETIME` | NOT NULL | `issued_at + 60 seconds` (FR-RE-003) |
| `used` | `BOOLEAN` | NOT NULL, DEFAULT `FALSE` | Marked `TRUE` on first successful page retrieval — prevents replay |

**Indexes:** `UNIQUE (token_hash)`, `INDEX (expires_at)` — supports an expiry cleanup job, `INDEX (user_id, edition_id, page_number)`

---

### 4.11 `commission_config`

Traces to: FR-FM-003, Core Business Rule and Appendix C of the design spec (Commission Model).

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `BIGINT UNSIGNED` | PK, AUTO_INCREMENT | Unique config row identifier |
| `transaction_type` | `ENUM('SINGLE','BUNDLE','ARCHIVE')` | NOT NULL | Rate applies to this order type |
| `rate_percent` | `DECIMAL(5,2)` | NOT NULL, CHECK (`rate_percent >= 0 AND rate_percent <= 100`) | Commission percentage |
| `effective_from` | `DATETIME` | NOT NULL | When this rate takes effect |
| `set_by` | `BIGINT UNSIGNED` | NOT NULL, FK → `users.id` | Platform Admin who set the rate |
| `created_at` | `DATETIME` | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | Record creation time |

**Indexes:** `INDEX (transaction_type, effective_from)` — supports "current rate as of now" lookups by selecting the latest `effective_from <= NOW()` row per type.

**Design note:** Rather than a single mutable row per type, this table is append-only historical configuration. The application selects the most recent applicable row per `transaction_type` at checkout time, then snapshots it into `transactions.commission_rate_snapshot`. This preserves a full audit trail of rate changes over time, beyond what the original conceptual model implied.

---

### 4.12 `platform_config`

Traces to: FR-PA-001.

Generic key-value store for platform-wide tunables (archive age threshold, maximum bundle discount, etc.).

| Column | Type | Constraints | Description |
|---|---|---|---|
| `config_key` | `VARCHAR(100)` | PK | Setting name, e.g. `ARCHIVE_AGE_DAYS`, `MAX_BUNDLE_DISCOUNT_PERCENT` |
| `config_value` | `VARCHAR(500)` | NOT NULL | Setting value, stored as text and cast at application layer |
| `description` | `VARCHAR(255)` | NULL | Human-readable explanation of the setting |
| `updated_by` | `BIGINT UNSIGNED` | NOT NULL, FK → `users.id` | Platform Admin who last changed it |
| `updated_at` | `DATETIME` | NOT NULL, DEFAULT `CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | Last update time |

---

### 4.13 `audit_log`

Traces to: FR-PA-003, Section 15.4 of the Design Specification Document.

Append-only. No update or delete operations are permitted at the application layer.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `BIGINT UNSIGNED` | PK, AUTO_INCREMENT | Unique log entry identifier |
| `actor_id` | `BIGINT UNSIGNED` | NOT NULL, FK → `users.id` | User who performed the action |
| `action` | `VARCHAR(100)` | NOT NULL | e.g. `PUBLISHER_APPROVED`, `EDITION_REMOVED`, `COMMISSION_UPDATED` |
| `target_type` | `VARCHAR(50)` | NOT NULL | Entity type affected, e.g. `publisher`, `edition` |
| `target_id` | `BIGINT UNSIGNED` | NOT NULL | Primary key of the affected entity |
| `ip_address` | `VARCHAR(45)` | NOT NULL | Source IP (IPv4 or IPv6) |
| `payload` | `JSON` | NULL | Full request payload, for forensic reconstruction |
| `created_at` | `DATETIME` | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | Action timestamp |

**Indexes:** `INDEX (actor_id)`, `INDEX (target_type, target_id)`, `INDEX (created_at)`

---

### 4.14 `notifications`

Traces to: FR-NO-001, FR-NO-002.

Tracks dispatch of email and WebSocket notifications for support/debugging purposes; not strictly required by the conceptual model but necessary to fulfill auditable delivery of registration, receipt, and status messages.

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `BIGINT UNSIGNED` | PK, AUTO_INCREMENT | Unique notification identifier |
| `user_id` | `BIGINT UNSIGNED` | NOT NULL, FK → `users.id` | Recipient |
| `channel` | `ENUM('EMAIL','WEBSOCKET')` | NOT NULL | Delivery channel (FR-NO-001, FR-NO-002) |
| `event_type` | `VARCHAR(100)` | NOT NULL | e.g. `REGISTRATION`, `PURCHASE_RECEIPT`, `PDF_CONVERSION_SUCCESS` |
| `status` | `ENUM('QUEUED','SENT','FAILED')` | NOT NULL, DEFAULT `'QUEUED'` | Delivery state |
| `sent_at` | `DATETIME` | NULL | Successful delivery time |
| `created_at` | `DATETIME` | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | Notification creation time |

**Indexes:** `INDEX (user_id)`, `INDEX (event_type)`, `INDEX (status)`

---

### 4.15 `refresh_tokens` / `login_attempts`

Traces to: FR-AC-004, FR-AC-005.

Two small supporting tables to fulfil multi-device session checks and rate-limited authentication, which the conceptual model referenced but did not break out separately.

**`refresh_tokens`**

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `BIGINT UNSIGNED` | PK, AUTO_INCREMENT | Unique session record |
| `user_id` | `BIGINT UNSIGNED` | NOT NULL, FK → `users.id` | Token owner |
| `device_fingerprint` | `VARCHAR(255)` | NOT NULL | Identifies the originating device/browser |
| `issued_at` | `DATETIME` | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | Session start |
| `expires_at` | `DATETIME` | NOT NULL | JWT/session expiry |
| `revoked` | `BOOLEAN` | NOT NULL, DEFAULT `FALSE` | Manually invalidated (e.g. logout, suspicious activity) |

**Indexes:** `INDEX (user_id, revoked)`

**`login_attempts`**

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `BIGINT UNSIGNED` | PK, AUTO_INCREMENT | Unique attempt record |
| `email` | `VARCHAR(255)` | NOT NULL | Attempted login identifier |
| `ip_address` | `VARCHAR(45)` | NOT NULL | Source IP |
| `success` | `BOOLEAN` | NOT NULL | Outcome |
| `attempted_at` | `DATETIME` | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | Attempt time |

**Indexes:** `INDEX (email, attempted_at)`, `INDEX (ip_address, attempted_at)` — supports rate-limiting queries (FR-AC-005).

---

## 5. Relationship Summary

| Parent | Child | Cardinality | Notes |
|---|---|---|---|
| `users` | `publishers` | 1 : 1 | Only for `role = 'PUBLISHER'` |
| `publishers` | `editions` | 1 : N | A publisher manages multiple titles (FR-PP-003) |
| `publishers` | `bundles` | 1 : N | A publisher may create multiple bundles |
| `bundles` | `editions` | N : M | Via `bundle_editions` join table |
| `users` | `orders` | 1 : N | A customer may place many orders |
| `orders` | `order_items` | 1 : N | An order may itemize one or many editions/bundles |
| `orders` | `transactions` | 1 : 1 | Created only on completed payment capture |
| `editions` | `order_items` | 1 : N | An edition may appear in many order line items |
| `bundles` | `order_items` | 1 : N | A bundle may appear in many order line items |
| `users` | `user_edition_access` | 1 : N | Access grants per customer |
| `editions` | `user_edition_access` | 1 : N | Access grants per edition |
| `users` | `page_tokens` | 1 : N | Tokens issued per reading session |
| `editions` | `page_tokens` | 1 : N | Tokens scoped per edition/page |
| `users` | `commission_config` | 1 : N | `set_by` references the admin who configured the rate |
| `users` | `audit_log` | 1 : N | `actor_id` references the user who performed the action |
| `users` | `notifications` | 1 : N | Notifications sent per user |
| `users` | `refresh_tokens` | 1 : N | Multi-device sessions per user |

---

## 6. Normalization Notes

The schema is normalized to **Third Normal Form (3NF)**:

- **1NF**: All columns hold atomic values; no repeating groups (e.g., bundle contents are decomposed into `bundle_editions`, and order contents into `order_items`, rather than stored as delimited lists).
- **2NF**: All non-key attributes depend on the whole primary key. Composite-keyed tables (`bundle_editions`) carry no non-key attributes that would violate this.
- **3NF**: No transitive dependencies — e.g., `transactions.net_amount` is derived from `gross_amount` and `commission_amount` but is stored, not transitively inferred from unrelated tables.

**Deliberate denormalization (with justification):**

| Field | Table | Why it's denormalized | Justification |
|---|---|---|---|
| `commission_rate_snapshot`, `commission_amount`, `net_amount` | `transactions` | Duplicates information derivable from `commission_config` at the time of sale | FR-FM-002 explicitly requires the *rate at time of transaction* to remain fixed even if `commission_config` changes later. This is a financial audit requirement, not an oversight. |
| `unit_price` | `order_items` | Duplicates `editions.price` / `bundles.total_price` | Prices may change after purchase; the line item must reflect what the customer actually paid. |
| `total_price` | `bundles` | Cached, derivable from constituent edition prices and discount | Cached for catalog display performance (NFR-PERF-001); recalculated authoritatively at checkout to avoid staleness affecting actual charges. |

---

## 7. Indexing Strategy

| Concern | Index | Supports |
|---|---|---|
| Catalog search/filtering | `editions (language, publication_date)`, `FULLTEXT (title)`, `editions (status)` | FR-CM-001 (filter by language, date, publisher, title) |
| Login | `users (email)` UNIQUE | Fast credential lookup |
| RBAC checks | `users (role, status)` | Frequent filtering during authorization |
| Reader token validation | `page_tokens (token_hash)` UNIQUE | NFR-PERF-001 — sub-1.5s reader page load |
| Token cleanup job | `page_tokens (expires_at)` | Periodic purge of expired tokens |
| Financial reporting | `transactions (created_at)`, `transactions (paypal_reference)` | FR-FM-004 — filterable, exportable reports |
| Order lookup by customer | `orders (user_id)`, `orders (status)` | Customer "my purchases" view |
| Access verification before serving a page | `user_edition_access (user_id, edition_id)` UNIQUE | Fast entitlement check on every reader request |
| Audit trail queries | `audit_log (actor_id)`, `audit_log (target_type, target_id)`, `audit_log (created_at)` | FR-PA-003 |
| Auth rate-limiting | `login_attempts (email, attempted_at)`, `login_attempts (ip_address, attempted_at)` | FR-AC-005 |

---

## 8. Data Integrity Rules

1. **Referential integrity**: All foreign keys use `ON DELETE RESTRICT` by default to prevent orphaning financial or content records. Exceptions:
   - `audit_log.actor_id` uses `ON DELETE RESTRICT` — audit history must never be erased by account deletion.
   - `page_tokens` rows use `ON DELETE CASCADE` from both `users` and `editions` — tokens are ephemeral and carry no long-term audit value.
2. **Status transition enforcement** (application-layer, not DB-level): `editions.status` must follow the state machine `PROCESSING → ACTIVE → ARCHIVED`, with `ACTIVE → SUSPENDED` and `PROCESSING → FAILED` as the only valid side-exits, matching the lifecycle diagram in the Design Specification Document.
3. **Order/transaction consistency**: A `transactions` row may only be created when its parent `orders.status = 'COMPLETED'`. A unique constraint on `transactions.order_id` enforces the 1:1 relationship.
4. **Single Content Model enforcement**: Application logic must guarantee that `editions.status = 'ACTIVE'` editions are only ever served through `/api/reader/page` (image delivery) and never through a download endpoint; `ARCHIVED` editions reverse this exclusively, per FR-RE-001 and FR-RE-005.
5. **Commission rate immutability post-sale**: Once written, `transactions.commission_rate_snapshot` must never be updated — enforced by omitting `UPDATE` permissions on that column at the application/service layer.
6. **Bundle discount ceiling**: `bundles.discount_percent` must not exceed the value stored in `platform_config` under `MAX_BUNDLE_DISCOUNT_PERCENT` (FR-PA-001); validated at bundle creation/edit time.
7. **Token single-use enforcement**: `page_tokens.used` is set `TRUE` transactionally on first successful page fetch; subsequent requests with the same `token_hash` are rejected even if not yet expired (NFR-SEC-002).

---

## 9. Storage and Retention

| Data | Location | Retention Policy |
|---|---|---|
| Relational data (all tables above) | MySQL 8.x | Backed up every 24 hours (NFR-AVAIL-001) |
| Source PDFs (`editions.pdf_path`) | Local filesystem, `/editions/{id}/source.pdf` | Retained indefinitely while edition is `ACTIVE`/`ARCHIVED`; deleted on admin removal (FR-PA-002) |
| Rendered page images (`editions.pages_dir`) | Local filesystem, `/editions/{id}/pages/page_NNN.webp` | Retained while `status = 'ACTIVE'`; may be purged after archive transition since archive customers receive the PDF directly |
| `page_tokens` | MySQL | Purged by scheduled cleanup job once `expires_at` has passed (recommended: hourly job, or `ON DELETE` via TTL-style batch delete) |
| `audit_log` | MySQL | Retained indefinitely; no delete path exposed at the application layer |

---

## 10. Appendix — DDL Reference

The following abbreviated DDL illustrates the core tables. Full DDL (including all constraints, charset/collation settings, and foreign key cascade rules) should be maintained in version-controlled migration scripts (e.g., Flyway or Liquibase) rather than this document.

```sql
CREATE TABLE users (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    role ENUM('CUSTOMER','PUBLISHER','PLATFORM_ADMIN','FINANCE_ADMIN','SYSTEM_ADMIN') NOT NULL,
    status ENUM('UNVERIFIED','ACTIVE','SUSPENDED') NOT NULL DEFAULT 'UNVERIFIED',
    email_verified_at DATETIME NULL,
    failed_login_count SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    locked_until DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_role_status (role, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE publishers (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL UNIQUE,
    company_name VARCHAR(200) NOT NULL,
    registration_number VARCHAR(100) NOT NULL,
    official_email VARCHAR(255) NOT NULL,
    bank_account_name VARCHAR(150) NOT NULL,
    bank_account_number_enc VARCHAR(255) NOT NULL,
    bank_name VARCHAR(150) NOT NULL,
    bank_branch VARCHAR(150) NULL,
    status ENUM('PENDING','APPROVED','REJECTED','SUSPENDED') NOT NULL DEFAULT 'PENDING',
    approved_by BIGINT UNSIGNED NULL,
    approved_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_pub_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_pub_approver FOREIGN KEY (approved_by) REFERENCES users(id),
    INDEX idx_pub_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE editions (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    publisher_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(200) NOT NULL,
    publication_date DATE NOT NULL,
    language VARCHAR(50) NOT NULL,
    price DECIMAL(10,2) NOT NULL CHECK (price >= 0),
    archive_price DECIMAL(10,2) NULL CHECK (archive_price >= 0),
    page_count SMALLINT UNSIGNED NULL,
    status ENUM('PROCESSING','ACTIVE','ARCHIVED','SUSPENDED','FAILED') NOT NULL DEFAULT 'PROCESSING',
    pdf_path VARCHAR(500) NOT NULL,
    pages_dir VARCHAR(500) NULL,
    archived_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_edition_publisher FOREIGN KEY (publisher_id) REFERENCES publishers(id),
    INDEX idx_edition_publisher (publisher_id),
    INDEX idx_edition_status (status),
    INDEX idx_edition_lang_date (language, publication_date),
    FULLTEXT idx_edition_title (title)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bundles (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    publisher_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(150) NOT NULL,
    discount_percent DECIMAL(5,2) NOT NULL CHECK (discount_percent BETWEEN 0 AND 100),
    total_price DECIMAL(10,2) NOT NULL,
    status ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_bundle_publisher FOREIGN KEY (publisher_id) REFERENCES publishers(id),
    INDEX idx_bundle_publisher (publisher_id),
    INDEX idx_bundle_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bundle_editions (
    bundle_id BIGINT UNSIGNED NOT NULL,
    edition_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (bundle_id, edition_id),
    CONSTRAINT fk_be_bundle FOREIGN KEY (bundle_id) REFERENCES bundles(id),
    CONSTRAINT fk_be_edition FOREIGN KEY (edition_id) REFERENCES editions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE orders (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    order_type ENUM('SINGLE','BUNDLE','ARCHIVE') NOT NULL,
    gross_amount DECIMAL(10,2) NOT NULL CHECK (gross_amount >= 0),
    discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    paypal_order_id VARCHAR(100) NULL UNIQUE,
    status ENUM('PENDING','CAPTURE_PENDING','COMPLETED','CANCELLED','FAILED','REFUNDED') NOT NULL DEFAULT 'PENDING',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_order_user (user_id),
    INDEX idx_order_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE order_items (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT UNSIGNED NOT NULL,
    edition_id BIGINT UNSIGNED NULL,
    bundle_id BIGINT UNSIGNED NULL,
    line_type ENUM('EDITION','ARCHIVE','BUNDLE') NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    CONSTRAINT fk_item_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_item_edition FOREIGN KEY (edition_id) REFERENCES editions(id),
    CONSTRAINT fk_item_bundle FOREIGN KEY (bundle_id) REFERENCES bundles(id),
    INDEX idx_item_order (order_id),
    INDEX idx_item_edition (edition_id),
    INDEX idx_item_bundle (bundle_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE transactions (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT UNSIGNED NOT NULL UNIQUE,
    gross_amount DECIMAL(10,2) NOT NULL,
    commission_rate_snapshot DECIMAL(5,2) NOT NULL,
    commission_amount DECIMAL(10,2) NOT NULL,
    net_amount DECIMAL(10,2) NOT NULL,
    paypal_reference VARCHAR(100) NOT NULL,
    confirmed_via ENUM('CLIENT_CAPTURE','WEBHOOK') NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_txn_order FOREIGN KEY (order_id) REFERENCES orders(id),
    INDEX idx_txn_paypal_ref (paypal_reference),
    INDEX idx_txn_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_edition_access (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    edition_id BIGINT UNSIGNED NOT NULL,
    order_id BIGINT UNSIGNED NOT NULL,
    access_source ENUM('SINGLE','BUNDLE','ARCHIVE') NOT NULL,
    granted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at DATETIME NULL,
    UNIQUE KEY uq_user_edition (user_id, edition_id),
    CONSTRAINT fk_access_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_access_edition FOREIGN KEY (edition_id) REFERENCES editions(id),
    CONSTRAINT fk_access_order FOREIGN KEY (order_id) REFERENCES orders(id),
    INDEX idx_access_edition (edition_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE page_tokens (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    edition_id BIGINT UNSIGNED NOT NULL,
    page_number SMALLINT UNSIGNED NOT NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    issued_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at DATETIME NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_token_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_token_edition FOREIGN KEY (edition_id) REFERENCES editions(id) ON DELETE CASCADE,
    INDEX idx_token_expiry (expires_at),
    INDEX idx_token_lookup (user_id, edition_id, page_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE commission_config (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    transaction_type ENUM('SINGLE','BUNDLE','ARCHIVE') NOT NULL,
    rate_percent DECIMAL(5,2) NOT NULL CHECK (rate_percent BETWEEN 0 AND 100),
    effective_from DATETIME NOT NULL,
    set_by BIGINT UNSIGNED NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_commission_setter FOREIGN KEY (set_by) REFERENCES users(id),
    INDEX idx_commission_type_date (transaction_type, effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE platform_config (
    config_key VARCHAR(100) PRIMARY KEY,
    config_value VARCHAR(500) NOT NULL,
    description VARCHAR(255) NULL,
    updated_by BIGINT UNSIGNED NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_config_updater FOREIGN KEY (updated_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE audit_log (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    actor_id BIGINT UNSIGNED NOT NULL,
    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id BIGINT UNSIGNED NOT NULL,
    ip_address VARCHAR(45) NOT NULL,
    payload JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_id) REFERENCES users(id),
    INDEX idx_audit_actor (actor_id),
    INDEX idx_audit_target (target_type, target_id),
    INDEX idx_audit_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE notifications (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    channel ENUM('EMAIL','WEBSOCKET') NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    status ENUM('QUEUED','SENT','FAILED') NOT NULL DEFAULT 'QUEUED',
    sent_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_notif_user (user_id),
    INDEX idx_notif_event (event_type),
    INDEX idx_notif_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE refresh_tokens (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    device_fingerprint VARCHAR(255) NOT NULL,
    issued_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at DATETIME NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_refresh_user_revoked (user_id, revoked)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE login_attempts (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    ip_address VARCHAR(45) NOT NULL,
    success BOOLEAN NOT NULL,
    attempted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_login_email_time (email, attempted_at),
    INDEX idx_login_ip_time (ip_address, attempted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

*© 2026 PaperFlow. All rights reserved. — End of Document —*