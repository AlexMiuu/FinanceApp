-- M6: quest templates, quests, and the income projection used for tailoring

CREATE TABLE quest_templates (
    code        text PRIMARY KEY,
    title       text NOT NULL,
    description text NOT NULL,
    rule        jsonb NOT NULL
);

INSERT INTO quest_templates (code, title, description, rule) VALUES
    ('CATEGORY_CAP', 'Trim your top category',
     'Spend less on your biggest discretionary category than you did last week.',
     '{"capFactor": 0.85, "period": "WEEK"}'),
    ('WEEKLY_CAP', 'Weekly discretionary cap',
     'Keep this week''s non-mandatory spending under a tailored cap.',
     '{"capFactor": 0.85, "incomeShare": 0.25, "period": "WEEK"}'),
    ('NO_SPEND_DAYS', 'No-spend days',
     'Have days with zero spending this week.',
     '{"days": 2, "period": "WEEK"}'),
    ('BEAT_LAST_MONTH', 'Beat last month',
     'Finish this month having spent less than last month.',
     '{"period": "MONTH"}');

CREATE TABLE quests (
    id              uuid PRIMARY KEY,
    user_id         uuid NOT NULL,
    template_code   text NOT NULL REFERENCES quest_templates (code),
    title           text NOT NULL,
    params          jsonb NOT NULL,       -- {cap} | {days} | {cap, categoryId, categoryName}
    period_start    date NOT NULL,
    period_end      date NOT NULL,
    status          text NOT NULL DEFAULT 'SUGGESTED',  -- SUGGESTED|ACTIVE|COMPLETED|FAILED|DECLINED
    progress_amount bigint NOT NULL DEFAULT 0,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (user_id, template_code, period_start)       -- one instance per template per period
);

CREATE INDEX idx_quests_user_status ON quests (user_id, status);

-- Event-fed from income.updated (user-service); tailoring input only.
CREATE TABLE user_income (
    user_id        uuid PRIMARY KEY,
    monthly_income bigint NOT NULL,
    updated_at     timestamptz NOT NULL DEFAULT now()
);
