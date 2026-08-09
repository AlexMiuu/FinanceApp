# Argali — Delivery Roadmap (M8 → M17)

**Status:** `pending approval` — planning artifact. No implementation has started.
**Date:** 2026-08-06
**Evidence baseline:** `origin/main` @ `562a826`.
**Inputs:** `argali-planning-brief.md` (strategy), `DESIGN.md` (data models/API), `REFACTOR.md` (code standard, authoritative), `docs/design.md` (design system).

---

## 1. Scope

| Question | Decision |
|---|---|
| Coverage | Full roadmap — F1–F5, the Macro Service question, hardening, developer/read API, design follow-ups |
| Depth | Milestone-level (contents + exit criteria + dependencies) |
| Launch bar | **Personal use.** Privacy/GDPR groundwork built now so a future public launch is config-and-content, not re-architecture |
| Priority | **M8 privacy is first** |
| Excluded | **PDF/CSV in both directions** — export, export changes, and statement upload. Owner-handled |
| Code standard | **`REFACTOR.md` is authoritative** for all new backend work (D8) |
| Git workflow | `main` (owner-only) ← PRs ← `dev` ← PRs ← `feature/*` (D10) |

**Effort sizing** is relative (S / M / L) — capacity unspecified.

---

## 2. Starting state, from `origin/main`

### Service standardization — 2 of 5 complete

`REFACTOR.md` mandates the `user-microservice` layered template for every service. Actual state:

| Service | Standardized? | Package shape on `origin/main` | Test files |
|---|---|---|---|
| **user** | ✅ template | `controller/ service/ repository/ entity/ dto/ mapper/ exception/ config/ events/` | 3 |
| **expense** | ✅ template | same, plus `scheduler/` | 7 |
| **report** | ❌ **not started** | `dashboard/ domain/ report/ web/ events/ config/` | 1 |
| **quest** | ❌ **not started** | `goal/ quest/ domain/ web/ events/ config/` | 2 |
| **notification** | ⚠️ partial | has `controller/` but `domain/`; no `dto/`, `mapper/`, `exception/` | **0** |
| gateway | n/a | routing only | 0 |

**This drives the sequence.** F2 lands in Quest; F3, F4, and F1 all land in Report. Both are un-standardized. Building features into them first and refactoring after means writing the code twice and turning the refactor diff into an unreviewable mess.

### Feature baseline

| Capability | State | Evidence (`origin/main`) |
|---|---|---|
| Expense/category CRUD + events | Shipped | `expense-service/.../events/Events.java` — `expense.created/updated/deleted`, `category.*` |
| Recurring expenses | Shipped | `expense-service/.../scheduler/RecurringExpenseJobs.java` |
| Report projections + dashboard | Shipped | `report-service/.../domain/ExpenseProjectionEntity.java`, `dashboard/DashboardService.java` |
| Saved reports + **CSV export** | Shipped | `report-service/.../report/ReportController.java` |
| Goals + calendar | Shipped | `quest-service/.../goal/` |
| Quests + scheduled finalization | Shipped | `quest-service/.../quest/QuestJobs.java` — `@Scheduled(cron = "0 15 0 * * *")` |
| STOMP notifications | Shipped | `notification-service/.../events/QuestEventListener.java` |
| Răboj tally + ghost guide slots | Shipped | `frontend/src/components/raboj.tsx:67-80` |
| **F1–F5** | **None started** | Zero hits for macro/season/oath/counterfactual/weather/ambient/cohort |
| **Any privacy/data-rights surface** | **None** | Zero hits for erasure/GDPR/consent/account-deletion in any service |
| Load-test harness (k6) | Not built | No such files |

### Two brief assumptions the evidence corrects

1. **F3 does not "reuse the existing ghost-guides element."** `raboj.tsx:67-80` draws faint marks on *un-notched future slots* ("room to grow") — a visual language to extend, not a component to hang a shadow ledger on. Frontend work is new; the backend is cheaper than the brief assumes (D3).
2. **The original M7 exit criteria target a public launch that is now deferred.** NFR-8 (1,000 concurrent, k6-verified) and NFR-3 (99.5% availability) are public-launch obligations. They move to M17.

