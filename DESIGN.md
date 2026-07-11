# Personal Finance App — Refined Design Document

**Status:** Draft v1 — refined from handwritten notes (2026-07-11)
**Owner:** Alexandru
**Decisions locked:** microservices architecture from day one · Google OAuth + email/password auth · Romania-focused (RON) · quests & challenges in v1 · bank linking designed-for but deferred to v2 · **shadcn/ui as the frontend component library (requirement, not preference)**

---

## 1. Vision & Objectives

A personal finance web app that helps a user track expenses, understand their spending through reports and dashboards, and improve their habits through tailored goals, quests, and challenges.

| # | Objective | V1? |
|---|-----------|-----|
| O1 | Track expenses daily / monthly / yearly | ✅ |
| O2 | Reports & dashboard: graphs, projections, bar/pie charts | ✅ |
| O3 | Quests & challenges for daily/monthly spending, tailored to the user (income, mandatory expenses) | ✅ |
| O4 | Bank account linking (expense import) + investment tracking | ⏳ v2 — schema/architecture leave room, no implementation |
| O5 | Room to grow (more objectives later) | — |

---

## 2. Functional Requirements

Prioritized MoSCoW-style. Each has acceptance criteria so "done" is testable.

### Auth & Account

- **FR-1 (Must)** — User can register, log in, and log out.
  - Email + password (Argon2id-hashed) **and** Google OAuth 2.0 (SSO).
  - JWT access token (short-lived, ~15 min) + refresh token (rotating, HttpOnly cookie).
  - AC: a user can sign up with email, sign in with Google using the same email and get the same account (account linking by verified email), and log out invalidating the refresh token.

### Money In / Profile

- **FR-2 (Must)** — User can enter income, savings, and goals in a profile/"info sheet".
  - Multiple income sources, each with amount + recurrence (monthly / yearly / one-off).
  - Savings entries (named pots with balances).
  - AC: entering income and savings immediately updates net worth and quest tailoring inputs.

- **FR-8 (Must)** — Net worth displayed on the main dashboard.
  - Net worth = sum of savings balances (v1 definition; investments/bank balances join in v2).
  - AC: visible on dashboard without navigation; updates when savings are edited.

- **FR-10 (Should)** — Built-in Romanian net ↔ gross salary calculator.
  - Implements CAS (25%), CASS (10%), income tax (10%) with personal deduction; rates stored as **versioned configuration**, not hard-coded, so law changes are a config update.
  - AC: given a gross salary in RON, shows net (and reverse), with a breakdown of each contribution.

### Expenses

- **FR-5 (Must)** — User-defined categories and subcategories.
  - One level of nesting (category → subcategory) via self-referencing table.
  - Categories can be flagged **mandatory** (rent, utilities) — feeds quest tailoring (O3).
  - Sensible defaults seeded at registration; user can rename/delete/add.
  - AC: user can create "Food → Groceries", assign expenses to it, and mark "Rent" mandatory.

- **FR-2b (Must)** — User can record expenses: amount (RON), category/subcategory, date, note.
  - AC: adding an expense reflects in dashboard totals within one refresh.

- **FR-9 (Must)** — User can edit and delete expenses from their lists.
  - AC: list view with inline edit/delete; deleting recalculates affected reports/goals/quests.

### Reports & Visualization

- **FR-3 (Must)** — User can generate, save, and revisit reports.
  - A report = named set of filters (date range, categories) + computed aggregates.
  - AC: user creates "June 2026 food spending", reopens it later, sees the same definition re-evaluated.

- **FR-6 (Must)** — Dashboard visualizations: pie charts (by category), bar/line charts (over time), projections (simple linear projection of month-end spend in v1).
  - AC: dashboard renders spending by category and by time with real user data.

- **FR-4 (Must)** — Reports are viewable only inside the app, with explicit export.
  - Export formats: **CSV** (must) and **PDF** (should).
  - AC: no public/shareable report URLs; export downloads a file.

### Goals, Calendar, Quests

- **FR-7 (Must)** — Calendar view showing goal fulfillment daily / monthly / yearly.
  - Goals: spending limits (per category or overall) or saving targets, per period.
  - AC: calendar shows each day/month colored by met / not met / no data.

- **FR-11 (Must, from O3)** — Quests & challenges tailored to the user.
  - Quest = time-boxed challenge generated from templates + the user's income, mandatory expenses, and history (e.g., "Spend under 200 RON on eating out this week", "No-spend day ×3 this month").
  - Progress auto-evaluated from expense events; user gets a notification on completion/failure.
  - AC: a user with income and 2 weeks of expenses gets ≥3 relevant quest suggestions; accepting one tracks progress automatically.

