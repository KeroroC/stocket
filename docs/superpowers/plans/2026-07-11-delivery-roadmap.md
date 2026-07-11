# Family Assets Delivery Roadmap

> **For agentic workers:** Each phase requires its own detailed plan before implementation. Implement phases in order; every phase must leave the repository deployable and testable.

**Goal:** Deliver the approved Family Assets design through small, independently verifiable increments instead of one oversized implementation plan.

**Architecture:** A single repository contains a Java 21/Spring Boot 4 modular monolith, a Vue 3 PWA, and Docker Compose deployment resources. PostgreSQL is the system of record; module boundaries are enforced with Spring Modulith, and GraalVM Native Image compatibility is verified from the first phase.

**Tech Stack:** Java 21 LTS, Spring Boot 4.0.3, Spring Modulith 2.0.5, Maven, PostgreSQL, Flyway, Vue 3, TypeScript, Vite, Element Plus, Docker Compose, GraalVM Native Image

---

## Phase 1: Engineering Foundation

Create the backend and frontend project skeletons, establish module boundaries, add PostgreSQL migrations, health/version endpoints, Docker Compose, CI, and JVM/native verification. The result is an empty but production-shaped application that starts reliably in development and as a native executable.

Detailed plan: `docs/superpowers/plans/2026-07-11-foundation-native-baseline.md`

## Phase 2: Identity and Household

Implement one-time household initialization, server-side sessions, CSRF protection, login/logout, account management, invitation acceptance, role enforcement, password changes, administrator reset, local maintenance recovery, and identity audit events.

Acceptance slice: an administrator initializes the system, creates or invites members, and all three roles receive correct API authorization.

## Phase 3: Catalog and Locations

Implement tree-shaped categories and locations, category attribute schemas, item definitions, tags, barcodes, location QR codes, archive rules, and catalog search projections.

Acceptance slice: a member creates a category template, location hierarchy, and reusable item definition, then retrieves it by name or exact barcode.

## Phase 4: Inventory Ledger

Implement batch and asset inventory entries, expiration calculation, inbound, consumption, transfer, partial batch split, adjustment, loss, retirement, immutable movements, transactional snapshots, row locking, optimistic versions, idempotency, and integrity reconciliation.

Acceptance slice: concurrent operations never create negative inventory, retries never duplicate a movement, and movement sums match inventory snapshots.

## Phase 5: Reminder and Notification Pipeline

Implement expiration and low-stock rules, reminder lifecycle, transactional Outbox processing, in-app notifications, Web Push, SMTP email, generic Webhook delivery, deduplication, exponential retries, encrypted channel secrets, and failed-delivery administration.

Acceptance slice: inventory changes create the correct reminders; a failing external channel does not roll back inventory and can be retried safely.

## Phase 6: Mobile-First PWA Workflows

Implement the task-oriented home page, global search, item/location browsing, four-step inbound wizard, barcode and QR scanning, inventory actions, reminder views, IndexedDB drafts, seven-day expiry, session-specific cleanup, offline application shell, and responsive desktop management screens.

Acceptance slice: an existing item can be received on a phone in under 30 seconds, a lost connection preserves the draft, and writes remain blocked until online.

## Phase 7: Attachments, Export, and Audit

Implement authenticated attachment upload/download, content-based file validation, invoices and warranty documents, CSV export, audit search, administrative diagnostics, and request trace correlation.

Acceptance slice: files cannot be accessed without authorization, exports match filtered search results, and critical changes are traceable to a member and request ID.

## Phase 8: Operations and Release Hardening

Complete HTTPS gateway configuration, secret handling, scheduled PostgreSQL and attachment backups, retention, restore verification, observability, rate limits, image scanning, AMD64/ARM64 native releases, checksums, upgrade documentation, and full acceptance tests.

Acceptance slice: a release restores into a clean temporary environment, passes JVM and native smoke tests on both Linux architectures, and produces documented recovery evidence.

## Planning Rule

Before starting a phase, write `docs/superpowers/plans/YYYY-MM-DD-<phase>.md` with exact files, failing tests, commands, expected failures, minimal implementations, verification, and commit boundaries. Do not pull later-phase features into an earlier phase unless the approved design requires them as a prerequisite.
