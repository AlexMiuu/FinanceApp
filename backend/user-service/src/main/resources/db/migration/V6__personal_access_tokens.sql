-- M14: personal access tokens for the read-only developer API.
--
-- Only the SHA-256 of the token is stored. The raw token is returned exactly
-- once, at creation, and is unrecoverable afterwards — a leaked database gives
-- an attacker nothing replayable. token_hash is the lookup key on every
-- introspection call, so it carries the unique index.
--
-- scope is a plain string rather than an enum: 'read' is the only value M14
-- issues, but a future write or per-resource scope must not need a migration.
--
-- revoked_at is nullable and set in place rather than deleting the row, so a
-- revoked token stays visible in the user's token list and its id keeps
-- resolving. Revocation checks read this column directly, which is what makes
-- revocation take effect on the very next request.
CREATE TABLE personal_access_tokens (
    id           uuid PRIMARY KEY,
    user_id      uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name         text NOT NULL,
    token_hash   text NOT NULL,
    scope        text NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    last_used_at timestamptz,
    revoked_at   timestamptz
);

CREATE UNIQUE INDEX personal_access_tokens_token_hash_key
    ON personal_access_tokens (token_hash);

-- The token list is always read for one user, newest first.
CREATE INDEX personal_access_tokens_user_id_idx
    ON personal_access_tokens (user_id, created_at DESC);
