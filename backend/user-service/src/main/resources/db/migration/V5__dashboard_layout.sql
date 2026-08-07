-- M10: per-user dashboard widget arrangement.
--
-- Server-side rather than localStorage (D9): losing this key would lose a user
-- choice that cannot be regenerated from anything else, so it belongs in a
-- service. One row per user — the layout is a single small document, not a
-- collection, so the user id is the primary key and an upsert replaces it whole.
CREATE TABLE dashboard_layouts (
    user_id    uuid PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    layout     jsonb NOT NULL,                     -- {"main":["balance",...],"side":["savings",...]}
    updated_at timestamptz NOT NULL DEFAULT now()
);
