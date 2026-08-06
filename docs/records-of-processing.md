# Records of Processing

**Status:** M8 deliverable — GDPR Art. 30-style record of the personal data Argali processes,
why, and for how long. Scope is **User Service and Expense Service only**, the two services
standardized and erasure/export-covered at this milestone. Report, Quest, and Notification
Service entries land with M9 (see closing note).

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
| `consent_records` | Record of what the user consented to and when (e.g. privacy policy / ToS version acceptance) | Legitimate interest — Argali needs to demonstrate what lawful basis applied and when, per GDPR Art. 5(2) accountability | Life of the account | Cascades via FK from `users` on account deletion |
| `erasure_requests` | Audit trail of erasure requests: which services acknowledged, when the request completed | Legal obligation / legitimate interest — evidence that an erasure request was honored | **90 days after `completed_at`.** Long enough to investigate a failed or partial erasure across services; short enough that the audit trail doesn't become a second copy of "who deleted their account and when" sitting around indefinitely. *(Proposed retention window — not yet enforced by a purge job; flagged for a follow-up milestone.)* | **Not** covered by the `users` FK cascade — deliberately has no FK to `users`, since it must survive the very deletion it's recording. It is not personal data *about the erased user* in the ordinary sense; it is a record that a request concerning that `user_id` occurred. Purged by age once a retention job exists |

## `expenses_db` (Expense Service)

| Data class | What it is | Lawful basis | Retention period | Erasure mechanism |
|---|---|---|---|---|
| `categories` | User-defined expense categories/subcategories, including the "mandatory" flag | Contract necessity — expense tracking cannot function without categorization | Life of the account | `GET /api/v1/expenses/export/me` for export; on `user.erasure.requested`, the listener deletes all rows for the user before publishing `user.erasure.completed` |
| `expenses` | Individual recorded expenses: amount (bani), category, date, note, source (`MANUAL` / future `BANK_IMPORT`) | Contract necessity — this is the core function of the app | Life of the account | Same listener, same event |
| `recurring_expenses` | Recurring expense templates (amount, category, cadence, next-run) that auto-post monthly occurrences | Contract necessity — an extension of core expense tracking | Life of the account | Same listener, same event |

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
- **Report Service, Quest Service, and Notification Service** process derived/projected data
  from the classes above (event-fed projections, quest progress, notification history); their
  own records-of-processing entries land with M9's erasure handlers.
