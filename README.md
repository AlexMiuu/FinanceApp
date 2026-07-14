# Personal Finance App

Personal finance web app: expense tracking, reports & dashboards, goal calendar, spending quests, net worth, Romanian salary calculator.

Full spec: [DESIGN.md](DESIGN.md) · Current milestone: **M4 — profile & money**

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