---

## 3. Decisions

### D8 — `REFACTOR.md` is the code standard; package-by-layer · **binding**
All new backend work follows the `user-microservice` template exactly as `REFACTOR.md` specifies and as `origin/main` implements it.

| Layer | Convention | Reference on `origin/main` |
|---|---|---|
| **Packages** | **Package-by-layer**: `controller/ service/ repository/ entity/ dto/ mapper/ exception/ config/ events/ scheduler/` | `user-service/`, `expense-service/` |
| **Controller** | HTTP only — route, delegate, return. No business logic. Validate with `@Valid` here; reject malformed input with 400 | `controller/AuthController.java` |
| **Service** | All business logic and orchestration. Bypassing layers is prohibited | `service/AuthService.java` |
| **Repository** | Data access only | `repository/UserRepository.java` |
| **Entity** | JPA; money `BIGINT` bani, UUIDv7, `timestamptz`. **Never exposed to a client** | `entity/UserEntity.java` |
| **DTO** | One per file in `dto/`, builder-based, immutable | `dto/SavingsDto.java` |
| **Mapper** | Dedicated `@Component` per aggregate with `toDto` / `toDtos`. Entity↔DTO translation is **its own layer** | `mapper/SavingsMapper.java` |
| **Exception** | One public class per file in `exception/`, message-constructor | `exception/NotFoundException.java` |
| **Handler** | Centralized `@RestControllerAdvice` in `exception/`; sanitized responses, **no stack traces to the client** | `exception/ApiExceptionHandler.java` |

**Also binding from `REFACTOR.md`:** `Optional` over `null` returns · immutability (`final`, records, unmodifiable collections) · fail-fast argument validation · expressive naming · **no redundant comments** (comments explain *why*, never *what*) · SRP, with long service methods broken into private helpers · **Mockito unit tests on the service layer using Given-When-Then, covering null inputs, boundaries, and exception paths — not just happy paths**.

**Incremental, one service at a time** (`REFACTOR.md` §2): establish a build+test baseline, realign, verify. No sweeping cross-service changes.

### D9 — Client state stays tidy; localStorage is a cache, never a store
Regenerable only (if losing a key loses user data, it belongs in a service) · bounded with an explicit cap and eviction · namespaced under one `argali:` prefix · enumerated in a single registry module, no scattered `localStorage` calls · cleared on sign-out *and* account deletion (sign-out currently does not).

Consequences: widget layout persists server-side in User Service. `frontend/src/lib/categoryUsage.ts` gains a cap — it grows once per interaction with no bound today. The registry is also what makes M8's data inventory honest: you cannot document client storage you cannot enumerate.

### D10 — Branch discipline: `main` ← `dev` ← `feature/*`

```
feature/<name> ──PR──▶ dev ──PR──▶ main
                                    ▲
                        owner-only ─┘
```

- **`main` is the source of truth. The owner is the only committer.** No agent opens, merges, or pushes to `main`.
- **`dev` forks from `main`** and is the integration branch. All agent PRs target `dev`.
- **One `feature/*` branch per feature**, cut from `dev`, merged back by PR. One milestone may span several — M9 is explicitly three.
- Ship iteratively: a feature branch is short-lived and merges when its milestone exit criteria pass, not when the roadmap does.

**Status:** `origin/dev` created at `562a826`, identical to `origin/main` (zero divergence, verified). `redesign/argali-raboj-ledger` is fully contained in `origin/main` — nothing stranded. `feature/ledger-ui-redesign` is fully merged and safe to delete.

**Standing caution:** the local checkout has run badly stale (9–11 commits) and that invalidated an entire planning pass. Verify facts against `origin/main` via `git ls-tree` / `git show origin/main:<path>`, not the working tree.

### D1 — Macro Service: event seam now, separate service later
Implement F1's ingestion as a `macro` feature **inside Report Service**, publishing `macro.season.changed` / `macro.cpi.updated` to `pf.events` from day one.

