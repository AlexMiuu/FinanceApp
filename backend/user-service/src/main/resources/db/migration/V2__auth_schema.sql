-- M1: identity & auth schema (DESIGN.md section 6, users_db)

CREATE EXTENSION IF NOT EXISTS citext;

CREATE TABLE users (
    id            uuid PRIMARY KEY,
    email         citext NOT NULL UNIQUE,
    password_hash text,                                -- NULL for OAuth-only accounts
    display_name  text NOT NULL,
    avatar_url    text,
    base_currency char(3) NOT NULL DEFAULT 'RON',
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE auth_identities (
    id           uuid PRIMARY KEY,
    user_id      uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider     text NOT NULL,                        -- 'PASSWORD' | 'GOOGLE'
    provider_uid text NOT NULL,                        -- Google "sub" claim; user id for PASSWORD
    created_at   timestamptz NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_uid)
);

CREATE INDEX idx_auth_identities_user ON auth_identities (user_id);

CREATE TABLE refresh_tokens (
    id         uuid PRIMARY KEY,
    user_id    uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash text NOT NULL UNIQUE,                   -- SHA-256 of the opaque token
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
