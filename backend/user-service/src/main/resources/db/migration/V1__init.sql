-- FINOVA user-service schema.
-- Hibernate runs with ddl-auto=validate, so every column below must match the
-- entity mapping exactly (name, type, length, nullability).

CREATE TABLE users (
    id            VARCHAR(36) NOT NULL,
    first_name    VARCHAR(80) NOT NULL,
    last_name     VARCHAR(80) NOT NULL,
    -- Stored lower-cased and trimmed, which is why the unique index is enough to
    -- stop two registrations that differ only in case or padding.
    email         VARCHAR(190) NOT NULL,
    phone         VARCHAR(32),
    -- BCrypt hash only. A plain password is never written anywhere in the platform.
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20) NOT NULL,
    status        VARCHAR(20) NOT NULL,
    last_login_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uq_users_email ON users (email);

CREATE TABLE refresh_tokens (
    id         VARCHAR(36) NOT NULL,
    user_id    VARCHAR(36),
    -- The JWT `jti` claim. Rotation revokes the row by this id and issues a new one.
    token_id   VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    ip_address VARCHAR(64),
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uq_refresh_tokens_token_id ON refresh_tokens (token_id);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

-- The refresh path is "is this session still alive?", which is a range query on
-- expires_at; without this the table degrades into a full scan once sessions pile up.
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens (expires_at);

CREATE TABLE audit_logs (
    id             VARCHAR(36) NOT NULL,
    -- Kafka envelope id. NULL for rows written by this service's own request path
    -- and by the dev seeder, which is why the unique index below is partial.
    event_id       VARCHAR(64),
    action         VARCHAR(40) NOT NULL,
    user_id        VARCHAR(36),
    resource       VARCHAR(60) NOT NULL,
    resource_id    VARCHAR(80),
    ip_address     VARCHAR(64),
    correlation_id VARCHAR(64),
    result         VARCHAR(20) NOT NULL,
    service        VARCHAR(40) NOT NULL,
    message        VARCHAR(500),
    metadata       JSONB,
    created_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_audit_logs PRIMARY KEY (id)
);

-- Consumer idempotency guarantee. Kafka delivery is at-least-once, so the same
-- audit event can arrive twice; this partial unique index makes the second delivery
-- fail at the database instead of writing a second audit row. Rows written locally
-- have a NULL event_id and are therefore not constrained by it.
CREATE UNIQUE INDEX uq_audit_logs_event_id
    ON audit_logs (event_id) WHERE event_id IS NOT NULL;

-- The admin audit screen filters on action, then on the actor, then narrows to a
-- time window; these are the access paths that must not degrade to a table scan.
CREATE INDEX idx_audit_logs_action ON audit_logs (action);

CREATE INDEX idx_audit_logs_user ON audit_logs (user_id);

CREATE INDEX idx_audit_logs_correlation ON audit_logs (correlation_id);

CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at DESC);