**Why:** a new service is a Maven module, database, Flyway baseline, compose service, CI wiring, and ops surface — heavy for a seasonal-window table plus a CPI series at personal scale. But the coupling risk is real, so consumers bind to the **event contract**, never a Java package; extraction later becomes a deployment change, not a refactor. Macro tables live in `reports_db` and no other service reads them, honouring database-per-service.

**Extraction triggers:** ingestion needs independent retry/release cadence, *or* a consumer outside Report would take on Report's availability as a dependency.

### D2 — F2 Tally Oath belongs to Quest Service; Expense Service is untouched
Quest owns the oath entity, window, and stamps. **Drop the brief's `expense.intent.created` event.**

**Why:** an intention is not an expense — no money is recorded — so it does not belong in the expense ledger's bounded context. Quest **already** consumes the full `expense.*` stream (`events/ProjectionListener.java`) and **already** schedules (`quest/QuestJobs.java`). F2 then needs one entity, one reconciliation path on an existing listener, one sweep, and outbound events — with **zero Expense Service diffs**. The brief's flow modifies a second service to introduce an event with exactly one consumer.

**Timer:** a **scheduled DB sweep** over open, expired oaths, in `scheduler/`, following the `QuestJobs` precedent — not RabbitMQ delayed messages, which need the delayed-exchange plugin and lose the inspectability of a queryable table. Minute-granularity is ample for a purchase pledge.

**Matching rule:** first unresolved oath matching all of — same `user_id`; same category **or its parent**; `|actual − pledged| ≤ max(10%, 500 bani)`; `expense_date` within `[created_at, expires_at]`. First match closes the oath. The parent-category term is required or a "Food" pledge never matches a "Food › Groceries" expense.

**Terminal states:** `KEPT` (matched within tolerance) · `SLIPPED` (matched, over tolerance) · **`FORGONE`** (window closed, no match — a distinct positive state with its own stamp). `FORGONE` is deliberately not folded into `KEPT`: they are different acts and the ledger should say which happened.

**Outbound:** `oath.kept` / `oath.slipped` / `oath.forgone` → Notification, which already consumes `quest.*`.

### D3 — F3 Ghost Flock: one baseline rule, computed on read, no new tables
A single counterfactual — *"non-mandatory spend held at its trailing 3-month median for the same category mix"* — computed in Report Service from the **existing** `expense_projection`. No second stored projection, no new consumer, no migration.

**Why cheaper than the brief assumes:** `expense_projection` already carries `user_id`, `category_path`, `is_mandatory`, `amount`, `expense_date` (`DESIGN.md:285-293`) — every field required. The ghost is a second *computation* over the same rows, not a second copy, which removes a migration, a listener, and the class of bug where real and ghost disagree because one consumer lagged.

**Cost caveat:** a trailing-median query per dashboard read. Negligible at personal scale with the existing `(user_id, expense_date)` index. Cache in `reports_db` only if p95 degrades.

**One rule, not a picker.** A baseline selector costs UI, explanation, and support, and pushes the feature toward the budgeting framing the brief warns against (§4: *"if early users describe Argali primarily as a budgeting app, positioning has failed"*).

### D4 — F4 Shepherd's Weather: banded composite with hysteresis
Composite = trailing-30-day burn rate ÷ normalized monthly income (Report already receives `income.updated`). Bands: clear ≤ 0.8, gathering 0.8–1.0, storm > 1.0, with **±0.05 hysteresis and a 6-hour minimum dwell** before any transition commits.

**Why hysteresis:** without it a user sitting near a boundary gets the whole surface flickering on every expense, which reads as a bug. Dwell time is also what makes it *ambient*; a surface that changes while you watch is an alert, and the brief specifies calm-tech.

`prefers-reduced-motion` → static tint, no transition. **Never gates functionality**: a failed or stale computation degrades silently to "clear".

