# Personal Finance App

Personal finance web app: expense tracking, reports & dashboards, goal calendar, spending quests, net worth, Romanian salary calculator.

Full spec: [DESIGN.md](DESIGN.md) · Current milestone: **M0 — skeleton**

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

- Frontend: http://localhost:3000
- Gateway hello checks: http://localhost:8080/api/v1/expenses/hello (same pattern for auth, reports, quests, notifications)
- RabbitMQ UI: http://localhost:15672 (guest/guest)

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
