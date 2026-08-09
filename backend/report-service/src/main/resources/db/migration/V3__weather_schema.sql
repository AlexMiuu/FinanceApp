-- M13 (F4 Shepherd's Weather): the two inputs the banded composite needs.

-- Event-fed from income.updated (user-service); the denominator of the composite.
-- Mirrors quest-service's identical projection rather than querying user-service,
-- so the dashboard never takes a synchronous cross-service dependency.
CREATE TABLE user_income (
    user_id        uuid PRIMARY KEY,
    monthly_income bigint NOT NULL,
    updated_at     timestamptz NOT NULL DEFAULT now()
);

-- Committed band plus the pending transition's dwell clock. Persisted rather than
-- held in memory because the 6-hour dwell has to survive a restart: an in-memory
-- clock would reset on every deploy, and the band would then commit on whichever
-- recompute ran six hours after the process last started.
CREATE TABLE weather_state (
    user_id       uuid PRIMARY KEY,
    current_band  text NOT NULL,
    pending_band  text,
    pending_since timestamptz,
    updated_at    timestamptz NOT NULL DEFAULT now()
);
