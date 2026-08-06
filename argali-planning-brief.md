# Argali — Strategy → Planning Handoff Brief

**Product:** Argali — a personal-finance **tracker** web app for the Romanian market (RON).
**Purpose of this doc:** hand off strategic direction to a planning agent for decomposition into an implementation plan. This is the *what and why*; the planning agent owns the *how and when*. Authoritative data models and API surface live in the repo-root `DESIGN.md`; the product/design-system summary lives in `design.md`. This brief adds competitive positioning and a proposed feature slate on top of those.

---

## 0. Non-negotiable constraints (read first)

These bound every plan the planning agent produces. Do not design around them; design *within* them.

1. **Argali is a tracker, never a custodian.** Argali **records and reflects** money; it never holds, deposits, withdraws, sweeps, or transfers funds.
   - Expenses/income are *recorded entries*. Savings pots and net worth are *user-entered figures* (net worth = sum of savings in v1).
   - Deferred v2 bank-linking is **read-only import** only (`source` + `external_id` for idempotent transaction import) — never payment initiation.
   - **Forbidden mechanics:** real Round-Up sweeping into an account, fund-holding Vaults/Spaces, automated transfers, save-now-buy-later custody, any e-money balance. Competitor equivalents (Revolut Vaults, Salt Bank Spaces, George/BT Round Up, Plum auto-save) collapse into **tracked** equivalents at most — the user logs a pot; Argali never holds it.
   - **This is a positioning asset, not a limitation:** staying non-custodial keeps Argali out of e-money / payment-institution licensing and heavier PSD2 payment obligations, and earns a "we never touch your money, we just help you read it" trust line the banks and neobanks structurally cannot make.

2. **One opinionated world — the "Răboj" shepherd's ledger.** Dark-mode only. Tanned-hide ground (`#14100d`), kraft-paper cards (`#241c15`), struck-brass accent (`#c79a5b`), sage income (`#9cb37a`), terracotta spend (`#c96a4e`), inked stamps, ruled ledger tape with serial numbers, hand-drawn SVG icons (no emoji). **No light mode, no user theme customization** — the constraint is the brand. Type: Zilla Slab (stamped headers), Inter (UI), Spline Sans Mono (money/serials). Status is never color-only (notch = met, cross = over).

3. **Architecture is event-driven microservices — plan with the grain.** Java 21 / Spring Boot 3 services (User, Expense, Report, Quest) behind Spring Cloud Gateway; **RabbitMQ** async events between services; **database-per-service** (no cross-service DB reads); **WebSocket/STOMP** for live push; PostgreSQL; money as `BIGINT` in **bani** (never floats); UUIDv7 keys; `timestamptz`. Report/Quest keep **event-fed local projections** (eventually consistent, sub-second). Do **not** plan synchronous cross-service joins to fake one global instant view — build projections off the event stream.

4. **Romania-native by default.** RON-first, bani precision, Romanian-language UI, native RO tax/salary intelligence (CAS 25% / CASS 10% / income tax 10%, versioned config). RON-first is table stakes, not a differentiator on its own — the differentiators are in §2 and §3.

---

## 1. Product context (one paragraph)

Argali helps a Romanian user **track expenses, understand spending through reports/dashboards, and improve habits through tailored goals, quests, and challenges** — framed as tending a shepherd's account book rather than operating a fintech dashboard. Baseline features already specced: RON expense/income tracking with category "mandatory" flags and recurring monthly posting; reports & dashboards (KPI tiles, spend donut, trend line, month-end projection, saved filter sets, CSV/PDF export); auto-evaluated gamified Quests & Challenges from history + mandatory-expense ratios; a goal calendar using the răboj notch-tally streak; and a versioned RO net↔gross salary calculator.

---

## 2. Competitive positioning (condensed)

Full benchmark analysis exists separately (YNAB/Lunch Money global benchmark + Romanian/EU-native landscape deep-dive). Planning-relevant conclusions:

