CREATE TABLE accounts (
    id             varchar(36)   NOT NULL,
    user_id        varchar(36)   NOT NULL,
    account_number varchar(34)   NOT NULL,
    account_type   varchar(20)   NOT NULL,
    currency       varchar(3)    NOT NULL,
    balance        numeric(19,3) NOT NULL,
    status         varchar(20)   NOT NULL,
    iban           varchar(34)   NULL,
    nickname       varchar(40)   NULL,
    created_at     timestamptz   NOT NULL,
    updated_at     timestamptz   NOT NULL,
    version        bigint        NOT NULL DEFAULT 0,
    CONSTRAINT pk_accounts PRIMARY KEY (id),
    CONSTRAINT uq_accounts_account_number UNIQUE (account_number),
    CONSTRAINT uq_accounts_user_type_currency UNIQUE (user_id, account_type, currency),
    CONSTRAINT ck_accounts_account_type CHECK (account_type IN ('CHECKING', 'SAVINGS')),
    CONSTRAINT ck_accounts_currency CHECK (currency IN ('TND', 'EUR', 'USD')),
    CONSTRAINT ck_accounts_status CHECK (status IN ('ACTIVE', 'BLOCKED', 'CLOSED')),
    CONSTRAINT ck_accounts_balance_non_negative CHECK (balance >= 0)
);

CREATE INDEX idx_accounts_user_id ON accounts (user_id);
CREATE INDEX idx_accounts_account_number ON accounts (account_number);
CREATE INDEX idx_accounts_status ON accounts (status);
CREATE INDEX idx_accounts_created_at ON accounts (created_at);

CREATE TABLE balance_projection (
    transaction_id varchar(36) NOT NULL,
    applied_at     timestamptz NOT NULL,
    CONSTRAINT pk_balance_projection PRIMARY KEY (transaction_id)
);
