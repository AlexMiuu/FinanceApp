-- M4: income, savings, and versioned salary-tax configuration (DESIGN.md section 6)

CREATE TABLE income_sources (
    id         uuid PRIMARY KEY,
    user_id    uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name       text NOT NULL,
    amount     bigint NOT NULL CHECK (amount > 0),    -- bani
    recurrence text NOT NULL,                          -- 'MONTHLY' | 'YEARLY' | 'ONE_OFF'
    start_date date NOT NULL,
    end_date   date,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_income_sources_user ON income_sources (user_id);

CREATE TABLE savings_accounts (
    id         uuid PRIMARY KEY,
    user_id    uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name       text NOT NULL,
    balance    bigint NOT NULL DEFAULT 0 CHECK (balance >= 0),  -- bani; powers net worth (FR-8)
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_savings_accounts_user ON savings_accounts (user_id);

-- Salary-calculator rules are data, not code: a law change is an INSERT with a
-- new valid_from, never a redeploy (FR-10).
CREATE TABLE tax_config (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    valid_from date NOT NULL UNIQUE,
    rules      jsonb NOT NULL
);

-- Romanian rules as of 2026: CAS 25%, CASS 10%, income tax 10% on the rest.
-- personalDeduction (bani) is simplified to 0 in v1: the degressive deduction
-- table only affects salaries near minimum wage; add it as data when needed.
INSERT INTO tax_config (valid_from, rules) VALUES
    (DATE '2026-01-01',
     '{"casRate": 0.25, "cassRate": 0.10, "incomeTaxRate": 0.10, "personalDeduction": 0}');
