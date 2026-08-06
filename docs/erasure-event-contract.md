# Erasure Event Contract

**Status:** binding contract, M8. Fixed before implementation started — user-service and
expense-service build against this document, not the other way around.
**Purpose:** let M9 (report-service, quest-service, notification-service) add their own
erasure handlers without reopening this design. If you are implementing an M9 handler,
read "How M9 extends this" below and stop — you should not need anything else in this file.

---

## 1. Exchange and events

All erasure events publish to the existing RabbitMQ topic exchange **`pf.events`** (the same
exchange `expense.*` / `category.*` / `user.registered` already use — see
`expense-service/.../events/EventsConfig.java` for the binding pattern to mirror).

### `user.erasure.requested`

Published by **user-service**, once it has deleted its own data for the user (i.e. after the
`users_db` cascade in §3 has committed, not before).

```json
{
  "erasureRequestId": "UUID",
  "userId": "UUID",
  "occurredAt": "Instant"
}
```

One consumer per downstream service. Each consumer deletes that service's rows for `userId`
and acknowledges with `user.erasure.completed`.

### `user.erasure.completed`

Published by **each** downstream service once it has erased its own data for that user.

```json
{
  "erasureRequestId": "UUID",
  "userId": "UUID",
  "service": "string",
  "occurredAt": "Instant"
}
```

`service` is what lets user-service's ack listener tell one service's completion apart from
another's. At M8, only `"expense"` is emitted. **M9 adds `"report"`, `"quest"`, and
`"notification"` on this same routing key** — no new routing key, no schema change.

---

## 2. Tracking table — `erasure_requests` (`users_db`, owned by user-service)

| Column | Type | Notes |
|---|---|---|
| `id` | uuid PK | the `erasureRequestId` carried on every event |
| `user_id` | uuid, **no FK** | deliberately has no foreign key to `users` — it must survive the user row's own deletion, since it *is* the audit trail that the deletion happened |
| `requested_at` | timestamptz | set when the request is created |
| `expected_services` | text, comma-separated | e.g. `"user,expense"` — see §2.1 |
| `completed_services` | text, comma-separated | grows as `user.erasure.completed` events arrive |
| `status` | text | `'PENDING'` \| `'COMPLETED'` |
| `completed_at` | timestamptz, nullable | set the moment `status` flips to `COMPLETED` |

### 2.1 Why comma-separated text, not an array or `jsonb`

`expected_services` and `completed_services` are deliberately plain `text` with comma-separated
values, **not** a Postgres array or `jsonb` column. This is a small audit table with a short,
enumerable value set — the risk that matters here is ORM type-mapping friction (array/jsonb
columns need explicit Hibernate type handling that the rest of this codebase's entities don't
otherwise use), not storage efficiency or queryability. A plain string with an in-memory
split/contains check in the service layer is simpler and has one fewer failure mode.

### 2.2 `expected_services` / `completed_services` semantics

- `expected_services` is populated from the config property `privacy.erasure.expected-services`
  (default `user,expense`) **at the moment the request is created** — not read again later, so a
  mid-flight config change never rewrites an in-progress request's expectations.
- `completed_services` starts containing `"user"` — user-service marks *itself* complete before
  it ever publishes the request (see §4, step 1). It is not empty at creation.
- A request is `COMPLETED` when `completed_services` (as a set) is a **superset** of
  `expected_services` (as a set) — not equality, so a service completing more than once (a
  redelivered message, say) does not block completion, and order of arrival does not matter.
- Idempotency: an ack listener re-appending a `service` that's already in `completed_services`
  is a no-op — don't double-append, don't error.

### 2.3 Config property

```yaml
privacy:
  erasure:
    # Lists all five services from M8, not just the two with a handler today.
    # Report, Quest, and Notification hold user-scoped data now even though
    # their erasure handlers land in M9 — listing them means a request stays
    # honestly PENDING until every service that holds data has actually
    # erased it, instead of flipping to COMPLETED the moment User and
    # Expense finish while three services still hold the user's rows.
    expected-services: user,expense,report,quest,notification
```

Implementation deliberately deviates from a `user,expense`-only default for the reason above.
This means **M9 needs no config change** — §4 below is one step shorter than a strict
incremental default would require.

---

## 3. Worked example trace

1. User calls `DELETE /api/v1/me` with `{"confirm": true}`.
2. **user-service** deletes the user's row in `users_db.users`. This cascades via FK to
   `auth_identities`, `refresh_tokens`, `income_sources`, `savings_accounts`, and
   `consent_records` — all gone in the same transaction.
3. user-service inserts a row into `erasure_requests`: `expected_services = "user,expense"`,
   `completed_services = "user"` (self-complete — its own deletion already happened in step 2),
   `status = PENDING`.
4. user-service publishes `user.erasure.requested` with the new `erasureRequestId`.
5. **expense-service** consumes `user.erasure.requested`, deletes the user's rows in
   `categories`, `expenses`, and `recurring_expenses` in `expenses_db`.
6. expense-service publishes `user.erasure.completed` with `service = "expense"`.
7. user-service's ack listener consumes `user.erasure.completed`, appends `"expense"` to
   `completed_services` for that `erasureRequestId`.
8. `completed_services = "user,expense"` is **not yet** a superset of `expected_services =
   "user,expense,report,quest,notification"` (§2.3) — `status` stays `PENDING`. This is
   intentional: Report, Quest, and Notification still hold the user's rows at M8, so a
   `COMPLETED` status here would be false.

At M8, the trace ends at step 8 in `PENDING` — a request never reaches `COMPLETED` until M9's
three handlers exist and ack. At M9, steps 5–7 repeat once per newly-standardized service; the
request flips to `COMPLETED` the moment `completed_services` becomes a superset of
`expected_services`, i.e. once Report, Quest, and Notification have all acked.

---

## 4. How M9 extends this

No schema change, no new routing key, no contract renegotiation. `expected-services` (§2.3)
already lists all five services from M8, so **no config change is needed**. Per service:

1. Add a `@RabbitListener` on `user.erasure.requested` in your service — mirror
   expense-service's `UserErasureRequestedListener`.
2. In that listener, delete your service's rows for the given `userId`.
3. Publish `user.erasure.completed` with `service` set to your service's name
   (`"report"` / `"quest"` / `"notification"`).

That's the whole extension. The tracking table, the exchange, the routing keys, and the
superset-completion rule are all already in place and do not change.
