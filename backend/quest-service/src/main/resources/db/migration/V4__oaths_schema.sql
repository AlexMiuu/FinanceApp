-- M12 (F2 Tally Oath): a pledge to spend a given amount in a given category
-- before a deadline. Reconciled against the expense_projection read model that
-- expense.created already feeds, so Expense Service is untouched.

CREATE TABLE oaths (
    id                 uuid PRIMARY KEY,
    user_id            uuid NOT NULL,
    category_id        uuid NOT NULL,
    category_name      text NOT NULL,
    pledged_amount     bigint NOT NULL,
    status             text NOT NULL DEFAULT 'OPEN',  -- OPEN|KEPT|SLIPPED|FORGONE
    matched_expense_id uuid,
    resolved_at        timestamptz,
    expires_at         timestamptz NOT NULL,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_oaths_user_status ON oaths (user_id, status);

-- Drives the expiry sweep, which scans every user's open oaths by deadline.
CREATE INDEX idx_oaths_status_expires ON oaths (status, expires_at);

-- Reconciliation asks "has this expense already closed an oath?" on every
-- expense.created, including redeliveries; a unique index also makes it
-- impossible for one expense to close two oaths.
CREATE UNIQUE INDEX idx_oaths_matched_expense ON oaths (matched_expense_id)
    WHERE matched_expense_id IS NOT NULL;
