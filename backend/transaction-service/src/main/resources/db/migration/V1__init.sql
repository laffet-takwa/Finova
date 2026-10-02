-- Finova transaction service: authoritative money ledger.
-- The account-service owns the account product; this service owns the balance.

CREATE TABLE ledger_account (
    id             varchar(36)  NOT NULL,
    account_id     varchar(36)  NOT NULL,
    account_number varchar(34)  NOT NULL,
    user_id        varchar(36)  NOT NULL,
    account_type   varchar(20)  NOT NULL,
    currency       varchar(3)   NOT NULL,
    balance        numeric(19,3) NOT NULL,
    status         varchar(20)  NOT NULL,
    version        bigint       NOT NULL DEFAULT 0,
    created_at     timestamptz  NOT NULL,
    updated_at     timestamptz  NOT NULL,
    CONSTRAINT pk_ledger_account PRIMARY KEY (id),
    CONSTRAINT uk_ledger_account_account_id UNIQUE (account_id),
    CONSTRAINT ck_ledger_account_balance_non_negative CHECK (balance >= 0)
);

CREATE INDEX ix_ledger_account_account_number ON ledger_account (account_number);
CREATE INDEX ix_ledger_account_user_id ON ledger_account (user_id);

CREATE TABLE transaction (
    id                       varchar(36)   NOT NULL,
    reference                varchar(24)   NOT NULL,
    idempotency_key          varchar(80)   NOT NULL,
    request_fingerprint      varchar(64)   NOT NULL,
    sender_account_id        varchar(36)   NOT NULL,
    receiver_account_id      varchar(36)   NOT NULL,
    sender_account_number    varchar(34),
    receiver_account_number  varchar(34),
    sender_user_id           varchar(36)   NOT NULL,
    receiver_user_id         varchar(36),
    amount                   numeric(19,3) NOT NULL,
    currency                 varchar(3)    NOT NULL,
    fee                      numeric(19,3) NOT NULL DEFAULT 0,
    description              varchar(255),
    type                     varchar(20)   NOT NULL,
    status                   varchar(20)   NOT NULL,
    failure_reason           varchar(255),
    risk_score               integer,
    risk_level               varchar(10),
    risk_reasons             jsonb,
    created_at               timestamptz   NOT NULL,
    completed_at             timestamptz,
    settled_sender_balance   numeric(19,3),
    settled_receiver_balance numeric(19,3),
    correlation_id           varchar(64),
    requested_by_user_id     varchar(36),
    ip_address               varchar(64),
    version                  bigint        NOT NULL DEFAULT 0,
    CONSTRAINT pk_transaction PRIMARY KEY (id),
    CONSTRAINT uk_transaction_reference UNIQUE (reference),
    CONSTRAINT uk_transaction_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_transaction_amount_positive CHECK (amount > 0)
);

CREATE INDEX ix_transaction_sender_account_id ON transaction (sender_account_id);
CREATE INDEX ix_transaction_receiver_account_id ON transaction (receiver_account_id);
CREATE INDEX ix_transaction_sender_user_id ON transaction (sender_user_id);
CREATE INDEX ix_transaction_receiver_user_id ON transaction (receiver_user_id);
CREATE INDEX ix_transaction_status ON transaction (status);
CREATE INDEX ix_transaction_created_at ON transaction (created_at);
CREATE INDEX ix_transaction_completed_at ON transaction (completed_at);
CREATE INDEX ix_transaction_correlation_id ON transaction (correlation_id);
CREATE INDEX ix_transaction_type ON transaction (type);
CREATE INDEX ix_transaction_currency ON transaction (currency);

CREATE TABLE ledger_event (
    id              varchar(36)   NOT NULL,
    transaction_id  varchar(36)   NOT NULL,
    ledger_account_id varchar(36) NOT NULL,
    account_id      varchar(36)   NOT NULL,
    direction       varchar(10)   NOT NULL,
    amount          numeric(19,3) NOT NULL,
    balance_before  numeric(19,3) NOT NULL,
    balance_after   numeric(19,3) NOT NULL,
    currency        varchar(3)    NOT NULL,
    created_at      timestamptz   NOT NULL,
    reference       varchar(24)   NOT NULL,
    CONSTRAINT pk_ledger_event PRIMARY KEY (id),
    CONSTRAINT ck_ledger_event_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_ledger_event_amount_positive CHECK (amount > 0)
);

CREATE INDEX ix_ledger_event_transaction_id ON ledger_event (transaction_id);
CREATE INDEX ix_ledger_event_ledger_account_id ON ledger_event (ledger_account_id);
CREATE INDEX ix_ledger_event_account_id ON ledger_event (account_id);
CREATE INDEX ix_ledger_event_created_at ON ledger_event (created_at);

CREATE TABLE transaction_event_marker (
    id             varchar(36) NOT NULL,
    topic          varchar(60) NOT NULL,
    event_id       varchar(64) NOT NULL,
    transaction_id varchar(36) NOT NULL,
    processed_at   timestamptz NOT NULL,
    CONSTRAINT pk_transaction_event_marker PRIMARY KEY (id),
    CONSTRAINT uk_transaction_event_marker_topic_event UNIQUE (topic, event_id)
);

CREATE INDEX ix_transaction_event_marker_transaction_id ON transaction_event_marker (transaction_id);

CREATE TABLE outbox_event (
    id           varchar(36) NOT NULL,
    topic        varchar(60) NOT NULL,
    event_key    varchar(64) NOT NULL,
    payload      jsonb       NOT NULL,
    created_at   timestamptz NOT NULL,
    published_at timestamptz,
    attempts     integer     NOT NULL DEFAULT 0,
    CONSTRAINT pk_outbox_event PRIMARY KEY (id)
);

CREATE INDEX ix_outbox_event_unpublished ON outbox_event (created_at) WHERE published_at IS NULL;

CREATE TABLE reference_sequence (
    sequence_date date   NOT NULL,
    last_value    bigint NOT NULL,
    CONSTRAINT pk_reference_sequence PRIMARY KEY (sequence_date)
);