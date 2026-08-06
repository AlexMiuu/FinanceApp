# Argali — Design Document

**Product:** Argali, a personal-finance web app (Romania / RON)
**Owner:** Alexandru
**This document** consolidates the product's objectives, architecture, requirements, and — new in this revision — the **visual design system** (the "Răboj — shepherd's ledger" world). It complements the engineering spec in the repo-root `DESIGN.md`; where they overlap, this file is the summary and the root spec is the authority for data models and API surface.

---

## 1. App Objectives

A personal-finance app that helps a user **track expenses, understand spending through reports and dashboards, and improve habits through tailored goals, quests, and challenges.**

| # | Objective | Scope |
|---|-----------|-------|
| O1 | Track expenses daily / monthly / yearly | v1 ✅ |
| O2 | Reports & dashboard: graphs, projections, bar/pie charts | v1 ✅ |
| O3 | Quests & challenges tailored to the user (income, mandatory expenses, history) | v1 ✅ |
| O4 | Bank-account linking (expense import) + investment tracking | v2 ⏳ (schema/architecture leave room) |
| O5 | Room to grow | ongoing |

**Design intent:** make daily money-keeping feel like tending a **shepherd's account book** — deliberate, tactile, and rewarding — rather than a generic fintech dashboard.

---

## 2. Overall Architecture & Structure

### 2.1 System (microservices)

```
   React SPA ──HTTPS──▶  API Gateway (Spring Cloud Gateway: JWT, CORS, routing)
        │  WebSocket          │
        ▼                     ├──▶ User Service      (auth, profile, income, savings, net worth, salary calc)
  Notification Service        ├──▶ Expense Service   (expenses, categories)
   (STOMP/WebSocket)          ├──▶ Report Service    (aggregations, saved reports, CSV/PDF export)
        ▲                     └──▶ Quest Service      (goals, calendar, quests, progress)
        │
        └────────── RabbitMQ (async events) ────────── all services publish/consume
                    PostgreSQL (database-per-service, no cross-service DB reads)
```

- **Communication:** synchronous REST (JSON) through the gateway for client calls; **asynchronous RabbitMQ events** between services. Report/Quest keep event-fed local projections (eventually consistent, sub-second).
- **Data ownership:** database-per-service; UUIDv7 keys; money stored as `BIGINT` in **bani** (RON cents), never floats; `timestamptz` throughout.
- **Auth:** User Service issues RS256 JWTs (short-lived access + rotating HttpOnly refresh); gateway validates.
- **Portability:** fully containerized — one `docker-compose up` brings up Postgres, RabbitMQ, gateway, and all services.

### 2.2 Frontend structure (`frontend/`)

React 18/19 + TypeScript + Vite. Single-page app shell; no client router — view state lives in `HomePage`.

```
frontend/src/
├─ App.tsx                 auth gate → HomePage | AuthPage
├─ main.tsx                root + ToastProvider
├─ index.css               design tokens, theme, materials, motion (the design system)
├─ auth/AuthContext.tsx    session bootstrap (refresh cookie), login/register/logout
├─ lib/
│  ├─ api.ts               typed fetch client (Bearer + 401-refresh retry) + all endpoints
│  ├─ categoryUsage.ts     local "frequent categories" ranking
│  └─ ws.ts                STOMP notifications client
├─ components/
│  ├─ Sidebar.tsx          collapsible rail (desktop) + bottom nav + FAB (mobile)
│  ├─ HomePage header      title/subtitle + ⌘K search + notifications bell
│  ├─ AddSheet.tsx         quick-add expense slide-over (wired to the real API)
│  ├─ BootSplash.tsx       session intro splash
│  ├─ brand.tsx            Argali mark + one-hand icon set (nav, close, chevron, check, bell, search)
│  ├─ raboj.tsx            RabojStreak (notch tally) + Stamp (inked status)
│  ├─ NotificationsBell.tsx  live WebSocket notification center
│  ├─ Toast.tsx            transient confirmations
│  └─ ui/                  shadcn/ui primitives (Button, Input, Select, Card, …)
└─ pages/
   ├─ DashboardTab.tsx     Overview — carried-forward hero, spend donut, recent activity, widgets
   ├─ ExpensesTab.tsx      Ledger — search, filter chips, ruled ledger tape, add/edit, recurring, categories
   ├─ ReportsTab.tsx       Reports — saved reports, KPI tiles, trend line, by-category
   ├─ QuestsTab.tsx        Quests — streak (răboj), suggestions, goal calendar, active quests, goals
   └─ ProfileTab.tsx       Account — profile, income sources, savings pots, salary calculator, sign-out
```

