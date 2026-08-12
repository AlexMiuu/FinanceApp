# Argali — Design Document

**Product:** Argali, a personal-finance web app (Romania / RON)
**Owner:** Alexandru
**This document** consolidates the product's objectives, architecture, requirements, and the **visual design system**. It complements the engineering spec in the repo-root `DESIGN.md`; where they overlap, this file is the summary and the root spec is the authority for data models and API surface.

**Design history:** the app shipped an earlier visual system, "Răboj — shepherd's ledger" (warm kraft-paper/espresso, brass accent, notch-tally streak, rotated ink-stamp badges). That system has been fully replaced by the one recorded below — treat any reference to brass/kraft/notch-tally/ink-stamp elsewhere (old docs, comments, memory) as superseded, not current.

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

**Design intent:** make daily money-keeping feel like a precise **operator's account book** — legible, hairline-ruled, and unhurried — rather than a generic rounded fintech dashboard. (Superseded phrasing, kept for history: an earlier revision framed this as a "shepherd's account book"; see §5 for the current system.)

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
│  ├─ raboj.tsx            RabojStreak (calendar-grid streak) + Stamp (flat status tag) — names kept for API stability, visuals rethemed
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

### 5.1 The world — the operator's account book

Argali (a wild ram) is the brand. This revision replaces the earlier warm "shepherd's ledger" world with a **near-black operator's account book**: precision and legibility over craft-fair warmth. Hairline borders, near-zero radius, and a struck-teal accent stand in for the old rounded kraft-paper/brass system. Status reads from a colored left-edge mark and a mono label, never a rounded badge or a rotated ink stamp. It refuses both the old brass-ledger warmth and the interchangeable trust-blue fintech dashboard.

The build reproduces an externally approved reference comp directly (a coded `.dc.html` comp covering every real screen as conditional states) rather than an internally invented concept — see the direction-contract HTML comment at the top of `frontend/index.html` for the recorded THESIS/OWN-WORLD/STORY/FIRST VIEWPORT/FORM/FINISH contract.

**Mode:** Operate (a tool to complete tasks). One dark theme by design — the book is read indoors, at a glance.

### 5.2 Colorways

Semantic tokens (CSS variables in `index.css`; single dark theme, `:root` mirrored by `.dark`):

| Role | Token | Value | Use |
|------|-------|-------|-----|
| Ground | `--background` | `#0E1113` | app background |
| Card | `--card` | `#101416` | standard surfaces, hero band |
| Paper | `--paper` | `#171C1F` | secondary surface tier |
| Popover | `--popover` | `#14181B` | menus, dropdowns, tooltips |
| Foreground | `--foreground` | `#F2F5F6` | primary text |
| Muted text | `--muted-foreground` | `#9AA3A8` | captions, meta |
| **Accent** | `--primary` / `--brass`\* | `#9AD4E3` | struck teal — CTAs, active nav, focus, ledger-label tint |
| On-accent | `--primary-foreground` | `#0E1113` | text on teal |
| Positive | `--good` | `#8FC7A6` | income, on-budget (sage) |
| Negative | `--destructive` | `#E09880` | spend, over-budget, unread/alert |
| Border | `--border` | `#62696D` | hairlines everywhere |
| Ring | `--ring` | `#9AD4E3` | focus rings |

\* `--brass` is a legacy token name kept for CSS-variable stability across the redesign; it now holds the teal accent value, not brass.

**Chart palette** (teal-toned categorical): `#9AD4E3 · #8FC7A6 · #4C93A6 · #E09880 · #8A9399`. Status is never encoded by color alone — a left-edge inset-shadow mark (`.edge-mark-good` / `.edge-mark-bad` / `.edge-mark-accent`) always accompanies the color.

### 5.3 Typography

**Inter** remains the pinned workhorse across both design generations. What changed: money and headline figures now speak in the mono ledger-column voice (Spline Sans Mono), not the slab serif — Zilla Slab is reserved for page/section titles only.

| Token | Face | Role |
|-------|------|------|
| `--font-sans` | **Inter** | all UI, body, labels |
| `--font-heading` | **Zilla Slab** | page/section titles only (e.g. the shared `<h1>` in `HomePage.tsx`) |
| `--font-mono` | **Spline Sans Mono** | nav, section captions (`.ledger-label`), ALL money/headline figures (`.figure`), column headers |

Money and stats use tabular-lining numerals (`.figure` / `.tnum`). Body floor ≥ 12px; section captions are `.ledger-label` (mono, uppercase, tracked, teal-tinted) — used as **column/section captions**, never as floating eyebrows.

### 5.4 Materials, shape & motion

