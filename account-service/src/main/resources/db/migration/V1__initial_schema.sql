CREATE TABLE bank_accounts (
    id UUID NOT NULL,
    account_number VARCHAR(16) NOT NULL,
    ifsc_code VARCHAR(255) NOT NULL,
    account_type VARCHAR(255) NOT NULL,
    balance NUMERIC(19,4) NOT NULL,
    available_balance NUMERIC(19,4) NOT NULL,
    hold_amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(255) NOT NULL,
    customer_id VARCHAR(255) NOT NULL,
    user_id BIGINT,
    branch_code VARCHAR(255) NOT NULL,
    nominee_name VARCHAR(255),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT,
    CONSTRAINT pk_bank_accounts PRIMARY KEY (id),
    CONSTRAINT uk_bank_accounts_account_number UNIQUE (account_number),
    CONSTRAINT ck_bank_accounts_account_type CHECK (account_type IN ('SAVINGS','CURRENT','SALARY','NRI')),
    CONSTRAINT ck_bank_accounts_status CHECK (status IN ('ACTIVE','INACTIVE','FROZEN','CLOSED'))
);

CREATE TABLE account_limits (
    id UUID NOT NULL,
    account_id UUID NOT NULL,
    transaction_type VARCHAR(255) NOT NULL,
    daily_limit NUMERIC(19,4) NOT NULL,
    per_transaction_limit NUMERIC(19,4) NOT NULL,
    used_today_amount NUMERIC(19,4) NOT NULL,
    reset_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_account_limits PRIMARY KEY (id),
    CONSTRAINT uk_account_limits_account_mode UNIQUE (account_id, transaction_type),
    CONSTRAINT ck_account_limits_transaction_type CHECK (transaction_type IN ('NEFT','RTGS','IMPS','UPI')),
    CONSTRAINT fk_account_limits_account FOREIGN KEY (account_id) REFERENCES bank_accounts (id)
);

CREATE TABLE outbox_events (
    id UUID NOT NULL,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    topic VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    published BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    published_at TIMESTAMP(6),
    CONSTRAINT pk_outbox_events PRIMARY KEY (id)
);

CREATE TABLE payment_dead_letters (
    id UUID NOT NULL,
    original_topic VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    failure_reason TEXT,
    failed_at TIMESTAMP(6) NOT NULL,
    status VARCHAR(255) NOT NULL,
    CONSTRAINT pk_payment_dead_letters PRIMARY KEY (id)
);

CREATE TABLE processed_events (
    id UUID NOT NULL,
    payment_reference_number VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    processed_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_processed_events PRIMARY KEY (id),
    CONSTRAINT uk_processed_events_ref_event UNIQUE (payment_reference_number, event_type)
);
