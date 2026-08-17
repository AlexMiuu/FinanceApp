# Deploying Argali for one person

A runbook for putting Argali on the web for your own use. It is deliberately not
the full production milestone (M17) — there is no billing, no load testing, no
support surface. It gets you a private instance on your own domain, over HTTPS,
that survives a reboot and can be restored if the server is lost.

## What you need

**A server.** Measured on the development stack at idle, the nine containers use
about **3.2 GB**, almost all of it the six JVMs:

| Container | Idle |
|---|---|
| report-service | 552 MB |
| user-service | 529 MB |
| quest-service | 518 MB |
| notification-service | 498 MB |
| expense-service | 477 MB |
| gateway | 343 MB |
| rabbitmq | 136 MB |
| postgres | 122 MB |
| frontend (nginx) | 13 MB |

- **8 GB** — comfortable, defaults work unchanged. This is the recommendation.
- **4 GB** — workable with the reduced limits noted in `.env.example`. Tight.
- **2 GB** — no. Six Spring Boot services will not fit.

Those figures are with the JVM free to size itself against a 7.4 GB host. The
production compose caps each heap, which brings them down, but the shape holds:
the JVMs are the cost and everything else is rounding error.

An EU region keeps latency low from Romania and avoids questions about moving
personal data outside the EU, which matters given the GDPR work already in the
codebase.

**A domain**, with an `A` record pointing at the server's IP. Caddy requests a
certificate for exactly the name in `ARGALI_DOMAIN`, so this must resolve before
the first start.

## One-time server setup

1. Create a non-root user with sudo, and use key-based SSH.
2. Firewall: allow `22`, `80`, `443` and nothing else.
   ```
   sudo ufw default deny incoming
   sudo ufw allow 22 && sudo ufw allow 80 && sudo ufw allow 443
   sudo ufw enable
   ```
   The compose file already publishes nothing but Caddy, so this is the second
   layer rather than the only one.
3. Install Docker Engine and the Compose plugin.
4. Clone the repository (only the compose file, Caddyfile and `infra/` are used
   at runtime — images come from the registry).

## Secrets

Copy `.env.example` to `.env` on the server and fill in the deployment block.
`docker-compose.prod.yml` refuses to start if `ARGALI_DOMAIN`, `ACME_EMAIL`,
`POSTGRES_PASSWORD`, `RABBITMQ_PASSWORD` or `AUTH_JWT_PRIVATE_KEY_PEM` are
missing — that is on purpose. A stack that boots with the development password
is worse than one that refuses.

Generate the signing key once:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048
```

Keep a copy somewhere safe. Losing it does not lose data, but it does sign
everyone out and invalidates issued tokens.

## Google sign-in

If you use it, add the production redirect URI in the Google console:

```
https://YOUR-DOMAIN/api/v1/auth/oauth/callback/google
```

Leave `GOOGLE_CLIENT_ID` empty to disable Google login entirely — the whole
OAuth chain is conditional on it, and the sign-in screen hides the button.

## First deploy

Images are built by `.github/workflows/publish-images.yml` on every push to
`main`/`dev` and pushed to GHCR. On the server:

```bash
docker compose -f docker-compose.prod.yml --env-file .env pull
docker compose -f docker-compose.prod.yml --env-file .env up -d
docker compose -f docker-compose.prod.yml logs -f caddy
```

Watch Caddy obtain the certificate. If it loops, the usual causes are the `A`
record not yet pointing here, or port 80 blocked.

If the GHCR packages are private, authenticate first with a personal access
token that has `read:packages`:

```bash
echo YOUR_TOKEN | docker login ghcr.io -u YOUR_GITHUB_USERNAME --password-stdin
```

## Create your account, then close the door

`AUTH_REGISTRATION_ENABLED` defaults to `false` in the production compose, so
nobody can sign up on a server you have just exposed. To make your own account:

1. Set `AUTH_REGISTRATION_ENABLED=true` in `.env`.
2. `docker compose -f docker-compose.prod.yml --env-file .env up -d user-service`
3. Register through the site.
4. Set it back to `false` and run the same command again.

The sign-in screen then shows "Closed to new accounts", and the endpoint itself
returns 403 — the check is server-side, not merely hidden in the UI.

## Backups

The only stateful volume is `postgres-data`. A nightly dump, kept off the
server:

```bash
docker compose -f docker-compose.prod.yml exec -T postgres \
  pg_dumpall -U finance | gzip > argali-$(date +%F).sql.gz
```

Put that in cron, and copy the file somewhere that is not this machine — a
backup living only on the box it protects is not a backup.

**Then restore it once, deliberately, before you need to.** Bring up a throwaway
Postgres container, load the dump, and confirm your expenses are there. An
untested backup is a guess.

## Updating

```bash
git pull
docker compose -f docker-compose.prod.yml --env-file .env pull
docker compose -f docker-compose.prod.yml --env-file .env up -d
```

Flyway migrations run at service start, so schema changes apply themselves.
Take a dump before an update that includes migrations.

## What this deliberately leaves out

- **Monitoring.** `restart: unless-stopped` covers a crash; nothing tells you the
  site is down. An external uptime ping is the cheapest fix.
- **Log shipping.** Logs live in the containers and rotate away.
- **Staging.** There is one environment, and it is the live one.
- **Load testing.** M17's k6 target does not exist at one user.

These are fine to skip for a personal instance and are the first things to add
if it ever serves anyone else.
