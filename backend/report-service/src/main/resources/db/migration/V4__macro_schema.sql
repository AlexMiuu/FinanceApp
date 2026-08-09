-- M15 (F1 Seasonal Transhumance): macro ingestion cache + pastoral calendar state.

-- Singleton row holding the currently-committed pastoral season. Global, not
-- per-user, so there is exactly one row (id always 1).
CREATE TABLE macro_season_state (
    id         int PRIMARY KEY,
    season     text NOT NULL,
    as_of_date date NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now()
);

-- Last-known-good cached value per macro series (kind is the primary key: one
-- row per series, overwritten only on a successful refresh).
CREATE TABLE macro_readings (
    kind       text PRIMARY KEY,
    value      numeric NOT NULL,
    as_of_date date NOT NULL,
    fetched_at timestamptz NOT NULL DEFAULT now()
);
