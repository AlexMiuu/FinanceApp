# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Romanian-market individuals who want to track personal expenses, understand their spending, and improve money habits. Multi-user product — designed for other people to register and use, not a single-owner tool, though it is early in its rollout. Users log in via email/password or Google OAuth and interact primarily on a single-page app shell across desktop and mobile browsers.

## Product Purpose

Argali is a personal-finance web app that helps a user track expenses, understand spending through reports and dashboards, and improve habits through goals, quests, and challenges tailored to their own income and mandatory costs. Success is a user who keeps entering expenses because the tracking loop (record → see report → get a tailored quest → improve) is engaging enough to stick with, not just a ledger they abandon after a week.

## Positioning

Distinguishes itself from generic fintech dashboards through two mechanisms a copycat can't just re-skin: (1) quests/challenges auto-generated from the user's actual income, mandatory expenses, and spending history rather than generic budgeting tips, and (2) a built-in Romanian net↔gross salary calculator (CAS/CASS/income tax, versioned config) that speaks directly to the local tax system. RON-first, Romania-focused.

## Operating Context

Single-page React app (no client router — view state lives in the app shell), read primarily indoors at a glance on desktop and mobile. Five core screens behind auth: Overview/Dashboard, Expenses/Ledger, Reports, Quests/Goals, Account/Profile. Global affordances present on every screen: navigation (rail on desktop, bottom nav + FAB on mobile), an add-expense flow reachable from anywhere, and live notifications over WebSocket.

## Capabilities and Constraints

- Auth: email+password (Argon2id) and Google OAuth, JWT access + rotating refresh token, account linking by verified email.
- Expenses: user-defined categories/subcategories (one level of nesting), a "mandatory" flag that feeds quest tailoring, recurring monthly expenses, edit/delete with recalculation of dependent reports/goals/quests.
- Reports: saved named filter sets + computed aggregates, pie/bar/line visualizations, month-end spend projection, CSV export (must) / PDF export (should) — no public/shareable report URLs.
- Goals/Quests: spending-limit or saving-target goals per period with a fulfillment calendar; quests are time-boxed challenges generated from templates + income + mandatory expenses + history, auto-evaluated from expense events.
- Profile: income sources (amount + recurrence), savings pots, net worth (= sum of savings in v1), Romanian net↔gross salary calculator.
- Notifications: live in-app via WebSocket (STOMP), read/unread notification center.
- Deferred to v2 (do not design for now): bank-account linking/import, investment tracking, browser push/email notifications.
- Backend: Java 21 + Spring Boot microservices behind an API gateway, PostgreSQL (database-per-service), RabbitMQ events, Docker/docker-compose. Frontend: React 18 + TypeScript + Vite, Tailwind CSS + shadcn/ui component library (required), Recharts, STOMP over WebSocket.
- Money is always RON, stored as integer bani (cents) — never floating point display errors in the UI.
- Target scale: sustain 1,000 concurrent users; p95 read < 300ms / write < 500ms; Lighthouse ≥ 90 performance on the dashboard; usable 360px–1440px viewport width.

## Brand Commitments

Product name: **Argali** (a wild ram — the brand mark is a ram silhouette + one notch mark). The name and ram mark are a locked brand asset. The prior "Răboj — shepherd's ledger" visual world (kraft-paper/espresso tones, brass accent, notch-tally motif, ledger/stamp component language) is being actively replaced by a new visual direction — treat it as evidence of product maturity and prior craft, not as a binding identity to preserve. Typography choice of **Inter** for UI/body text is a standing preference the user has pinned across at least two design iterations (both the outgoing Răboj system and the new incoming reference use it) — carry it forward unless the user says otherwise.

## Evidence on Hand

- Root `DESIGN.md` — full engineering spec (data models, API surface, event catalog, delivery roadmap). Authoritative for product/data facts, not visual ones.
- `docs/design.md` — product + outgoing visual design-system doc (Răboj). Its non-visual sections (objectives, FRs, NFRs, architecture) remain accurate; its visual section (§5, "Design Style & Colorways") is superseded by the new redesign in progress.
- Working, deployed frontend at `frontend/src/` implementing the Răboj visual system end-to-end (real evidence of current craft level, not just a mock).
- New approved visual reference: a coded comp (`Argali App - Live Responsive.dc.html`) covering every real app screen as conditional states — this is the target for the in-progress redesign.

## Product Principles

1. Tracking must stay low-friction — adding an expense should reflect in totals within one refresh; nothing about the redesign should add steps to that core loop.
2. Money is never ambiguous — RON, tabular figures, integer-bani precision, no floating-point display drift.
3. Personalization comes from the user's real data (income, mandatory expenses, history), not generic advice — quests and projections must read as computed *for this user*, not templated copy.
4. One coherent design system across all five screens and every state (empty, loading, error) — no per-page one-offs.
5. Built to eventually onboard other real users, not just the owner — visual and UX decisions should hold up for a stranger seeing the product for the first time, not only for an insider who already knows the data.

## Accessibility & Inclusion

Contrast ≥ 4.5:1 for text (carried forward from the outgoing design system as a standing requirement, not a discardable stylistic choice). `prefers-reduced-motion` must be honored. No emoji as UI iconography — drawn SVG icons only (a standing preference across design iterations).