### D5 — F5 Collective Răboj: specify now, implement at scale
F5 **cannot ship at a personal-use bar** — a k-anonymity floor with one user yields no publishable cohort, by construction. The anonymization boundary and threshold are specified in M8; implementation is gated on real user volume.

**Why specify early:** the boundary constrains event schemas and the lawful-basis record. Deciding it alongside the privacy foundation costs S; retrofitting an anonymization guarantee onto live events is where this goes wrong.

**Threshold (confirmed):** **k ≥ 20** distinct users per cohort-period before any figure is published, with **suppression** (not rounding) below the floor.

### D6 — Developer/read API after the feature trio
Read-only, personal-access-token-scoped API at the gateway plus webhook fan-out over existing RabbitMQ events. Placed after F3/F2/F4 so the ghost, oath, and weather states are stable and become part of the v1 surface rather than a breaking addition. At a personal bar it has immediate value for your own scripts.

### D7 — Open-banking import is blocked at this launch bar
Read-only aggregation via Finqware/Salt Edge is gated behind **commercial contracts a personal deployment cannot obtain** — a business-access blocker, not an engineering one. `source` / `external_id` (`DESIGN.md:274-275`) already reserve the schema. Statement upload is owner-handled.

---

## 4. Milestones

### M8 — Privacy & data-rights foundation · **Size: M** · **FIRST**
Scoped so nothing written here gets relocated by M9: the **contract and the already-standardized services** land now; Report/Quest/Notification handlers land inside their own standardization milestone.

**Contents**
- `user.erasure.requested` event contract + fanout, with per-service completion tracking and a visible terminal state
- Erasure + export handlers for **User and Expense** (both already on the template)
- Per-user data export, machine-readable (GDPR Art. 20). *Distinct from the report CSV export and unaffected by the §1 exclusion*
- Account deletion in `ProfileTab.tsx` — **no deletion path exists anywhere today**
- Consent + lawful-basis fields; retention policy per data class
- Privacy policy and ToS drafted — content may be provisional, the plumbing must be real
- **F5 anonymization boundary specified at k ≥ 20** (D5)

**Exit criteria**
- Erasure removes the user's rows from `users_db` and `expenses_db`, verified by direct DB inspection, not by API response
- Export returns every personal-data class in User and Expense, checked against a written inventory
- Deletion is reachable in the UI and irreversible with confirmation
- A records-of-processing document names each data class, its lawful basis, and its retention period
- The erasure contract is documented well enough that M9 can implement three more handlers against it without reopening the design

---

### M9 — Service standardization: Report, Quest, Notification · **Size: L** · depends on: M8 contract
Completes the `REFACTOR.md` program. **Precedes all feature work** — F2 lands in Quest and F3/F4/F1 in Report, and building into un-standardized services means writing that code twice.

**Contents** — one service at a time (`REFACTOR.md` §2), each its own `feature/*` branch and PR:
- **Report:** `dashboard/ domain/ report/ web/` → `controller/ service/ repository/ entity/ dto/ mapper/ exception/`
- **Quest:** `goal/ quest/ domain/ web/` → the template, with `QuestJobs` → `scheduler/`
- **Notification:** finish the partial migration — `domain/` → `entity/` + `repository/`, add `dto/`, `mapper/`, `exception/`
- Each service gains its **erasure + export handler** against the M8 contract, in final structure
- **Service-layer Mockito tests** per `REFACTOR.md` §5 — Given-When-Then, including null, boundary, and exception paths. Current counts: report 1, quest 2, notification 0

