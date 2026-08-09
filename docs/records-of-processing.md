# Records of Processing

**Status:** M8 deliverable, extended in M9 — GDPR Art. 30-style record of the personal data
Argali processes, why, and for how long. User Service and Expense Service were standardized
and erasure/export-covered at M8; Report Service and Notification Service are added here as
part of M9's service standardization. The Quest Service entry lands as the rest of M9 completes.

Argali is **non-custodial**: it records and reflects money the user enters or later imports
read-only — it never holds, moves, or has access to actual funds. That constrains what
"financial data" means below: amounts and categories the user typed in, not account balances
or payment credentials.

---

## `users_db` (User Service)

| Data class | What it is | Lawful basis | Retention period | Erasure mechanism |
|---|---|---|---|---|
| `users` | Profile: email, password hash (or null for OAuth-only), display name, avatar URL, base currency | Contract necessity — an account cannot exist without it | Life of the account | `DELETE /api/v1/me` deletes the row directly |
| `auth_identities` | OAuth provider linkage (e.g. Google `sub` claim) for SSO login | Contract necessity — required to authenticate an OAuth account | Life of the account | Cascades via FK from `users` on account deletion |
| `refresh_tokens` | Hashed rotating session tokens | Contract necessity — required to keep a session alive without re-entering credentials | Short-lived by design: 30-day TTL per token, but a token is revoked (not just expired) immediately on rotation, on logout, or on reuse-detection (reuse of an already-rotated token revokes every session for that user — see `AuthService.refresh`). Revoked/expired rows are not actively purged before account deletion | Cascades via FK from `users` on account deletion |
| `income_sources` | User-entered income (amount, recurrence, dates) — feeds net worth and the salary calculator | Consent — an optional feature beyond the minimum account, entered at the user's discretion | Life of the account | Cascades via FK from `users` on account deletion |
| `savings_accounts` | User-entered savings pot balances — figures the user typed in, never a real account balance | Consent — optional, user-entered | Life of the account | Cascades via FK from `users` on account deletion |
| `dashboard_layouts` | The user's dashboard widget arrangement: which widgets sit in which column, and in what order, as a JSON document | Consent — a cosmetic preference, optional and entered at the user's discretion. Holds no financial figures, only widget ids | Life of the account | `GET /api/v1/me/data-export` returns it under `dashboardLayout`; cascades via FK from `users` on account deletion |
| `consent_records` | Record of what the user consented to and when (e.g. privacy policy / ToS version acceptance) | Legitimate interest — Argali needs to demonstrate what lawful basis applied and when, per GDPR Art. 5(2) accountability | Life of the account | Cascades via FK from `users` on account deletion |
| `erasure_requests` | Audit trail of erasure requests: which services acknowledged, when the request completed | Legal obligation / legitimate interest — evidence that an erasure request was honored | **90 days after `completed_at`.** Long enough to investigate a failed or partial erasure across services; short enough that the audit trail doesn't become a second copy of "who deleted their account and when" sitting around indefinitely. *(Proposed retention window — not yet enforced by a purge job; flagged for a follow-up milestone.)* | **Not** covered by the `users` FK cascade — deliberately has no FK to `users`, since it must survive the very deletion it's recording. It is not personal data *about the erased user* in the ordinary sense; it is a record that a request concerning that `user_id` occurred. Purged by age once a retention job exists |

## `expenses_db` (Expense Service)

| Data class | What it is | Lawful basis | Retention period | Erasure mechanism |
|---|---|---|---|---|
| `categories` | User-defined expense categories/subcategories, including the "mandatory" flag | Contract necessity — expense tracking cannot function without categorization | Life of the account | `GET /api/v1/expenses/export/me` for export; on `user.erasure.requested`, the listener deletes all rows for the user before publishing `user.erasure.completed` |
| `expenses` | Individual recorded expenses: amount (bani), category, date, note, source (`MANUAL` / future `BANK_IMPORT`) | Contract necessity — this is the core function of the app | Life of the account | Same listener, same event |
| `recurring_expenses` | Recurring expense templates (amount, category, cadence, next-run) that auto-post monthly occurrences | Contract necessity — an extension of core expense tracking | Life of the account | Same listener, same event |

## `reports_db` (Report Service)

Report Service holds no data authored by the user directly to it — `expense_projection` and
`category_projection` are event-fed shadow copies of Expense Service's own data (see
`docs/erasure-event-contract.md` and `expense_projection`'s own comment: "Upserted, never
authored here"), kept only so dashboard/report queries never call across services. `reports`
is the one table of genuinely user-authored content in this database.

