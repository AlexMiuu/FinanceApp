-- M5: event-fed projections + goals & evaluations (DESIGN.md section 6, quests_db)
-- quest_templates/quests arrive with M6.

CREATE TABLE expense_projection (
    expense_id    uuid PRIMARY KEY,
    user_id       uuid NOT NULL,
    category_id   uuid NOT NULL,
    category_path text NOT NULL,
    is_mandatory  boolean NOT NULL,
    amount        bigint NOT NULL,
    currency      char(3) NOT NULL DEFAULT 'RON',
    note          text,
    expense_date  date NOT NULL,
    updated_at    timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_q_projection_user_date ON expense_projection (user_id, expense_date);

CREATE TABLE category_projection (
    category_id  uuid PRIMARY KEY,
    user_id      uuid NOT NULL,
    name         text NOT NULL,
    parent_id    uuid,
    is_mandatory boolean NOT NULL
);

CREATE TABLE goals (
    id            uuid PRIMARY KEY,
    user_id       uuid NOT NULL,
    name          text NOT NULL,
    type          text NOT NULL,              -- 'SPENDING_LIMIT' now; 'SAVING_TARGET' reserved (needs savings events)
    category_id   uuid,                        -- NULL = overall spending
    target_amount bigint NOT NULL CHECK (target_amount > 0),
    period        text NOT NULL,               -- 'DAILY' | 'MONTHLY' | 'YEARLY'
    start_date    date NOT NULL,
    end_date      date,
    active        boolean NOT NULL DEFAULT true,
    created_at    timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_goals_user ON goals (user_id);

-- One row per goal per completed period; powers the calendar's history (FR-7).
CREATE TABLE goal_evaluations (
    id            uuid PRIMARY KEY,
    goal_id       uuid NOT NULL REFERENCES goals (id) ON DELETE CASCADE,
    period_start  date NOT NULL,
    period_end    date NOT NULL,
    actual_amount bigint NOT NULL,
    met           boolean NOT NULL,
    evaluated_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (goal_id, period_start)
);