**Screens (5, single shell):** Overview · Expenses · Reports · Quests · Account. Global affordances: collapsible rail (folds to a bottom bar + FAB under 768px), ⌘K search, live notifications, and an add-transaction slide-over reachable from anywhere.

---

## 3. Functional Requirements

Prioritized MoSCoW; each has testable acceptance criteria in the root spec. Summary:

| ID | Req | Priority |
|----|-----|----------|
| FR-1 | Register / log in / log out — email+password (Argon2id) **and** Google OAuth; JWT access + rotating refresh; account linking by verified email | Must |
| FR-2 | Enter income sources (amount + recurrence) and savings pots in profile | Must |
| FR-2b | Record expenses: amount (RON), category/subcategory, date, note | Must |
| FR-3 | Generate, save, and revisit reports (named filter sets + computed aggregates) | Must |
| FR-4 | Reports in-app only, with explicit export — **CSV** (must), PDF (should) | Must |
| FR-5 | User-defined categories & subcategories (one level), "mandatory" flag, seeded defaults | Must |
| FR-6 | Dashboard visualizations: pie by category, bar/line over time, month-end projection | Must |
| FR-7 | Calendar of goal fulfillment (daily/monthly/yearly), colored met / not-met / no-data | Must |
| FR-8 | Net worth on the dashboard (= sum of savings in v1) | Must |
| FR-9 | Edit and delete expenses from lists; recalculates dependent reports/goals/quests | Must |
| FR-10 | Romanian net↔gross salary calculator — CAS 25% / CASS 10% / income tax 10%, **versioned config** | Should |
| FR-11 | Quests & challenges tailored from templates + income + mandatory expenses + history; auto-evaluated | Must |
| FR-12 | In-app live notifications over WebSocket + read/unread center | Should |

Recurring monthly expenses (auto-posted) were added as a usability extension in the Expenses flow.

**Deferred to v2:** bank-account linking (`source` + `external_id` fields already present for idempotent import), investment tracking, browser-push / email notifications.

---

## 4. Non-Functional Requirements

| ID | Requirement | Target / Verification |
|----|-------------|----------------------|
| NFR-1 | Responsive & dynamic UI | Usable 360–1440px; Lighthouse ≥ 90 performance on dashboard |
| NFR-2 | Low latency | p95 API read < 300ms, write < 500ms under normal load |
| NFR-3 | High availability | 99.5% monthly; health checks + auto-restart; Postgres the only stateful SPOF |
| NFR-4 | Sensitive-data protection | TLS everywhere; Argon2id hashing; tokens stored hashed; encrypted at rest; **no financial data in URLs or logs** |
| NFR-5 | Modular, maintainable code | Enforced service boundaries (no shared DB); per-service tests; CI gates merge on build+tests |
| NFR-6 | Clean, appealing visuals | Single design system — **shadcn/ui on Tailwind** — with a consistent chart palette |
| NFR-7 | Widgets & notifications | Dashboard widgets (net worth, top categories, quest progress, calendar); live notifications |
| NFR-8 | Scale | Sustain 1,000 concurrent users; verified with k6/Gatling against the compose stack |
| NFR-9 | Portability | Everything containerized; one `docker-compose up`; 12-factor env config |

Design-specific NFRs added by the redesign: **WCAG contrast ≥ 4.5:1** for text, **`prefers-reduced-motion`** honored, drawn SVG icons only (no emoji), and a single theme tuned for indoor, at-a-glance use.

---

## 5. Design Style & Colorways

### 5.1 The world — "Răboj, the shepherd's ledger"

Argali (a wild ram) is the brand. The design draws on the **răboj**, the Carpathian shepherd's **notched tally stick** — the genuine folk-accounting artifact of the culture the app serves. The surface reads like a warm **account book**: tanned-hide espresso ground, kraft-paper cards, hairline rules, a single struck-brass accent, and inked stamps. It deliberately refuses the interchangeable trust-blue fintech dashboard.

**Mode:** Operate (a tool to complete tasks). One dark theme by design — the book is read indoors, at a glance, on a warm dark surface.

