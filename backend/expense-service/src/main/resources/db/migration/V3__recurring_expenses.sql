-- Usability: monthly recurring expense templates. A daily job (plus startup
-- catch-up) posts real expenses from these, so rent/subscriptions never need
-- manual entry. next_run in the past simply catches up — backdating a
-- template auto-fills its history.

CREATE TABLE recurring_expenses (
    id           uuid PRIMARY KEY,
    user_id      uuid NOT NULL,
    category_id  uuid NOT NULL REFERENCES categories (id),
    amount       bigint NOT NULL CHECK (amount > 0),   -- bani
    note         text,
    day_of_month int NOT NULL CHECK (day_of_month BETWEEN 1 AND 31),
    next_run     date NOT NULL,
    active       boolean NOT NULL DEFAULT true,
    created_at   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_recurring_user ON recurring_expenses (user_id);
CREATE INDEX idx_recurring_due ON recurring_expenses (next_run) WHERE active;
