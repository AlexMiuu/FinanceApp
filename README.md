# Personal Finance App

Personal finance web app: expense tracking, reports & dashboards, goal calendar, spending quests, net worth, Romanian salary calculator.

Full spec: [DESIGN.md](DESIGN.md) · Current milestone: **M6 — quests & notifications**
Refactoring REFACTOR.md
## Quick entry & recurring expenses

- **Recurring monthly templates** (`/api/v1/expenses/recurring`): tick "Repeat monthly"
  on the add-expense form. A daily job + startup catch-up posts occurrences through the
  normal expense flow (events, projections, quests all see them). Backdating a template
  auto-fills the missed months; end-of-month anchors clamp (31st → Feb 28) and recover.
- **Quick-add ergonomics**: one-tap chips for your most-used categories, last-used
  category preselected, "Again" re-adds a row dated today, amount field refocuses after
  each save. (Interim measures until CSV bank-statement import lands.)

## Quests & notifications (M6)

- Four seeded quest templates (`quest_templates`): CATEGORY_CAP, WEEKLY_CAP,
  NO_SPEND_DAYS, BEAT_LAST_MONTH. Caps are tailored from the previous full weeks'
  discretionary spend (85%), with an income-based fallback (`income.updated` events feed a
  projection); mandatory categories are excluded; dead-on-arrival caps are never suggested.
- Lifecycle: SUGGESTED → accept/decline → ACTIVE → COMPLETED/FAILED. Cap quests fail the
  moment an expense event pushes them over; a nightly job (and lazy reads) finalize ended
  periods. Transitions publish `quest.*` events.
- notification-service persists quest events and pushes them over **STOMP/WebSocket**
  (`/ws`, JWT on the CONNECT frame, per-user queues). Bell + notification center in the
  header update live — no reload.
- All containers run in `Europe/Bucharest` so day boundaries match the user.

## Goals & calendar (M5)

- Spending-limit goals per day/month/year, overall or per category (subcategories
  inherit the parent's goal scope). Current-period status on every goal.
- `GET /api/v1/calendar?month=` — per-day met/missed/in-progress/future for daily
  goals plus monthly/yearly summaries; completed periods are persisted to
  `goal_evaluations` (history feeds M6 quest tailoring).
- quest-service runs its own event-fed expense/category projection like report-service.
- `SAVING_TARGET` goals are schema-reserved but deferred: they need savings events
  from user-service that don't exist yet.

## Profile & money (M4)

- Income sources (amount + recurrence) and savings accounts under `/api/v1/me/**`;
  `GET /api/v1/me/net-worth` returns savings total + normalized monthly income and
  feeds the dashboard's net-worth tile (FR-8).
- `POST /api/v1/salary-calculator` — Romanian gross↔net with CAS/CASS/income-tax
  breakdown; rates live in the versioned `tax_config` table (a law change is an
  INSERT, not a redeploy).

## Dashboard & reports (M3)

- report-service keeps an **event-fed projection** of expenses (never queries other services);
  category renames re-denormalize projected rows.
- `GET /api/v1/dashboard?month=` — totals, mandatory share, per-category and per-day
  aggregates, linear month-end projection.
- Saved reports = named filter sets, re-evaluated on `POST /reports/{id}/run` (result cached);
  `GET /reports/{id}/export` downloads CSV (FR-4).
- Frontend: dashboard tab (stat tiles, category donut, daily bars — Recharts) and reports tab.

## Expenses (M2)

- Categories/subcategories (one level deep) with a **mandatory** flag that will drive quest
  tailoring; sensible defaults are seeded per user via the `user.registered` event
  (lazy-seeded on first read as fallback).
- Expense CRUD with date/category filters and pagination; amounts stored as `bigint` bani.
- Domain events (`expense.created|updated|deleted`, `category.updated|deleted`) publish to
  the `pf.events` topic exchange after commit — report/quest projections consume them in M3+.

## Architecture

Microservices behind an API gateway. Each service owns its database (database-per-service on one PostgreSQL instance); services communicate asynchronously via RabbitMQ.

| Component | Tech | Port (host) |
|-----------|------|-------------|
| frontend | React + TypeScript + Vite + Tailwind + shadcn/ui | 3000 |
| gateway | Spring Cloud Gateway | 8080 |
| user-service | Spring Boot (auth, profile, income, savings, salary calc) | — (8081 internal) |
| expense-service | Spring Boot (expenses, categories) | — (8082 internal) |
| report-service | Spring Boot (aggregation, reports, export) | — (8083 internal) |
| quest-service | Spring Boot (goals, calendar, quests) | — (8084 internal) |
| notification-service | Spring Boot (WebSocket, notifications) | — (8085 internal) |
| postgres | PostgreSQL 16 | 5432 |
| rabbitmq | RabbitMQ 4 (+ management UI) | 5672 / 15672 |

Only the gateway and frontend are exposed; services are reached exclusively through the gateway.

## Run everything

Requires Docker Desktop.

```sh
docker compose up --build
```

- Frontend: http://localhost:3000 — register an account or sign in
- RabbitMQ UI: http://localhost:15672 (guest/guest)

## Authentication (M1)

- Email/password (Argon2id) and optional Google OAuth. Access tokens are 15-minute RS256 JWTs
  issued by user-service and validated by the gateway against `/api/v1/auth/jwks`.
  Refresh tokens are rotating, stored hashed, and travel only in an HttpOnly cookie
  scoped to `/api/v1/auth`. Reusing a rotated refresh token revokes all of that user's sessions.
- Every route except `/api/v1/auth/**` requires `Authorization: Bearer <token>` at the gateway.
- Endpoints: `POST /api/v1/auth/register | login | refresh | logout`, `GET /api/v1/me`,
  `GET /api/v1/auth/jwks`, `GET /api/v1/auth/oauth/providers`.

**Google OAuth setup (optional):** create an OAuth client at
https://console.cloud.google.com/apis/credentials with redirect URI
`http://localhost:3000/api/v1/auth/oauth/callback/google`, then copy `.env.example` to `.env`
and fill `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET`. Without credentials the Google button
simply doesn't appear.

**Dev note:** without `AUTH_JWT_PRIVATE_KEY_PEM` the signing key is ephemeral —
restarting user-service invalidates existing sessions.

## Local development

**Frontend** (hot reload, proxies `/api` to the gateway on 8080):

```sh
cd frontend
npm install
npm run dev
```

**Backend** — run infra only, then start services from your IDE (requires local Maven + JDK 21; each service's `application.yml` defaults point at localhost):

```sh
docker compose up postgres rabbitmq
```

## Repository layout

```
backend/            Maven multi-module (gateway + 5 services, one Dockerfile)
frontend/           Vite React app (shadcn/ui)
infra/postgres/     DB-per-service init script
docker-compose.yml  Full stack
DESIGN.md           Requirements, architecture, schema, roadmap
```