**Position map — who already owns what in RO/EU:**
- **Default everyday PFM (owned):** George (BCR), BT Pay, ING Home'Bank — free, pre-installed, RON-native, good categorization/budgets/analytics.
- **Cross-border super-app + light gamification (owned):** Revolut (largest RO retail base).
- **Digital-native neobank (owned):** Salt Bank.
- **Free RO-language aggregating tracker + education (owned):** Money in Motion (MiM) — **closest direct competitor**; open-banking to all RO banks + Revolut via Finqware, 100k users in ~10 months, but **no gamification and no salary calculator**.
- **Manual privacy-first RO ledger (owned):** CashControl.
- **Multi-currency EU power-user tracker (owned):** Wallet by BudgetBakers, Spendee.
- **Gamified/habit-based RON-native tracking + cultural identity + salary intelligence (UN-OWNED):** Argali's target.

**Adopt** (proven, compatible with the constraints above):
- Method-as-product, recast as *ritual* — treat the răboj tally with the seriousness YNAB gives its method; it's the retention spine, taught via quests.
- A developer/read API + webhook/event feed — cheap moat, natural fit for the event architecture, hard for monoliths to copy.
- Manual entry as a **first-class ritual**, not a fallback — the ledger-tape UI makes hand-entry the deliberate act the brand promises; also the honest answer to imperfect RO bank coverage.
- Radical pricing transparency + clean data posture (subscription-funded, no ads, no data sales) — brand-consistency requirement.

**Avoid:**
- Envelope / zero-based "give every dollar a job" as the core loop (guilt machinery; already ruled out; wrong psychology for a streak/accumulation app).
- Bank aggregation as a launch dependency (coverage is gated behind Finqware/Salt Edge contracts; keep it v2, read-only).
- USD-premium / single-global-budget economics.
- Aesthetic neutrality / theme-switching / AI auto-categorization as the hero.
- Anything custodial (see §0.1).

**White space (defensible):** the *combination* of habit/streak/quest gamification + native RO tax/salary intelligence + a deliberately non-corporate tactile folk identity + non-custodial trust. No single feature is the moat — the bundle is.

---

## 3. Proposed feature slate (5)

Each is tactile, psychological, or macro-aware; each maps to a specific event-stack strength; none is envelope budgeting, a 50/30/20 tool, or a portfolio tracker; **none is custodial** — all are record / project / analyze only. Priorities are a starting recommendation, not a mandate.

### F1 — Seasonal Transhumance *(macro-aware; cross-service event choreography)* — Priority: Medium
**Concept:** the ledger follows the pastoral calendar. A new **Macro Service** publishes seasonal + RO macro events (INS CPI prints, ANRE energy-tariff changes, known winter-cost seasonality). Quest Service consumes them and re-shapes quests by season (autumn `coborâtul oilor` → "build the winter reserve" quests); Report Service adjusts month-end **projections** for seasonal cost inflation instead of assuming flat months.
**Event flow:** `MacroService → [macro.season.changed, macro.cpi.updated] → RabbitMQ →` consumed by Quest (re-templating) and Report (re-projection).
**Non-custodial check:** reshapes quests and projections only; never a balance.
**Dependencies / open items:** external data source + refresh cadence for INS/ANRE; a `MacroService` (new). Ambitious — sequence after core quests are stable.

### F2 — The Tally Oath (pre-commitment) *(psychological; event reconciliation across a time window)* — Priority: High
**Concept:** before a discretionary purchase, the user carves an *intention notch* (a pledge). When the real expense lands — or the window closes without one — the pipeline reconciles intent vs. reality and Quest Service stamps **Kept** or **Slipped**. This is a Ulysses/pre-commitment contract rendered as a physical oath, not a nag.
**Event flow:** `Expense (intent recorded) → [expense.intent.created] →` Quest opens a reconciliation window; on `expense.created` (or window-expiry timer) → `[oath.kept | oath.slipped]` → stamp projection.
**Non-custodial check:** intent and expense are both *records*; no money reserved or moved.
**Dependencies / open items:** define the reconciliation window semantics + matching rule (amount/category/merchant tolerance); timer/expiry mechanism (scheduler or delayed message).

### F3 — The Ghost Flock (counterfactual shadow ledger) *(psychological; dual projection off one stream)* — Priority: High
**Concept:** Report Service runs a **second projection off the same expense stream** under a counterfactual rule (e.g., "if discretionary had held flat vs. last season"). Dashboard shows the real inked răboj against a faint **ghost răboj** — the flock you let wander. Reuses the existing "ghost guides — room to grow" design element and gives it meaning. Weaponizes loss aversion warmly (sheep on the wrong hillside, not "over budget").
**Event flow:** same `expense.*` stream, second consumer/projection in Report Service; no new source data.
**Non-custodial check:** pure projection; no balances.
**Dependencies / open items:** choose the counterfactual baseline rule(s) and how the user selects/understands them; near-zero marginal infra cost — good early win.