### Notifications

- **FR-12 (Should)** — In-app notifications delivered live over WebSocket (quest completed, goal at risk, monthly report ready), with a notification center (read/unread).
  - AC: completing a quest pops a notification without page reload.

### Deferred to v2

- Bank account linking (PSD2/open-banking aggregator, e.g., GoCardless/Tink/Salt Edge) — **design constraint on v1:** expenses table carries a `source` field (`MANUAL` now, `BANK_IMPORT` later) and an optional `external_id` for idempotent imports.
- Investment tracking.
- Browser push / email notifications.

---

## 3. Non-Functional Requirements

Made measurable so they can be verified:

| ID | Requirement | Target / Verification |
|----|-------------|----------------------|
| NFR-1 | Responsive & dynamic UI | Usable at 360 px – 1440 px; Lighthouse ≥ 90 performance on dashboard |
| NFR-2 | Low latency | p95 API read < 300 ms, p95 write < 500 ms under normal load |
| NFR-3 | High availability | 99.5 % monthly target; health checks + container auto-restart; no single stateful point of failure beyond Postgres |
| NFR-4 | Sensitive data protection | TLS everywhere; Argon2id password hashing; OAuth tokens & refresh tokens stored hashed; DB encrypted at rest (volume-level); no financial data in URLs or logs |
| NFR-5 | Modular, maintainable code | Service boundaries enforced (no shared DB between services); per-service tests; CI pipeline gates merge on build+tests |
| NFR-6 | Clean, appealing visuals | Single design system — **shadcn/ui components (required) on Tailwind CSS**; consistent chart palette |
| NFR-7 | Widgets & notifications | Dashboard is a grid of user-arrangeable widgets (net worth, top categories, quest progress, calendar snippet); live notifications (FR-12) |
| NFR-8 | Scale | Sustain 1,000 concurrent users; verified with a k6/Gatling load test against docker-compose stack |
| NFR-9 | Portability | Everything containerized (Docker); one `docker-compose up` brings the full stack; 12-factor config via env vars |

---

## 4. Architecture (Microservices)

```
                        ┌────────────────────────┐
   React SPA  ──HTTPS──▶│  API Gateway            │
   (static hosting)     │  Spring Cloud Gateway   │
        │               │  JWT validation, CORS,  │
        │  WebSocket    │  rate limiting, routing │
        │               └───┬────┬────┬────┬─────┘
        │                   │    │    │    │
        ▼                   ▼    ▼    ▼    ▼
  ┌───────────┐  ┌────────┐ ┌─────────┐ ┌────────┐ ┌─────────┐
  │Notification│  │  User  │ │ Expense │ │ Report │ │  Quest  │
  │  Service   │  │Service │ │ Service │ │Service │ │ Service │
  │ (WebSocket)│  │(auth,  │ │(expenses│ │(aggr., │ │(goals,  │
  │            │  │income, │ │ categ.) │ │ export)│ │ quests, │
  │            │  │savings,│ │         │ │        │ │calendar)│
  │            │  │salary  │ │         │ │        │ │         │
  └─────┬──────┘  │ calc)  │ └────┬────┘ └───┬────┘ └────┬────┘
        │         └───┬────┘      │          │           │
        │             │           │          │           │
        │         ┌───▼───┐  ┌────▼───┐ ┌────▼───┐  ┌────▼───┐
        │         │users_db│  │expenses│ │reports │  │quests  │
        │         └────────┘  │  _db   │ │  _db   │  │  _db   │
        │                     └────────┘ └────────┘  └────────┘
        │                    (one Postgres instance, DB-per-service)
        │
        └────────◀── RabbitMQ (events) ──── all services publish/consume
```

### Services

| Service | Responsibilities | Notes |
|---------|-----------------|-------|
| **API Gateway** | Single entry point, route to services, validate JWTs, CORS, rate limiting | Spring Cloud Gateway. No business logic. |
| **User Service** | Registration, login, Google OAuth, JWT issuing, refresh tokens, profile, income sources, savings, net worth, salary calculator | Owns identity. Salary calc is stateless logic + versioned tax config. |
| **Expense Service** | Expense CRUD, categories/subcategories | Publishes `expense.created/updated/deleted` events. |
| **Report Service** | Aggregations, saved reports, dashboard data endpoints, CSV/PDF export | Maintains its own read-optimized copy of expense data fed by events (no cross-service DB reads). |
| **Quest Service** | Goals, calendar fulfillment, quest templates, quest generation & progress | Consumes expense events; also keeps an event-fed local projection. Publishes `quest.completed` etc. |
| **Notification Service** | WebSocket sessions (STOMP), notification persistence, read/unread | Consumes events from all services. |

