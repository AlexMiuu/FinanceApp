-- M3: event-fed read models + saved reports (DESIGN.md section 6, reports_db)

-- Local copy of expense data, maintained from expense.* events. Never queried
-- cross-service: this is the whole point of the projection.
CREATE TABLE expense_projection (
    expense_id    uuid PRIMARY KEY,
    user_id       uuid NOT NULL,
    category_id   uuid NOT NULL,
    category_path text NOT NULL,                -- denormalized 'Housing > Rent'
    is_mandatory  boolean NOT NULL,             -- effective: category or its parent
    amount        bigint NOT NULL,              -- bani
    currency      char(3) NOT NULL DEFAULT 'RON',
    note          text,
    expense_date  date NOT NULL,
    updated_at    timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_projection_user_date ON expense_projection (user_id, expense_date);
CREATE INDEX idx_projection_user_category ON expense_projection (user_id, category_id);

-- Category shadow copy so renames can be propagated into category_path.
CREATE TABLE category_projection (
    category_id  uuid PRIMARY KEY,
    user_id      uuid NOT NULL,
    name         text NOT NULL,
    parent_id    uuid,
    is_mandatory boolean NOT NULL
);

CREATE TABLE reports (
    id            uuid PRIMARY KEY,
    user_id       uuid NOT NULL,
    name          text NOT NULL,
    filters       jsonb NOT NULL,               -- {from, to, categoryIds}
    created_at    timestamptz NOT NULL DEFAULT now(),
    last_run_at   timestamptz,
    cached_result jsonb
);

CREATE INDEX idx_reports_user ON reports (user_id);