- **Panels:** `.ledger-card` — flat surface, hairline border, near-zero radius (`--radius: 0.125rem`, ≈2px), no gradient. `.ledger-paper` — the hero/section band: full-bleed, bounded by hairline top/bottom rules, no radius (replaces the old rounded kraft-paper hero).
- **Signature elements:** the **entry streak** (`RabojStreak` — a calendar grid of filled/empty squares, replacing the old carved-notch tally graphic; export name kept for API stability) and the **ledger tape** (Expenses as ruled rows via the `.ruled` utility, with serial numbers and a page subtotal). Statuses render as flat mono `.status-tag` labels with a colored left-edge mark (`Stamp` component; replaces the old rotated ink-stamp look).
- **Ghost balance:** the Overview hero includes a real (not fabricated) "if this month had kept last month's pace" comparison, computed from `Dashboard.previousMonthTotal` prorated to today's day-of-month — only renders when that data exists.
- **Iconography:** one hand — drawn SVG icons, round joins (nav set, close, chevron, check, bell, search) + the Argali ram mark, retinted to the teal accent. **No emoji anywhere.**
- **Grain:** removed. The old paper-grain texture overlay belonged to the kraft-paper world; this system is crisp and flat by design.
- **Motion:** 150–300ms transitions; entrance keyframes (`sheetIn`, `riseIn`, `bootRise`/`bootFill`/`bootOut`, `fadeIn`); `prefers-reduced-motion` collapses all animation. The old notch-cut and coil-unroll keyframes (brass-motif-specific) were removed with the motif.

### 5.5 Accessibility & responsive

- Contrast ≥ 4.5:1 for text; focus rings themed in teal; nav controls carry `aria-label`; expandable rows expose `role`/`aria-expanded`.
- Themed browser surfaces (scrollbar, selection, caret, date-picker glyph) — nothing left at OS default.
- Responsive: rail (desktop) folds to a bottom nav + FAB under 768px; layouts stack and reflow 360–1440px.

**Closed in M10, before this redesign, and still true of it:** icon-button hit areas raised to 44×44 at ≤768px (enforced once as a zero-specificity `:where()` floor in `index.css`, so new components inherit it rather than re-specifying it); a mobile search affordance — under 640px the field collapses behind a 44px search control and expands to a full-width row, with `⌘K` and Escape wired to it; and the boot splash gated to once per session via `argali:splash-seen`. The dashboard is also user-arrangeable as of M10: widgets move between the wide and narrow columns by drag or by arrow buttons, and the arrangement persists per user in User Service (D9 — it is a choice nothing can regenerate, so it does not belong in localStorage). All of this behavior carried through this redesign unchanged; only its visual surface moved to the new system below.

**F4 Shepherd's Weather (M13) under the new system:** the ambient background bands (`.weather-gathering` / `.weather-storm`, applied at root by `useShepherdWeather`) are retinted off the redesign's near-black base (`#0E1113`) rather than the old kraft-paper one — same clear → dimmer → darkest relationship, new palette, contrast unaffected since `--foreground` never moves.

### 5.6 Intentional scope boundaries (visual-only redesign)

This redesign preserved existing behavior, data-fetching, and API wiring exactly — it did not implement every mechanic the approved reference comp shows, where doing so would have meant new product behavior rather than a visual change:

- **Mobile navigation topology.** The approved comp specifies a sticky top bar that expands into a dropdown tab list on tap. The app keeps its pre-existing bottom tab bar + floating add-button pattern instead. This is a deliberate choice, not an oversight: it's an established, ergonomic mobile pattern already in place before this redesign, and switching topologies is a structural/behavioral change beyond a visual reskin.
- **Ledger running-balance & inline row actions.** The comp's ledger carries a running balance column and inline edit/void actions per row (a "correction-appended, never-renumbered" ledger model). The shipped Expenses table keeps its existing No./Entry/Amount·Date columns with an expand-to-detail row instead. The comp's model is behavioral, not just visual, so it's out of scope here — a candidate for a future feature-level pass, not this redesign.
- **Header export control.** The comp shows a header "EXPORT" button. This app does not have one, by a standing pre-redesign product decision: CSV/PDF export is owner-handled outside the UI, not a user-facing feature in the current launch scope. Its absence predates and is unrelated to this redesign.

**Known follow-ups:** this redesign was built against a `dev`-stale branch and had to be reconciled against `dev`'s actual current state (M9–M15: widget arrangement, Ghost Flock, Tally Oath, Shepherd's Weather, seasonal transhumance, dev-API) before merging — DashboardTab, HomePage, ProfileTab, and BootSplash needed a fresh retheme pass against that real content rather than a patch of the stale one; give those screens, and the `PledgeSheet` component, a finish-review pass of their own once landed. (Independent finish-review disposition on the redesign, prior to this reconciliation: **ship**.)

---

## 6. Tech Stack (summary)

Backend: Java 21 + Spring Boot 3, Spring Cloud Gateway, Spring Security + OAuth2, PostgreSQL 16, Flyway, RabbitMQ, Redis (optional). Frontend: React + TypeScript + Vite, Tailwind CSS + shadcn/ui, Recharts (+ hand-authored SVG charts), STOMP over WebSocket. Delivery: Docker + docker-compose, GitHub Actions CI, k6 load testing.

---

*Authoritative data models, event catalog, API surface, and delivery roadmap live in the repo-root `DESIGN.md`. This document is the product + design-system summary.*
