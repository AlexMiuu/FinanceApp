# Argali Terms of Service

> **Provisional draft prepared ahead of public launch; content will be reviewed before Argali
> is offered to users beyond its owner.** Argali currently operates at a personal-use bar with a
> single user (the owner). These terms are written in the shape they'll need at public launch,
> but describe today's actual functionality rather than a hypothetical future feature set.

**Last updated:** 2026-08-06 · **Applies to:** the Argali web application (RON, Romania-focused)

---

## 1. What Argali is

Argali is a personal-finance **tracking** application: it helps you record expenses and income,
understand spending through reports and dashboards, and build habits through goals, streaks,
and quests. See [`privacy-policy.md`](privacy-policy.md) for how your data is handled.

## 2. Argali is non-custodial — read this before anything else

**Argali never holds, moves, or has access to your money.** It is not a bank, e-money
institution, or payment service, and it does not seek to be. Every balance, expense, income
figure, or savings pot you see in Argali is either:

- an amount **you entered manually**, or
- in a future version, an amount **imported read-only** from a linked account, for display and
  tracking only.

Argali cannot initiate a payment, move funds between accounts, or take any action that affects
your actual money. If you are looking for a tool that moves or holds funds on your behalf,
Argali is not that tool, by design.

## 3. Eligibility and current availability

Argali is not currently offered to the public. It operates at a personal-use bar for its owner.
References in these terms to "you," "your account," or "users" describe the intended shape of
the relationship for a future public launch; today, that relationship is between Argali and its
owner. This section will be updated when Argali opens beyond that.

## 4. Your account

- You are responsible for keeping your login credentials secure.
- You may delete your account at any time via `DELETE /api/v1/me`, with explicit confirmation.
  Deletion is immediate and irreversible — see [`erasure-event-contract.md`](erasure-event-contract.md)
  for how it propagates across Argali's services.
- You may export a machine-readable copy of your data at any time via the endpoints listed in
  [`privacy-policy.md`](privacy-policy.md) §5.

## 5. Acceptable use

You agree not to:

- use Argali to record or process data you don't have the right to record (e.g. someone else's
  financial information without their consent);
- attempt to access another user's data, disrupt the service, or circumvent its security;
- use Argali for any unlawful purpose.

## 6. No financial advice, no custody, no guarantees

Argali is a tracking and habit tool, not a financial adviser, and nothing in the app — reports,
projections, quests, the salary calculator, or cohort comparisons where applicable — constitutes
financial, tax, or legal advice. Figures like the month-end projection or the salary calculator
output are estimates based on the data you provide and general Romanian tax rules; verify
anything consequential independently.

Because Argali never holds or moves funds, it bears no custodial responsibility for your money.
Any error in what Argali *records* (a mis-entered expense, a stale category) is something you
can correct in the app; it never reflects an error in an actual transaction, because Argali
never executes one.

## 7. Pricing and data

Argali does not run advertising and does not sell your data. If Argali becomes a paid product at
public launch, pricing will be disclosed clearly and in advance — no surprise charges, no
undisclosed data monetization funding a "free" tier. See
[`privacy-policy.md`](privacy-policy.md) §4.

## 8. Termination

You may stop using Argali and delete your account at any time (§4). Argali's operator may
suspend or terminate access for violation of §5 (acceptable use) or as required by law. Because
Argali is not yet public, termination provisions beyond account self-deletion will be expanded
before public launch.

## 9. Availability

Argali is currently operated at a personal-use scale without a formal uptime commitment. Service
availability guarantees (if any) will be defined before public launch, alongside the production
hardening work tracked in the delivery roadmap.

## 10. Governing law

These terms are intended to be governed by the laws of Romania and applicable European Union
law, consistent with Argali's RON/Romania-focused positioning. This will be confirmed as part of
the pre-public-launch review referenced at the top of this document.

## 11. Changes to these terms

These terms will change as Argali moves toward public launch. The "provisional draft" notice at
the top will be removed once the terms have been reviewed for that launch.

## 12. Contact

Questions about these terms: **[contact email placeholder — to be filled in before public
launch]**.
