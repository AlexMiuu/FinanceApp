-- M8: privacy & data-rights foundation (GDPR Art. 7 consent, Art. 17 erasure, Art. 20 export)

-- Consent is captured at registration: the row is the evidence that the version
-- of the policy in force at the time was accepted.
CREATE TABLE consent_records (
    id           uuid PRIMARY KEY,
    user_id      uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    consent_type text NOT NULL,                         -- 'TOS_PRIVACY'
    version      text NOT NULL,                         -- policy version accepted, e.g. '2026-08-06'
    granted_at   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_consent_records_user ON consent_records (user_id);

-- Deliberately no FK to users (id): this row is the audit trail of an erasure and
-- must outlive the user row it describes. ON DELETE CASCADE here would destroy the
-- record at exactly the moment it becomes the only proof the erasure happened.
CREATE TABLE erasure_requests (
    id                 uuid PRIMARY KEY,
    user_id            uuid NOT NULL,
    requested_at       timestamptz NOT NULL DEFAULT now(),
    expected_services  text NOT NULL,                    -- comma-separated, e.g. 'user,expense'
    completed_services text NOT NULL DEFAULT '',         -- comma-separated, grows as acks arrive
    status             text NOT NULL DEFAULT 'PENDING',  -- 'PENDING' | 'COMPLETED'
    completed_at       timestamptz
);

CREATE INDEX idx_erasure_requests_user ON erasure_requests (user_id);
