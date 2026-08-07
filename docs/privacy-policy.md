# Argali Privacy Policy

> **Provisional draft prepared ahead of public launch; content will be reviewed before Argali
> is offered to users beyond its owner.** Argali is currently a personal-use application with a
> single user (the owner). This document describes the data-handling plumbing that already
> exists — export and erasure are live, self-service, and real — so the policy is accurate
> today rather than aspirational for a future launch.

**Last updated:** 2026-08-06 · **Applies to:** the Argali web application (RON, Romania-focused)

---

## 1. Who we are

Argali is a personal-finance tracking application for the Romanian market. This policy is
written from the perspective of Argali's operator (contact point below) as the entity
responsible for the data described here.

## 2. Argali is non-custodial — it never touches your money

Argali **records and reflects** your finances. It never holds, deposits, withdraws, sweeps, or
transfers funds, and it has no ability to move money on your behalf. Every figure you see —
expenses, income, savings pots, net worth — is either an amount **you typed in**, or, in a
future version, an amount imported **read-only** from a linked account. Argali is a ledger, not
a bank, wallet, or payment institution, and this policy should be read with that in mind: the
"financial data" below means recorded entries, not account access or transaction authority.

## 3. What we collect, why, and for how long

This section mirrors [`records-of-processing.md`](records-of-processing.md), which is the
authoritative, table-form version of this list. In summary:

| Data | Why we collect it | Lawful basis | How long we keep it |
|---|---|---|---|
| Email, password hash or OAuth link, display name, avatar | To create and secure your account | Necessary to provide the service (contract) | For as long as your account exists |
| Session tokens (refresh tokens) | To keep you signed in without re-entering your password | Necessary to provide the service (contract) | Short-lived — revoked on rotation, logout, or suspicious reuse; expire automatically at most 30 days after issue |
| Income sources, savings pot figures | To power net worth and the salary calculator, if you choose to enter them | Your consent (these are optional) | For as long as your account exists |
| Expenses, categories, recurring expense templates | The core function of the app — recording and reporting your spending | Necessary to provide the service (contract) | For as long as your account exists |
| Consent records | To keep an honest record of what you agreed to and when | Our legitimate interest in demonstrating compliance | For as long as your account exists |
| Erasure request audit trail | To prove an erasure request was received and completed | Legal obligation / legitimate interest | 90 days after the request completes, then eligible for deletion |

We do not collect more than the table above. We do not have access to your actual bank
balances, card numbers, or payment credentials — Argali does not ask for them.

## 4. No ads, no data sales

Argali does not run advertising and does not sell, rent, or otherwise share your data with
third parties for their own marketing or advertising purposes. If Argali is ever
subscription-funded at public launch, that funding model — not your data — pays for the
service. This is a deliberate positioning choice, not a placeholder promise.

## 5. Your rights

You have the right to access, export, and erase your data. Today, these rights are **fully
self-service** — there is no manual request process to wait on, because the plumbing described
below *is* the fulfillment mechanism:

- **Access / export your data:** each service exports the data it holds, as JSON:
  - `GET /api/v1/me/data-export` — profile, income sources, savings accounts, consent records
  - `GET /api/v1/expenses/export/me` — categories, expenses, recurring expense templates
  - `GET /api/v1/reports/export/me` — saved reports and the expense/category read models
  - `GET /api/v1/notifications/export/me` — your notification history

  Together these cover every personal data class Argali holds about you **except Quest
  Service's**, whose export endpoint is the last piece of M9 still outstanding. See
  [`records-of-processing.md`](records-of-processing.md) for the per-database inventory.
- **Erasure ("right to be forgotten"):** deleting your account (`DELETE /api/v1/me`, with
  explicit confirmation) permanently removes your data from every service that holds it. This
  is immediate and irreversible once confirmed — see
  [`erasure-event-contract.md`](erasure-event-contract.md) for exactly how it propagates across
  services.
- **Rectification:** you can edit your profile, income, savings, and expense data directly in
  the app at any time.

Because Argali currently has one user, these rights exist as working software, not as a policy
promise waiting on a support queue.

## 6. Where your data lives

Data is stored in PostgreSQL databases operated by Argali, one database per backend service
(no service reads another service's database directly). Data does not leave these databases
except as part of the export and erasure mechanisms described above.

## 7. Romania / RON-specific note

Argali is built for the Romanian market: amounts are recorded in RON, and the salary calculator
uses versioned Romanian tax rules (CAS, CASS, income tax). This policy is intended to align with
Romanian and EU data protection law (GDPR); the provisional note at the top of this document
applies to that alignment as much as to the rest of the content.

## 8. Future: cohort features

Argali's roadmap includes an optional, strictly anonymized cohort-comparison feature (see
[`f5-anonymization-spec.md`](f5-anonymization-spec.md)). It does not exist yet and will not be
built until a k-anonymity threshold (k ≥ 20 distinct users per cohort) can genuinely be met —
which requires a user base larger than the one that exists today. This policy will be updated
before that feature ships.

## 9. Changes to this policy

Because Argali is not yet publicly available, this policy will change as the product moves
toward public launch. The "provisional draft" notice at the top will be removed once the policy
has been reviewed for that launch.

## 10. Contact

Questions about this policy or your data: **[contact email placeholder — to be filled in before
public launch]**.