### Cross-cutting decisions

- **Communication:** synchronous REST (JSON) through the gateway for client calls; **asynchronous events via RabbitMQ** between services. Avoid service-to-service REST chains where an event projection works — keeps services decoupled and latency low.
- **Data ownership:** database-per-service (separate logical databases on one PostgreSQL instance for dev/v1; splittable to separate instances later). **No service ever queries another service's DB.**
- **Service discovery:** docker-compose DNS (service names). No Eureka in v1 — it adds ops weight without payoff at this scale; the gateway routes to known service names.
- **Auth flow:** User Service issues JWTs (RS256, public key shared with gateway/services); gateway rejects invalid tokens; services trust the gateway-forwarded identity claims but still verify signature.
- **IDs:** UUIDv7 everywhere (time-ordered, index-friendly).
- **Money:** stored as `BIGINT` in **bani** (RON cents). Never floating point. Currency column present (`char(3)`, default `'RON'`) so multi-currency is a v2 migration, not a rewrite.
- **Consistency note (accepted trade-off):** report/quest projections are eventually consistent (sub-second in practice via RabbitMQ). The expense list itself is always read from Expense Service, so user edits always look immediate.

---

## 5. Tech Stack

| Layer | Choice | Rationale |
|-------|--------|-----------|
| Backend | Java 21 + Spring Boot 3.x | Your stack; LTS; virtual threads help concurrency targets |
| Gateway | Spring Cloud Gateway | Native Spring ecosystem fit |
| Auth | Spring Security + spring-boot-starter-oauth2-client (Google), JJWT/Nimbus for tokens | |
| DB | PostgreSQL 16 | Relational fits structured data (your call, confirmed) |
| Migrations | Flyway (per service) | Versioned schema evolution |
| Messaging | RabbitMQ | Simpler than Kafka at this scale; fine for 1k users |
| Cache (optional, when needed) | Redis | Dashboard aggregate caching if p95 demands it |
| Frontend | React 18 + TypeScript + Vite | Confirmed direction |
| UI kit | Tailwind CSS + shadcn/ui | Clean, bold, customizable — matches "simple but appealing" |
| Charts | Recharts | Pie/bar/line + composability, React-native API |
| Data fetching | TanStack Query | Cache/invalidations map well to REST |
| WebSocket client | STOMP over WebSocket (@stomp/stompjs) | Pairs with Spring's STOMP support |
| Containerization | Docker + docker-compose (dev & v1 prod) | NFR-9 |
| CI | GitHub Actions | Build, test, image publish |
| Load testing | k6 | Verify NFR-2/NFR-8 |

---

## 6. Database Design

Corrections vs. the first draft ER: no list-columns on `USER` (relations come from FKs), income is a table not a single integer, reports don't store expense-ID strings (they store filter definitions), and categories/goals/savings/quests get proper tables. All PKs `UUID`, all money `BIGINT` (bani), all timestamps `timestamptz`.

### users_db (User Service)

```sql
users (
  id              uuid PK,
  email           citext UNIQUE NOT NULL,
  password_hash   text NULL,              -- NULL for OAuth-only accounts
  display_name    text NOT NULL,
  avatar_url      text NULL,
  base_currency   char(3) NOT NULL DEFAULT 'RON',
  created_at      timestamptz NOT NULL,
  updated_at      timestamptz NOT NULL
)

auth_identities (                          -- SSO account linking
  id            uuid PK,
  user_id       uuid FK -> users,
  provider      text NOT NULL,             -- 'GOOGLE' | 'PASSWORD'
  provider_uid  text NOT NULL,             -- Google sub claim
  UNIQUE (provider, provider_uid)
)

refresh_tokens (
  id          uuid PK,
  user_id     uuid FK -> users,
  token_hash  text NOT NULL,
  expires_at  timestamptz NOT NULL,
  revoked_at  timestamptz NULL
)

income_sources (
  id          uuid PK,
  user_id     uuid FK -> users,
  name        text NOT NULL,               -- 'Salary', 'Freelance'
  amount      bigint NOT NULL,             -- bani
  recurrence  text NOT NULL,               -- 'MONTHLY' | 'YEARLY' | 'ONE_OFF'
  start_date  date NOT NULL,
  end_date    date NULL
)

savings_accounts (                          -- powers net worth (FR-8)
  id          uuid PK,
  user_id     uuid FK -> users,
  name        text NOT NULL,               -- 'Emergency fund'
  balance     bigint NOT NULL,
  updated_at  timestamptz NOT NULL
)

tax_config (                                -- versioned salary-calc rules (FR-10)
  id          uuid PK,
  valid_from  date NOT NULL,
  rules       jsonb NOT NULL               -- {cas: 0.25, cass: 0.10, income_tax: 0.10, deduction_table: ...}
)
```

