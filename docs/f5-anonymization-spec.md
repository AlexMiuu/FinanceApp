# F5 Anonymization Spec — "The Collective Răboj"

**Status:** specification only, M8. No implementation exists and none is planned until the
trigger condition in §5 is met (`docs/roadmap.md` D5, M16). This document exists so the
anonymization boundary is evaluable by a reviewer **before** any cohort code is written — the
boundary constrains event schemas and the lawful-basis record, so it needs to be settled ahead
of, not alongside, implementation.

F5 (`argali-planning-brief.md` §3): Report Service publishes anonymized cohort aggregates (e.g.
"this heating season, median utility spend for a cohort") so a user sees their notches against
the quiet median of the flock — a normalization signal, not a leaderboard.

---

## 1. What a "cohort" is (proposal — open to revision)

**Proposed dimensions:** `region × spending-category-mix × month`.

- **Region** — one of Romania's official development regions (e.g. București-Ilfov, Nord-Vest,
  Centru), not the finer-grained județ. A județ-level cohort is far more likely to fall below
  the k-anonymity floor at any plausible early user count; the development-region grain trades
  precision for a realistic chance of ever publishing.
- **Spending-category-mix** — a coarse bucket derived from the user's top-level mandatory
  category spend distribution (the same categories the quest-tailoring logic already uses), not
  free-text category names. The intent is a handful of discrete mix buckets (e.g.
  "housing-dominant," "food-dominant"), not a combinatorial explosion of category
  permutations that would fragment every cohort down to single-digit membership.
- **Month** — calendar month of the spend being aggregated, potentially season-anchored later to
  align with F1's pastoral calendar if the two features end up sharing a cadence.

This is a **starting proposal, not a locked decision** — it is deliberately the first thing a
GDPR/anonymization review should push on before F5 is implemented (D5, M16).

## 2. k-anonymity threshold (confirmed)

**k ≥ 20** distinct users per cohort-period, before any figure is published. This number is
fixed (`docs/roadmap.md` D5); the cohort *dimensions* above are not.

## 3. Suppression rule

**Suppress the entire cohort figure below the floor. Do not round or fuzz it.**

A cohort-period with fewer than 20 distinct contributing users publishes **nothing** for that
cohort-period — no rounded approximation, no noise-added estimate, no partial figure. Rounding
or fuzzing a small-`n` figure can still leak information via repeated queries (an attacker who
can query the same near-threshold cohort repeatedly, or across adjacent time windows, can
triangulate a fuzzed value back toward the true one, or infer membership from small shifts
between queries). Outright suppression doesn't have that failure mode — there is nothing to
triangulate against.

## 4. What "distinct users" means, operationally

Count **distinct contributing `user_id`s** for the cohort-period, not events and not rows.

- The count is `COUNT(DISTINCT user_id)` over the rows/events contributing to that
  cohort-period — never `COUNT(*)`.
- A single user who logs fifty expenses in a cohort-period contributes **one** to the count, not
  fifty. Event volume must never be conflated with population size — a cohort could otherwise
  clear k=20 on paper while actually reflecting three highly active users, defeating the purpose
  of the threshold entirely.
- The count is re-derived per cohort-period at aggregation time; it is not a running total
  carried over from a prior period.

## 5. Why this can't ship at the current launch bar

Argali is currently at a personal-use launch bar with exactly one user — the owner. Any cohort,
under any dimensioning, has at most one distinct contributing `user_id`, which is always below
the k ≥ 20 floor by construction, so every cohort-period would suppress unconditionally and the
feature could never demonstrate value. Worse, attempting to build or test the aggregation
pipeline against real single-user data before there's a real cohort would mean the "aggregate"
computed during development *is* the individual — the one case anonymization exists to prevent.
F5 stays spec-only until the roadmap's scale trigger (real user volume, D5/M16) makes a genuine
cohort possible.

## 6. Reviewer checklist

Before F5 implementation begins, a reviewer should confirm:

- ☐ Cohort dimensions don't allow re-identification via a single outlier user (e.g. a cohort
  bucket so narrow that one unusually high or low spender is identifiable by elimination).
- ☐ Suppression is enforced at the query/aggregation layer, not just hidden in the UI — a client
  that can query the aggregation endpoint directly must never receive a below-floor figure.
- ☐ k is re-validated **per cohort-period**, not just once at feature launch — a cohort that
  cleared the floor last month but lost contributors this month must suppress this month.
- ☐ The "distinct users" count is verified to be `COUNT(DISTINCT user_id)`, not event count, in
  the actual query — not just in this document.
- ☐ Region and category-mix bucket boundaries are reviewed against real (or realistically
  simulated) distribution data before launch, since the proposal in §1 is unvalidated.
