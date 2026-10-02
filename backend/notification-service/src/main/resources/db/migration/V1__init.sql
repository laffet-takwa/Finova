-- FINOVA notification-service schema.
-- Hibernate runs with ddl-auto=validate, so every column below must match the
-- entity mapping exactly (name, type, length, nullability).

CREATE TABLE notifications (
    id              VARCHAR(36)  NOT NULL,
    user_id         VARCHAR(36)  NOT NULL,
    type            VARCHAR(30)  NOT NULL,
    category        VARCHAR(20)  NOT NULL,
    severity        VARCHAR(10)  NOT NULL,
    title           VARCHAR(140) NOT NULL,
    message         VARCHAR(500) NOT NULL,
    transaction_id  VARCHAR(36),
    reference       VARCHAR(24),
    amount          NUMERIC(19, 3),
    currency        VARCHAR(3),
    read            BOOLEAN      NOT NULL DEFAULT FALSE,
    read_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL,
    correlation_id  VARCHAR(64),
    source_service  VARCHAR(40),
    source_event_id VARCHAR(64),
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_notifications PRIMARY KEY (id)
);

-- Inbox query shape: one user's notifications, newest first.
CREATE INDEX idx_notification_user_created ON notifications (user_id, created_at DESC);

-- Partial index: the unread badge and the "unread only" filter only ever touch
-- unread rows, which are a small fraction of a mature inbox, so the index stays
-- tiny instead of duplicating the full user index.
CREATE INDEX idx_notification_user_unread ON notifications (user_id) WHERE read = FALSE;

CREATE INDEX idx_notification_transaction ON notifications (transaction_id);

-- Consumer idempotency guarantee. Kafka delivery is at-least-once, so the same
-- source event can arrive twice; this partial unique index makes the second
-- delivery fail at the database instead of writing a second inbox row. Rows
-- created by a human (or the dev seeder) have a NULL source_event_id and are
-- therefore not constrained by it.
CREATE UNIQUE INDEX uq_notification_source_event
    ON notifications (source_event_id) WHERE source_event_id IS NOT NULL;

CREATE TABLE notification_preferences (
    id               VARCHAR(36) NOT NULL,
    email_enabled    BOOLEAN     NOT NULL DEFAULT TRUE,
    push_enabled     BOOLEAN     NOT NULL DEFAULT TRUE,
    in_app_enabled   BOOLEAN     NOT NULL DEFAULT TRUE,
    transfer_alerts  BOOLEAN     NOT NULL DEFAULT TRUE,
    security_alerts  BOOLEAN     NOT NULL DEFAULT TRUE,
    marketing_emails BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at       TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_notification_preferences PRIMARY KEY (id)
);

-- Transactional outbox: the notification row and the notification.created event
-- commit together, so a broker outage can delay the event but never lose it.
CREATE TABLE outbox_event (
    id           VARCHAR(36)  NOT NULL,
    topic        VARCHAR(60)  NOT NULL,
    event_key    VARCHAR(80)  NOT NULL,
    dedupe_key   VARCHAR(160) NOT NULL,
    payload      JSONB        NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    published_at TIMESTAMPTZ,
    attempts     INTEGER      NOT NULL DEFAULT 0,
    last_error   VARCHAR(500),
    CONSTRAINT pk_outbox_event PRIMARY KEY (id),
    CONSTRAINT uq_outbox_event_dedupe_key UNIQUE (dedupe_key)
);

CREATE INDEX idx_outbox_pending ON outbox_event (created_at) WHERE published_at IS NULL;