### F4 — The Shepherd's Weather (ambient macro-mood) *(tactile / calm-tech; continuous STOMP push)* — Priority: Medium
**Concept:** Report Service computes a slow composite of trailing burn-rate vs. income and pushes it over STOMP as **weather, not a number**. The ground (tanned-hide surface, grain, brass warmth) shifts almost imperceptibly: clear (calm/warm) → gathering cloud (cooler, grain thickens) → storm (paper darkens). The user *feels* state before reading it; collapses the anxious balance-check loop into a peripheral glance.
**Event flow:** `Report → [ambient.weather.updated] → STOMP → client surface tint`.
**Non-custodial check:** derived signal only.
**Dependencies / open items:** define the composite + thresholds; must degrade gracefully under `prefers-reduced-motion` (freeze to a static tint); keep it subtle enough not to read as gimmick.

### F5 — The Collective Răboj (anonymized cohorts) *(macro-aware + social proof; privacy-safe aggregate projection; scale moat)* — Priority: Low → Medium (scale-gated)
**Concept:** Report Service publishes **anonymized cohort aggregates** as events (e.g., "Cluj software workers, this heating season, median utility spend"). The user sees their notches against the quiet **median of the flock** — *not* a leaderboard (shame is the wrong lever); a normalization signal. Because it's event-driven and database-per-service, the aggregation is a separate projection that **never reads another user's rows** — privacy-preserving by construction. Compounds with scale, so single-user indie tools can't match it.
**Event flow:** per-user `expense.*` → anonymized aggregation projection → `[cohort.median.updated]` (no PII, k-anonymity threshold before publishing).
**Non-custodial check:** aggregate analytics only.
**Dependencies / open items:** cohort definition + **minimum cohort size (k-anonymity)** before any figure is shown; needs user base to be useful — gate behind scale; GDPR review of the anonymization boundary.

---

## 4. Recommended sequencing (starting point)

1. **F3 Ghost Flock** and **F2 Tally Oath** first — highest psychological payoff, lowest new-infra cost (F3 reuses the existing stream + an existing visual element; F2 needs only reconciliation + a timer). These also prove the "quest/ritual, not budgeting" positioning early.
2. **F4 Shepherd's Weather** next — differentiating, moderate effort, mostly a Report projection + a client surface treatment.
3. **F1 Seasonal Transhumance** once the Macro Service is justified — highest external-integration cost.
4. **F5 Collective Răboj** last — value is scale-gated; ship when the user base can produce honest medians above the k-anonymity floor.

**Success framing for planning:** if early users describe Argali primarily as "a budgeting app," positioning has failed — they should describe it as a habit/quest experience. Consider making the RO net↔gross **salary calculator** a standalone shareable hook that funnels to signup (it's the single clearest feature no RO competitor bundles).

---

## 5. Open questions for the planning agent

- **F2:** exact intent↔expense matching rule and reconciliation-window/expiry mechanism (scheduler vs. delayed message)?
- **F1/F5:** which external data sources (INS CPI, ANRE tariffs) and cohort taxonomy, and their refresh cadence + failure handling?
- **F5:** the k-anonymity threshold and GDPR sign-off on the anonymization boundary before any cohort figure is exposed.
- **F4:** the burn-rate composite definition + thresholds, and the reduced-motion fallback.
- **Cross-cutting:** whether the Macro Service (F1) is a new bounded context or an extension of Report; and where the developer/read API (from §2 Adopt) sits in the roadmap.

---

## 6. Triggers that should change the plan

- **MiM adds gamification or a salary calculator** → white space narrows; accelerate and lean on cultural identity + design quality (hardest to copy).
- **George / BT Pay / Revolut ship streaks/quests + salary tools** → habit-mechanic edge erodes; pivot emphasis to cross-bank read-only aggregation + tactile brand.
- **A funded EU gamified app (e.g., Monkee, Plum) launches a genuine RON/RO-language product** → revisit differentiation immediately.

---

*Prepared as a strategy→implementation handoff. Constraints in §0 are binding; §3 priorities and §4 sequencing are recommendations for the planning agent to schedule and scope.*