| Data class | What it is | Lawful basis | Retention period | Erasure mechanism |
|---|---|---|---|---|
| `expense_projection` | Event-fed read model mirroring Expense Service's expenses (amount, category, date, note) for dashboard/report queries | Contract necessity — the same basis as the source data in `expenses_db`; this is a performance-motivated local copy, not a separate collection purpose | Life of the account, kept in sync via `expense.*` events | `GET /api/v1/reports/export/me` for export; on `user.erasure.requested`, `PrivacyService` deletes all rows for the user before publishing `user.erasure.completed` with `service=report` |
| `category_projection` | Event-fed shadow copy of Expense Service's categories, used to denormalize `category_path` onto `expense_projection` | Contract necessity — same basis as source data | Life of the account, kept in sync via `category.*` events | Same listener, same event |
| `reports` | User-authored saved reports: name, filter definition (date range, category IDs), cached last-run result | Consent — an optional feature beyond the minimum account, created at the user's discretion | Life of the account | Same listener, same event |
| `user_income` | Event-fed shadow copy of the user's monthly income (bani), used only to compute the F4 burn-rate composite | Contract necessity — same basis as the source data in `users_db`; a performance-motivated local copy | Life of the account, kept in sync via `income.updated` events | Same listener, same event; `PrivacyService` deletes the row on erasure and includes it in the export |
| `weather_state` | F4's committed ambient band (clear/gathering/storm) plus the in-flight hysteresis dwell clock (pending band, pending-since timestamp) | Contract necessity — derived operational state, not separately collected | Life of the account | Same listener, same event; `PrivacyService` deletes the row on erasure and includes it in the export |

## `quests_db` (Quest Service)

| Data class | What it is | Lawful basis | Retention period | Erasure mechanism |
|---|---|---|---|---|
| `oaths` | User-authored spending pledges (F2 Tally Oath): the category pledged against, the amount in bani, the deadline, and the outcome (`KEPT` / `SLIPPED` / `FORGONE`) plus the expense that settled it | Consent — an optional self-commitment feature beyond the minimum account, created at the user's discretion | Life of the account | `GET /api/v1/quests/export/me` for export; on `user.erasure.requested`, `PrivacyService` deletes all rows for the user before publishing `user.erasure.completed` with `service=quest` |

An oath records an *intention* rather than a transaction, and is deliberately never written to
`expenses_db`: no money changed hands when it was made. `matched_expense_id` points at a row in
this service's local `expense_projection`, so erasing the user clears both sides together.

## Browser storage (Argali web client)

Not a database, but it is still personal data held on the user's device, so it is
inventoried on the same terms. Everything here is a **regenerable cache** — D9 requires
that losing any of it costs a convenience, never user data — and every key is declared in
one place, `frontend/src/lib/storage.ts`. That registry is what makes this table
verifiable: no module calls `localStorage`/`sessionStorage` directly, so the list below
is exhaustive by construction rather than by memory.

| Key | What it is | Store | Cap & eviction | Cleared when |
|---|---|---|---|---|
| `argali:category-usage:<userId>` | Per-category use counts and the last category used, so the add-expense sheet can offer one-tap chips and preselect sensibly | `localStorage` | **40 categories max**; least-used evicted first, the last-used category always kept | Sign-out and account deletion |
| `argali:splash-seen` | Flag that the boot animation already played this tab session | `sessionStorage` | Scalar flag; the browser drops it when the tab session ends | Sign-out, account deletion, and tab close |

No access token, refresh token, email, or financial figure is written to browser storage:
the access token is held in memory only and the refresh token is an `HttpOnly` cookie the
page cannot read. `clearArgaliStorage()` runs on **both** sign-out and account deletion,
and is prefix-scoped so it can never delete another application's keys on a shared origin.

---

## Notes

- **Retention "life of the account"** means: retained for as long as the account exists, with
  no independent expiry — the only removal path is the user's own account deletion
  (`DELETE /api/v1/me`), which is immediate and irreversible once confirmed. There is currently
  exactly one account (the owner's), so every retention period above is, in practice, "until the
  owner deletes it."
- **Why not "legitimate interest" for core tracking data:** the app's entire function is
  recording and reporting the user's own entries back to them — that's a contractual necessity,
  not a business interest balanced against the user's, so contract necessity is the honest basis
  rather than reaching for legitimate interest as a default.
- **Notification history has no age-based purge.** `notifications` grows for the life of the
  account and is only ever removed wholesale by erasure. The list endpoint caps its *response*
  at the 50 most recent, which hides the growth without bounding it — so the stored history is
  older and larger than anything the UI shows. Called out here rather than quietly fixed: a
  retention window for generated notifications is a policy decision, and it belongs with the
  `erasure_requests` 90-day window as a follow-up milestone, not inside a standardization PR.
- **Quest Service** mostly processes derived data from the classes above (quest progress,
  and the `expense_projection` / `category_projection` copies described for `reports_db`);
  those need no separate lawful basis. `oaths` is the exception and is documented below.
  **Report Service** and **Notification Service** are documented above.