### 5.2 Colorways

Semantic tokens (CSS variables in `index.css`; single dark theme, `:root` mirrored by `.dark`):

| Role | Token | Value | Use |
|------|-------|-------|-----|
| Ground | `--background` | `#14100d` | app background (tanned hide) |
| Card | `--card` | `#1d1712` | standard surfaces |
| Paper | `--paper` | `#241c15` | kraft ledger surfaces (hero) |
| Popover | `--popover` | `#2a211a` | menus, tooltips |
| Foreground | `--foreground` | `#f3ece0` | primary text (warm ivory) |
| Muted text | `--muted-foreground` | `#bfae97` | captions, meta (~6:1 on card) |
| **Accent** | `--primary` / `--brass` | `#c79a5b` | struck brass — CTAs, active nav, progress, notches |
| On-accent | `--primary-foreground` | `#14100d` | text on brass |
| Positive | `--good` | `#9cb37a` | income, on-budget (sage) |
| Negative | `--destructive` | `#c96a4e` | spend, over-budget (terracotta) |
| Border | `--border` | `oklch(1 0 0 / 8%)` | hairlines |
| Ring | `--ring` | `#c79a5b` | focus rings |

**Chart palette** (warm categorical): `#c79a5b · #9cb37a · #b6763e · #8a6440 · #6f5638`. Status is never encoded by color alone (calendar/quests carry shape marks — a notch for met, a cross for over).

### 5.3 Typography

The user's request pinned **Inter** as the workhorse; personality lives in the ledger voice, not in an expressive body face.

| Token | Face | Role |
|-------|------|------|
| `--font-sans` | **Inter** | all UI, body, labels |
| `--font-heading` | **Zilla Slab** | stamped ledger headers & headline figures (slab = account-book / official) |
| `--font-mono` | **Spline Sans Mono** | ruled ledger columns, serial numbers, money figures |

Money and stats use tabular-lining numerals (`.figure` / `.tnum`). Body floor ≥ 12px; section captions are `.ledger-label` (12px Inter uppercase, brass-tinted) — used as **column/section captions**, never as floating eyebrows.

### 5.4 Materials, shape & motion

- **Cards:** `.ledger-card` — kraft-paper gradient, hairline brass top rule, one radius (`--radius-2xl` ≈ 16px). `.ledger-paper` — the hero page surface (warmer paper, brass edge, `.ink-underline` under carried-forward totals).
- **Signature elements:** the **răboj tally** (`RabojStreak` — carved notches bundled in fives, ghost guides for room to grow) and the **ledger tape** (Expenses as ruled rows with serial numbers, column headers, and a page subtotal). Statuses render as inked **stamps**.
- **Iconography:** one hand — drawn SVG icons at 1.7 stroke, round joins (nav set, close, chevron, check, bell, search) + the Argali ram mark. **No emoji anywhere.**
- **Grain:** a faint (5%) paper-grain overlay across the app.
- **Motion:** 150–300ms transitions; entrance keyframes (`sheetIn`, `riseIn`, boot sequence); `prefers-reduced-motion` collapses all animation.

### 5.5 Accessibility & responsive

- Contrast ≥ 4.5:1 for text; focus rings themed in brass; nav controls carry `aria-label`; expandable rows expose `role`/`aria-expanded`.
- Themed browser surfaces (scrollbar, selection, caret, date-picker glyph) — nothing left at OS default.
- Responsive: rail (desktop) folds to a bottom nav + FAB under 768px; layouts stack and reflow 360–1440px.

**Known follow-ups:** raise icon-button hit areas to 44px on touch; add a mobile search affordance; gate the boot splash to once per session. (Independent finish-review disposition on the visual system: **ship**.)

---

## 6. Tech Stack (summary)

Backend: Java 21 + Spring Boot 3, Spring Cloud Gateway, Spring Security + OAuth2, PostgreSQL 16, Flyway, RabbitMQ, Redis (optional). Frontend: React + TypeScript + Vite, Tailwind CSS + shadcn/ui, Recharts (+ hand-authored SVG charts), STOMP over WebSocket. Delivery: Docker + docker-compose, GitHub Actions CI, k6 load testing.

---

*Authoritative data models, event catalog, API surface, and delivery roadmap live in the repo-root `DESIGN.md`. This document is the product + design-system summary.*
