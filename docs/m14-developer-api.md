# M14 developer API — personal access tokens, webhooks, public read API

Read-only, non-interactive access to your own Argali data: mint a token, read from
`/api/v1/public/*`, or subscribe a webhook to be pushed domain events as they happen.
Everything here sits behind the normal signed-in session (`/api/v1/me/*`) or a personal
access token (`/api/v1/public/*`) — there is no separate developer account.

## Personal access tokens

Minted, listed, and revoked under your existing session (`/api/v1/me/tokens`, JWT-auth'd).
A token can never mint another token.

| Method | Path | Notes |
|---|---|---|
| `POST` | `/api/v1/me/tokens` | Body: `{"name": "..."}`. Response includes the raw token — the only time it is ever readable. |
| `GET` | `/api/v1/me/tokens` | Lists your tokens (id, name, created/last-used, no raw value). |
| `DELETE` | `/api/v1/me/tokens/{id}` | Revokes immediately — takes effect on the very next request, no propagation delay. |

Every token issued today carries scope `read` only — read-scoped by construction, not by a
runtime check that a bug could bypass: no route under `/api/v1/public/**` exposes a write
verb.

The raw token is `pat_<32 bytes of random, base64url>`. Only its SHA-256 digest is stored;
the raw value is never persisted anywhere and never appears in a log line — events are
logged by token id.

```bash
curl -X POST https://argali.example/api/v1/me/tokens \
  -H "Authorization: Bearer $SESSION_JWT" \
  -H "Content-Type: application/json" \
  -d '{"name": "my-script"}'
# → 201 {"id":"...","name":"my-script","createdAt":"...","token":"pat_..."}
```

Use the raw token as a bearer credential against the public API:

```bash
curl https://argali.example/api/v1/public/expenses \
  -H "Authorization: Bearer pat_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
```

Internally, the gateway exchanges the opaque token for a short-lived internal JWT
(`POST /api/v1/auth/introspect` against user-service) and forwards that downstream —
expense/report/quest-service never see the personal access token itself.

## Public read API

All GET-only, all rate-limited (see below), all scoped to the calling token's owner.

| Method | Path | Mirrors |
|---|---|---|
| `GET` | `/api/v1/public/expenses` | `/api/v1/expenses` — same `from`/`to`/`categoryId`/`page`/`size` params. |
| `GET` | `/api/v1/public/dashboard` | `/api/v1/dashboard` — same `month` param. Already carries the ghost series; there is no separate ghost endpoint. |
| `GET` | `/api/v1/public/weather` | `/api/v1/weather` — current weather state only. |
| `GET` | `/api/v1/public/oaths` | `/api/v1/oaths` — same `status` param. |

Rate limit: 30 requests burst, refilling at 30/minute sustained, keyed per authenticated
caller. A conservative in-memory token bucket — this is a single-owner app, so the goal is
catching a runaway script, not multi-tenant fairness. It resets on gateway restart.

```bash
curl "https://argali.example/api/v1/public/dashboard?month=2026-08" \
  -H "Authorization: Bearer pat_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
```

## Webhooks

Registered, listed, and deleted under your session (`/api/v1/me/webhooks`, JWT-auth'd).

| Method | Path | Notes |
|---|---|---|
| `POST` | `/api/v1/me/webhooks` | Body: `{"url": "...", "eventPattern": "..."}`. Response includes the signing secret — the only time it is ever readable. |
| `GET` | `/api/v1/me/webhooks` | Lists your subscriptions (id, url, pattern, created/disabled-at, consecutive failure count). No secret. |
| `DELETE` | `/api/v1/me/webhooks/{id}` | Deletes the subscription. |

### Subscribable event patterns

`expense.*`, `category.*`, `income.updated`, `quest.*`, `oath.*`, `ambient.weather.updated`.

`user.registered` and the `user.erasure.*` pair are internal lifecycle/privacy signals and
are deliberately not subscribable — forwarding those to an arbitrary third-party URL is not
something a subscription can opt into.

### Delivery contract

Each delivery is a `POST` to your registered URL with:

| Header | Value |
|---|---|
| `X-Argali-Signature` | `sha256=<hex>` — HMAC-SHA256 over the exact raw request body, keyed by your webhook's secret. Recompute it from the raw bytes to verify authenticity and that the payload wasn't tampered with. |
| `X-Argali-Event` | The event type, e.g. `expense.created`. |
| `X-Argali-Delivery` | A unique id for this delivery attempt's parent event — stable across retries of the same delivery. |
| `X-Argali-Attempt` | `1`, `2`, `3`, ... — which attempt this is. |

**Retry schedule:** on a non-2xx response or an unreachable endpoint, retry after 1s, then
5s, then 25s. After the third retry (attempt 4) is also exhausted, the delivery is
dead-lettered and dropped.

**Auto-disable:** after 10 *consecutive* exhausted deliveries, the subscription is switched
off (`disabledAt` set, visible via `GET /api/v1/me/webhooks`) and stops being fanned out to.
A single bad afternoon on your endpoint doesn't disable it — only an endpoint that stays
gone across 10 straight failed deliveries does. Delete and re-register to resume.

Verify a delivery (Node example):

```js
const crypto = require("crypto");

function isValid(rawBody, signatureHeader, secret) {
  const expected = "sha256=" + crypto
    .createHmac("sha256", secret)
    .update(rawBody) // raw bytes, before any JSON.parse
    .digest("hex");
  return crypto.timingSafeEqual(Buffer.from(expected), Buffer.from(signatureHeader));
}
```
