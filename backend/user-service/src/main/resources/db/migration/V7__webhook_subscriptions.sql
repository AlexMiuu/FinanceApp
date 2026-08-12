-- M14 (D6): user-registered webhook endpoints fed from the pf.events exchange.
--
-- The signing secret is stored in the clear, unlike a personal access token,
-- which is hashed. The difference is deliberate: a PAT is only ever compared
-- against, so a one-way hash suffices, whereas this secret has to be replayed on
-- every delivery to compute the HMAC. It is returned to the client exactly once
-- at creation and never read back over the API.
--
-- consecutive_failures counts deliveries that exhausted every retry, not
-- individual failed attempts, and resets on the first success. disabled_at is
-- set once that counter crosses the threshold so a subscriber whose endpoint has
-- gone permanently missing stops generating traffic on every event.
CREATE TABLE webhook_subscriptions (
    id                   uuid PRIMARY KEY,
    user_id              uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    url                  text NOT NULL,
    event_pattern        text NOT NULL,          -- AMQP topic pattern, e.g. 'expense.*' or 'oath.kept'
    secret               text NOT NULL,
    created_at           timestamptz NOT NULL DEFAULT now(),
    disabled_at          timestamptz,
    consecutive_failures integer NOT NULL DEFAULT 0
);

-- Every delivered event fans out through a lookup of one user's live
-- subscriptions, so that is the access path worth indexing.
CREATE INDEX idx_webhook_subscriptions_live
    ON webhook_subscriptions (user_id)
    WHERE disabled_at IS NULL;
