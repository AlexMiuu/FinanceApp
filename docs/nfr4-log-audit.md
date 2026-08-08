# NFR-4 log/URL audit — M10 exit criterion

**NFR-4** (`DESIGN.md:109`): *"no financial data in URLs or logs."*

**Status:** Complete. Zero violations found. Zero files changed.

**Why this ran now, not with the rest of M10:** the M10-partial commit (`ef086e9`, merged via PR #9) deliberately deferred this audit — quest-service and notification-service hadn't been restructured under M9 yet, so findings taken against their pre-standardization code would have gone stale the moment those services were rewritten. M9 (report, quest, notification standardization) is now fully merged to `dev`, so this is the first point the audit can run against final code for all five services.

**Scope:** every backend service (`user`, `expense`, `report`, `quest`, `notification`), the `gateway`, and the `frontend`. Three reviewers split the work by service group and worked independently; this document merges their findings.

**Method, applied uniformly:**
1. Every SLF4J logger call site (`log.info/debug/warn/error/trace`), plus `System.out`/`System.err`/`printStackTrace` — checked whether any interpolated value is a monetary amount, balance, IBAN/account identifier, token, or a full request/response payload. Logging an entity UUID, a routing key, a service name, or an exception with no re-interpolated sensitive field is accepted practice.
2. Each `application.yml` — checked for `logging.level` overrides that would enable SQL parameter logging (`org.hibernate.SQL`, `org.hibernate.orm.jdbc.bind`) or HTTP request/response body logging.
3. Every REST controller — checked whether financial data (amounts, balances, export/report contents) ever travels via `@PathVariable`/`@RequestParam`/query string instead of the request/response body. IDs, dates, and category filters in query strings are accepted; monetary values are not.
4. The privacy-critical paths specifically — erasure (`PrivacyService`/`AccountDeletionService`), export (`DataExportService`), and the CSV export path in report-service — read in full, since these are the classes most likely to hold a full personal-data payload in memory.
5. Frontend: `console.*` calls, and `frontend/src/lib/api.ts` for financial values reaching a URL instead of a request body.

## user-service

No violations. Every logger call carries only entity UUIDs, service-name strings, or exceptions (`AccountDeletionService`, `UserEventPublisher`, `IncomeEventPublisher`, `ErasureCompletedListener`, `JwtService`). No `application.yml` logging overrides. No controller declares `@RequestParam` at all — every amount (income, savings, salary) travels via `@RequestBody`. `DataExportService` builds the full export DTO in memory and returns it without ever logging it.

## expense-service

No violations. Logger calls carry user/request UUIDs, routing keys, or a bare posting count (`RecurringExpenseService`); the catch-all `ApiExceptionHandler` logs the exception object only. `ExpenseController#list` is the only `@RequestParam` user (date range, category UUID, pagination) — no amounts. `PrivacyService` (erasure/export) logs nothing.

## report-service

No violations. Only 3 files log anything, all in the `events/` package, all carrying routing key / userId / erasureRequestId only. The CSV export endpoint (`ReportController#export`) and `DashboardService`/`ReportService` have no logging calls at all — export content and dashboard projections are never logged. Only query param across both controllers is `month`.

## quest-service

No violations. Same pattern as report-service — logging confined to `events/`, carrying only routing keys and UUIDs. `QuestService`/`GoalService` compute and return cap/progress/target-amount values but never log them. Only query param is `month` on `/calendar`.

## notification-service

No violations. `EventRelay`, `QuestEventListener`, `UserErasureRequestedListener` log only routing keys, user UUIDs, and exceptions — never a STOMP payload or notification body. No `logging.level` overrides. `NotificationService`/`NotificationController` declare no logger at all, so the message body broadcast to clients is never logged.

## gateway

No violations. Zero logger calls in the module (`RouteConfig`, `SecurityConfig`, `GatewayApplication`). No `logging.level` override for `spring.cloud.gateway`/`reactor.netty` (which would otherwise dump full proxied requests/responses), no access-log filter. Routes pass query params straight through unmodified and unlogged; those params are IDs/dates only, confirmed against what the frontend actually sends (see below).

## frontend

No violations. Zero `console.*` calls anywhere in `frontend/src` (checked `log`/`warn`/`error`/`debug`/`info`). Every endpoint in `frontend/src/lib/api.ts` that carries a financial value sends it via a JSON request body; the only query-string usage across the app is date ranges, category filters, pagination, and `month` — never an amount, balance, or account identifier. `frontend/src/lib/ws.ts` (STOMP client) does not log connect headers, message bodies, or tokens. No analytics/telemetry/error-tracking SDK is present in `package.json`.

## Build verification

All 6 backend modules (`user-service`, `expense-service`, `report-service`, `quest-service`, `notification-service`, `gateway`) compile clean on this branch under JDK 21 (`mvn compile`, run per-module — this repo has no aggregator `pom.xml`). Frontend had zero file changes in this audit; its build was verified separately as part of the M10-partial PR (#9).

## Conclusion

NFR-4 holds across the full system as it stands today. No fixes were required because none of the five services, the gateway, or the frontend had a violation — the discipline established in `user-service`/`expense-service` (the two services standardized earliest, under M8) held through the M9 rewrite of `report`/`quest`/`notification` and through the M10-partial frontend work. This satisfies M10's exit criterion: *"Log audit produces a written finding list; every instance fixed or explicitly accepted in writing."* The finding, for every instance checked, is "no violation" — recorded above per service with what was specifically reviewed.