### expenses_db (Expense Service)

```sql
categories (
  id            uuid PK,
  user_id       uuid NOT NULL,             -- owner (no cross-DB FK; enforced in service)
  parent_id     uuid NULL FK -> categories, -- NULL = top-level, set = subcategory
  name          text NOT NULL,
  icon          text NULL,
  color         text NULL,
  is_mandatory  boolean NOT NULL DEFAULT false,  -- feeds quest tailoring
  UNIQUE (user_id, parent_id, name)
)

expenses (
  id           uuid PK,
  user_id      uuid NOT NULL,
  category_id  uuid FK -> categories,
  amount       bigint NOT NULL,            -- bani, > 0
  currency     char(3) NOT NULL DEFAULT 'RON',
  note         text NULL,
  expense_date date NOT NULL,
  source       text NOT NULL DEFAULT 'MANUAL',  -- 'MANUAL' | 'BANK_IMPORT' (v2)
  external_id  text NULL,                  -- idempotency key for v2 imports
  created_at   timestamptz NOT NULL,
  updated_at   timestamptz NOT NULL
)
-- indexes: (user_id, expense_date), (user_id, category_id)
```

### reports_db (Report Service)

```sql
expense_projection (                        -- local read model, fed by events
  expense_id   uuid PK,
  user_id      uuid NOT NULL,
  category_id  uuid NOT NULL,
  category_path text NOT NULL,             -- denormalized 'Food > Groceries'
  is_mandatory boolean NOT NULL,
  amount       bigint NOT NULL,
  expense_date date NOT NULL
)

reports (
  id           uuid PK,
  user_id      uuid NOT NULL,
  name         text NOT NULL,
  filters      jsonb NOT NULL,             -- {from, to, category_ids, group_by}
  created_at   timestamptz NOT NULL,
  last_run_at  timestamptz NULL,
  cached_result jsonb NULL                 -- last computed aggregates
)
```

### quests_db (Quest Service)

```sql
expense_projection ( ... )                  -- same event-fed shape as reports_db

goals (
  id            uuid PK,
  user_id       uuid NOT NULL,
  name          text NOT NULL,
  type          text NOT NULL,             -- 'SPENDING_LIMIT' | 'SAVING_TARGET'
  category_id   uuid NULL,                 -- NULL = overall
  target_amount bigint NOT NULL,
  period        text NOT NULL,             -- 'DAILY' | 'MONTHLY' | 'YEARLY'
  start_date    date NOT NULL,
  end_date      date NULL,
  active        boolean NOT NULL DEFAULT true
)

goal_evaluations (                          -- powers the calendar (FR-7)
  id            uuid PK,
  goal_id       uuid FK -> goals,
  period_start  date NOT NULL,
  period_end    date NOT NULL,
  actual_amount bigint NOT NULL,
  met           boolean NOT NULL,
  evaluated_at  timestamptz NOT NULL,
  UNIQUE (goal_id, period_start)
)

quest_templates (                           -- seeded; rules engine input
  id          uuid PK,
  code        text UNIQUE NOT NULL,        -- 'NO_SPEND_DAYS', 'CATEGORY_CAP', ...
  title       text NOT NULL,
  description text NOT NULL,
  rule        jsonb NOT NULL               -- parameterized rule definition
)

quests (
  id              uuid PK,
  user_id         uuid NOT NULL,
  template_id     uuid FK -> quest_templates,
  title           text NOT NULL,           -- rendered with user's params
  params          jsonb NOT NULL,          -- {cap: 20000, category_id: ...}
  period_start    date NOT NULL,
  period_end      date NOT NULL,
  status          text NOT NULL,           -- 'SUGGESTED'|'ACTIVE'|'COMPLETED'|'FAILED'|'DECLINED'
  progress_amount bigint NOT NULL DEFAULT 0,
  updated_at      timestamptz NOT NULL
)
```

