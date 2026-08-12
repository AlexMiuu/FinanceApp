-- M15 (F1 Seasonal Transhumance): the winter-reserve template, plus the local
-- projection of the seasonal calendar published by report-service's macro feature.

INSERT INTO quest_templates (code, title, description, rule) VALUES
    ('SEASONAL_RESERVE', 'Build the winter reserve',
     'Ahead of the cold season, hold this month''s spending under a tailored ceiling so the difference becomes a reserve.',
     '{"incomeShare": 0.10, "period": "MONTH", "trigger": "macro.season.changed"}');

-- Singleton by construction: quest-service only ever needs the season it is
-- currently in, never the history, so a one-row table beats an append log here.
CREATE TABLE macro_season (
    id         integer PRIMARY KEY CHECK (id = 1),
    season     text NOT NULL,
    source     text,
    as_of_date date,
    updated_at timestamptz NOT NULL DEFAULT now()
);
