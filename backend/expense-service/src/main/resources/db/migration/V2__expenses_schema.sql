-- M2: categories & expenses (DESIGN.md section 6, expenses_db)

CREATE TABLE categories (
    id           uuid PRIMARY KEY,
    user_id      uuid NOT NULL,                        -- owner; no cross-DB FK (enforced in service)
    parent_id    uuid REFERENCES categories (id),      -- NULL = top-level, set = subcategory
    name         text NOT NULL,
    icon         text,
    color        text,
    is_mandatory boolean NOT NULL DEFAULT false,       -- feeds quest tailoring (O3)
    created_at   timestamptz NOT NULL DEFAULT now()
);

-- Postgres treats NULLs as distinct in unique constraints, so top-level names
-- need their own index to be unique per user.
CREATE UNIQUE INDEX uq_categories_sub ON categories (user_id, parent_id, name) WHERE parent_id IS NOT NULL;
CREATE UNIQUE INDEX uq_categories_top ON categories (user_id, name) WHERE parent_id IS NULL;
CREATE INDEX idx_categories_user ON categories (user_id);

CREATE TABLE expenses (
    id           uuid PRIMARY KEY,
    user_id      uuid NOT NULL,
    category_id  uuid NOT NULL REFERENCES categories (id),
    amount       bigint NOT NULL CHECK (amount > 0),   -- bani (RON cents), never floats
    currency     char(3) NOT NULL DEFAULT 'RON',
    note         text,
    expense_date date NOT NULL,
    source       text NOT NULL DEFAULT 'MANUAL',       -- 'MANUAL' | 'BANK_IMPORT' (v2)
    external_id  text,                                 -- idempotency key for v2 bank imports
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_expenses_user_date ON expenses (user_id, expense_date);
CREATE INDEX idx_expenses_user_category ON expenses (user_id, category_id);