### notifications_db (Notification Service)

```sql
notifications (
  id          uuid PK,
  user_id     uuid NOT NULL,
  type        text NOT NULL,               -- 'QUEST_COMPLETED', 'GOAL_AT_RISK', 'REPORT_READY'
  title       text NOT NULL,
  body        text NOT NULL,
  data        jsonb NULL,                  -- deep-link payload
  read_at     timestamptz NULL,
  created_at  timestamptz NOT NULL
)
```

### Event catalog (RabbitMQ, JSON payloads)

| Event | Producer | Consumers |
|-------|----------|-----------|
| `expense.created` / `expense.updated` / `expense.deleted` | Expense | Report, Quest |
| `category.updated` | Expense | Report, Quest |
| `goal.evaluated` | Quest | Notification |
| `quest.completed` / `quest.failed` / `quest.suggested` | Quest | Notification |
| `report.generated` | Report | Notification |
| `user.registered` | User | Expense (seed default categories) |

---

## 7. API Surface (high level)

All routes behind the gateway under `/api/v1/…`; all except auth require `Authorization: Bearer <JWT>`.

```
POST   /auth/register            POST   /auth/login          POST /auth/refresh
POST   /auth/logout              GET    /auth/oauth/google   (redirect flow)

GET/PUT    /me                   GET/POST/PUT/DELETE /me/income-sources
GET/POST/PUT/DELETE /me/savings  GET    /me/net-worth
POST   /salary-calculator        (stateless: gross→net / net→gross)

GET/POST   /categories           PUT/DELETE /categories/{id}
GET/POST   /expenses             PUT/DELETE /expenses/{id}
GET        /expenses?from=&to=&categoryId=&page=

GET        /dashboard            (aggregate widgets payload)
GET/POST   /reports              GET/PUT/DELETE /reports/{id}
POST       /reports/{id}/run     GET /reports/{id}/export?format=csv|pdf

GET/POST   /goals                PUT/DELETE /goals/{id}
GET        /calendar?year=&month=            (goal fulfillment map)
GET        /quests?status=       POST /quests/{id}/accept|decline
GET        /notifications        POST /notifications/{id}/read

WS         /ws                   (STOMP; topics: /user/queue/notifications)
```

---

## 8. Delivery Roadmap

| Milestone | Contents | Exit criteria |
|-----------|----------|---------------|
| **M0 — Skeleton** | Repo layout, docker-compose (Postgres, RabbitMQ, gateway, all services stubbed), CI, Flyway baseline, React app shell | `docker-compose up` serves a health-checked hello through the gateway |
| **M1 — Auth** | Register/login/refresh/logout, Google OAuth, account linking | FR-1 ACs pass |
| **M2 — Expenses** | Categories/subcategories, expense CRUD, default category seeding, events publishing | FR-2b, FR-5, FR-9 |
| **M3 — Dashboard & Reports** | Report service projections, dashboard endpoints, charts UI, saved reports, CSV export | FR-3, FR-6, FR-4 (CSV) |
| **M4 — Profile & Money** | Income sources, savings, net worth widget, salary calculator | FR-2, FR-8, FR-10 |
| **M5 — Goals & Calendar** | Goals CRUD, evaluation job, calendar UI | FR-7 |
| **M6 — Quests & Notifications** | Quest templates + generation, progress tracking, WebSocket notifications, notification center | FR-11, FR-12 |
| **M7 — Hardening** | Load test to 1k concurrent (k6), p95 tuning, PDF export, widget grid arrangement, security pass | NFR-2, NFR-8 verified |

Each milestone is shippable; nothing depends on a later one.

---

## 9. Open Questions (park for later, don't block M0)

1. **Quest generation rules** — which concrete templates ship in v1? (Proposal: category cap, overall weekly cap, no-spend days, "beat last month".) Needs a short design session before M6.
2. **PDF export** rendering approach — server-side (OpenPDF/JasperReports) vs. print-styled HTML. Decide in M3.
3. **Recurring expenses** (rent auto-entry monthly) — not in the original notes; cheap to add in M2 if wanted.
4. **Hosting target for v1 prod** — single VPS with docker-compose is the assumption; revisit if availability targets demand more.
5. **Widget arrangement persistence** — per-user layout JSON in User Service; confirm scope in M7.