**Exit criteria**
- All three match the user/expense package shape; no entity is serialised to a client anywhere
- No handler leaks a stack trace; all errors route through the centralized advice
- Every service has service-layer unit tests covering at least one null, one boundary, and one exception case per public service method
- Erasure verified across all five databases end to end (completing M8's coverage)
- **Behaviour unchanged** — existing endpoints return byte-identical responses for a seeded fixture set, before and after

---

### M10 — Hardening & design follow-ups · **Size: M** · **Complete**
Descoped from the original `DESIGN.md` M7; load testing moves to M17 (§2).

**Contents:** dashboard widget grid, user-arrangeable, **layout persisted server-side as JSON in User Service** per `DESIGN.md:438` (not localStorage, D9) · route/vendor code-splitting · security pass verifying NFR-4's "no financial data in URLs or logs" across all services · design follow-ups from `docs/design.md:181` — 44px touch targets, mobile search affordance, splash gated once per session · **D9 localStorage cleanup**

Shipped in two slices: the frontend + user-service work landed in PR #9 (`feature/m10-hardening-partial`), which deliberately deferred the cross-service log audit until quest/notification-service were standardized under M9. That gap closed once PR #7/#8 merged; the audit ran against final M9 code and is recorded in `docs/nfr4-log-audit.md`.

**Exit criteria**
- [x] Widget layout survives reload, is per-user, and appears in the M8 export inventory
- [x] Dashboard initial JS bundle measurably smaller than the pre-split baseline (record both numbers) — 445.06 kB → 360.09 kB raw (−19.1%), 132.22 kB → 115.62 kB gzip (−12.6%); signed-out −27.5%/−21.2%
- [x] Log audit produces a written finding list; every instance fixed or explicitly accepted in writing — `docs/nfr4-log-audit.md`, zero violations across all 5 services + gateway + frontend
- [x] All interactive controls ≥ 44×44px at ≤ 768px; splash fires once per session
- [x] **D9 conformance:** every key namespaced under `argali:`, listed in one registry module, capped with eviction, cleared on sign-out

**Excluded:** PDF export, CSV work, k6/1k-concurrent load test.

---

### M11 — F3 Ghost Flock · **Size: S** · depends on: M9 (Report standardized) · **Draft PR #11, pending review**
Cheapest of the five (D3) and the earliest proof of the positioning.

**Contents:** trailing-median counterfactual over existing `expense_projection`; ghost series on the dashboard endpoint; shadow-răboj extending the `raboj.tsx:67-80` guide-mark language; an in-product explanation of what the ghost *is* — an unexplained second line is noise.

**Exit criteria**
- [x] Real and ghost render together; the ghost is visually subordinate and never mistakable for the real ledger
- [x] A user with < 3 months of history gets a defined empty state, not a misleading flat ghost
- [x] Ghost values reconcile against a hand-computed median on a seeded fixture — `GhostFlockCalculatorTest`
- [x] **No new table and no new event consumer**
- [~] Dashboard p95 within 10% of the pre-M11 measurement — isolated computation cost measured (+0.18ms absolute); a full live HTTP-level before/after was not captured (shared dev stack was in concurrent use). Recommend confirming on a quiet stack before merge.

---

### M12 — F2 Tally Oath · **Size: M** · depends on: M9 (Quest standardized) · **Draft PR #12, pending review**
**Contents:** `oaths` entity in `quests_db`; create/list/cancel endpoints through the full controller→service→repository stack with DTOs and a mapper; reconciliation on the existing `expense.created` path; expiry sweep in `scheduler/`; `oath.*` events → Notification; a **dedicated oath affordance** in the UI (`AddSheet.tsx` is a *record* action, an oath is a *pledge* — conflating them undermines the mechanic); stamps via the existing `Stamp` component.

**Exit criteria**
- [x] Pledge → in-tolerance expense → `KEPT`, end to end — unit-verified via `OathServiceTest`; not yet exercised live on the compose stack
- [x] Pledge → over-tolerance expense → `SLIPPED`
- [x] Pledge → expiry with no match → `FORGONE`, with a stamp visually distinct from `KEPT` (`good` vs `muted` tone)
- [x] Re-delivering the same `expense.created` does not double-resolve an oath (idempotency test)
- [x] **Expense Service has zero diffs in this milestone** — verified via `git diff --stat`
- [x] Service-layer tests cover the matching rule's boundaries: exact tolerance edge, parent-category match, window edge

---

### M13 — F4 Shepherd's Weather · **Size: S–M** · depends on: M9 · **Draft PR #13, pending review**
**Contents:** composite in Report Service; `ambient.weather.updated` → STOMP; surface tint from a root-level state class; three band treatments in `index.css`; reduced-motion static fallback.

Report Service did not already consume `income.updated` as this section originally assumed — only Quest Service did. M13 added the missing projection, mirroring Quest Service's existing pattern.

**Exit criteria**
- [x] Band changes observable end to end by seeding burn rate across a threshold — unit-verified via `WeatherServiceTest`; not yet exercised live on the compose stack
- [x] **Hysteresis verified:** oscillating across a boundary produces at most one transition per dwell window
- [x] `prefers-reduced-motion` yields a static tint
- [x] Killing the computation leaves the app fully functional at the "clear" default
- [x] **Contrast ≥ 4.5:1 for all text in all three bands** — computed (WCAG relative luminance): clear ~16.1:1 / 8.8:1 (fg/muted-fg), gathering ~16.4:1 / 8.9:1, storm ~16.9:1 / 9.2:1

**Outstanding before merge:** none of M11/M12/M13 has had a human visual click-through in this environment (no browser automation available). Recommended before merging any of the three draft PRs.

---

### M14 — Developer/read API + webhooks · **Size: M** · depends on: M11, M12, M13 (D6)
**Contents:** personal access tokens (issue/revoke/scope, hashed at rest); read-only `/api/v1/public/*` for expenses, dashboard, ghost, oaths, weather; webhook subscriptions fed from existing RabbitMQ events with retry and dead-lettering; gateway rate limiting; endpoint docs.

**Exit criteria**
- A read-scoped token cannot write — verified per endpoint, not per token
- Revocation is immediate
- A webhook returning 500 retries with backoff then dead-letters without blocking the queue
- Tokens never logged, never in URLs (NFR-4)

---

### M15 — F1 Seasonal Transhumance · **Size: L** · depends on: M9, D1
Largest, and the only one with an external-dependency surface.

**Contents:** `macro` feature in Report Service (D1); pastoral seasonal calendar (internal data, no external dependency); INS CPI + ANRE tariff ingestion with cache, refresh cadence, and explicit stale-data behaviour; `macro.*` publication; Quest consumption for seasonal re-templating; seasonal adjustment to month-end projection.

**Exit criteria**
- Season transitions re-shape quest templates, verified by advancing a clock fixture
- Month-end projection differs measurably between flat and seasonally-adjusted months, and the difference is explainable
- **External source unavailable → last-known-good with a visible staleness indicator; never a blocked dashboard, never a silent zero**
- Ingestion failure raises an operator-visible signal
- Every macro-derived figure carries its source and as-of date in the UI

---

### M16 — F5 Collective Răboj · **Size: S now / L later** · gated (D5)
Specification lands in M8. Nothing further is buildable at one user. **On trigger** (user base above k ≥ 20): aggregation projection, `cohort.median.updated`, cohort UI, GDPR sign-off. **Exit criterion now:** a written anonymization boundary a reviewer can evaluate before any code exists.

---

### M17 — Public-launch gate · **not scheduled** · trigger: decision to launch publicly
Production hosting + TLS + backups + restore drill · **k6 load test to 1k concurrent and p95 tuning (NFR-2, NFR-8 — moved from the original M7)** · availability instrumentation (NFR-3) · subscription billing · error monitoring · support surface · privacy policy and ToS finalised from the M8 drafts · open-banking import once contracts are obtainable (D7) · F5 implementation if the k-floor is met · salary-calculator standalone funnel hook (`argali-planning-brief.md:105`).

---

## 5. Sequence

```
M8  Privacy foundation  (FIRST — contract + user/expense)
 │
 ▼
M9  Service standardization  (report → quest → notification, one PR each,
 │                            each carrying its erasure handler)
 ▼
M10 Hardening & design follow-ups
 │
 ├──▶ M11 F3 Ghost ───┐
 ├──▶ M12 F2 Oath ────┼──▶ M14 Dev API ──▶ M15 F1 Macro ──▶ M17 (gated)
 └──▶ M13 F4 Weather ─┘

M16 F5 — spec inside M8; implementation gated on user volume
```

M9 is the pivot: a hard prerequisite for M11/M12/M13, because those features land in the services it standardizes. M11/M12/M13 are mutually independent — order by appetite. This preserves the brief's F3 → F2 → F4 → F1 → F5 intent (`argali-planning-brief.md:100-103`).

---

## 6. Risks

| # | Risk | Likelihood | Mitigation |
|---|---|---|---|
| R1 | Feature work starts before M9 and gets rewritten by the refactor | **High if M9 slips** | M9 is a hard dependency on M11/M12/M13; enforced at PR review |
| R2 | M9 changes behaviour while restructuring | Medium — large diff | Byte-identical fixture responses as an exit criterion; one service per PR; baseline tests before altering logic (`REFACTOR.md` §2) |
| R3 | Privacy work slips behind features → 5-service × N-feature retrofit | Low now (M8 is first) | Erasure handlers are part of each service's own definition of done |
| R4 | F1's external sources (INS/ANRE) break silently, poisoning projections | High — no stable public contract | Last-known-good + visible staleness + operator signal as exit criteria; F1 late |
| R5 | Ghost (F3) reads as a budget line — the exact positioning failure the brief names | Medium | One rule not a picker; visually subordinate; explicit in-product explanation |
| R6 | Weather (F4) reads as a gimmick or fails contrast in the storm band | Medium | Hysteresis + dwell; per-band contrast as an exit criterion; never gates function |
| R7 | Oath (F2) becomes a nag and gets abandoned | Medium | No notification on pledge creation; user-initiated only |
| R8 | Personal-scale testing hides scale bugs | Medium | Deliberately accepted at this bar; M17 holds the load test — a known deferral, not an oversight |
| R9 | D3's read-time median degrades dashboard p95 | Low at personal scale | Measured as an M11 exit criterion; cache behind the same endpoint if breached |
| R10 | Planning or implementation runs against a stale local checkout | **High — has already happened once** | Verify against `origin/main` with `git ls-tree` / `git show`; fetch before trusting local `main` (D10) |

---

## 7. Decisions log

**Resolved 2026-08-06:** oath expiry → `FORGONE` as a distinct state · widget layout → server-side, not localStorage · PDF/CSV excluded in both directions · M8 first · code standard = `REFACTOR.md`, package-by-layer · branch flow `main` ← `dev` ← `feature/*`, agents never touch `main` · **F5 cohort floor confirmed at k ≥ 20 with suppression below** · this roadmap tracked at `docs/roadmap.md`.

**Open:** none blocking.

---

## 8. Verification approach

Per milestone, before it counts as done:
1. Exit criteria checked individually against **running behaviour**, not code inspection.
2. Event-driven paths verified end to end on the compose stack — publish, consume, observe the projection change.
3. Reviewer pass separate from the authoring pass; no self-approval. **The reviewer checks D8 conformance explicitly** — layering, DTO/entity boundary, mapper presence, exception idiom, test depth — since structural drift is invisible in a passing suite.
4. Changed files inspected for placeholders, `test.skip`, and unimplemented branches before completion is claimed.
5. Performance-sensitive milestones (M11, M13, M15) record before/after p95 on the dashboard read path.

**Frontend verification note:** browser screenshots time out in this environment; verify via page-text extraction. Run backend from compose and frontend from the Vite dev server on :5173 (proxies `/api` → :8080) — the Docker `frontend` image has been stale historically.

---

## 9. Deliberate exclusions

PDF export, CSV export changes, statement upload (owner-handled) · open-banking import (contract-blocked, D7) · k6/1k-concurrent load testing (M17 — the target does not exist at personal scale) · billing, production hosting, support (M17) · F5 implementation (scale-gated; spec only).

---

*Brief §0 constraints respected throughout: non-custodial — every feature records, projects, or analyses, none holds value; single Răboj world; event-driven with no synchronous cross-service joins; RON/bani. Brief §3–§4 priorities were treated as recommendations; deviations are stated with rationale in §3 and §4.*
