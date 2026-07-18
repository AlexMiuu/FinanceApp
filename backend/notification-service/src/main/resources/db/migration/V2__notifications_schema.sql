-- M6: persisted notifications (DESIGN.md section 6, notifications_db)

CREATE TABLE notifications (
    id         uuid PRIMARY KEY,
    user_id    uuid NOT NULL,
    type       text NOT NULL,        -- 'quest.completed' | 'quest.failed' | 'quest.suggested' | ...
    title      text NOT NULL,
    body       text NOT NULL,
    data       jsonb,
    read_at    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at DESC);
