CREATE TABLE virtual_payment_addresses (
    id UUID NOT NULL,
    vpa VARCHAR(255) NOT NULL,
    customer_id VARCHAR(255) NOT NULL,
    account_number VARCHAR(255) NOT NULL,
    is_default BOOLEAN NOT NULL,
    active BOOLEAN NOT NULL,
    upi_pin VARCHAR(255),
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_virtual_payment_addresses PRIMARY KEY (id),
    CONSTRAINT uk_virtual_payment_addresses_vpa UNIQUE (vpa)
);

CREATE TABLE upi_transactions (
    id UUID NOT NULL,
    upi_transaction_id VARCHAR(255) NOT NULL,
    transaction_type VARCHAR(255) NOT NULL,
    sender_vpa VARCHAR(255) NOT NULL,
    receiver_vpa VARCHAR(255) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    remarks VARCHAR(255),
    status VARCHAR(255) NOT NULL,
    initiated_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6),
    expires_at TIMESTAMP(6),
    npci_transaction_id VARCHAR(255),
    failure_reason VARCHAR(255),
    CONSTRAINT pk_upi_transactions PRIMARY KEY (id),
    CONSTRAINT uk_upi_transactions_txn_id UNIQUE (upi_transaction_id),
    CONSTRAINT ck_upi_transactions_type CHECK (transaction_type IN ('PAY','COLLECT','REFUND')),
    CONSTRAINT ck_upi_transactions_status CHECK (status IN ('INITIATED','PENDING_PIN','PIN_VERIFIED','PROCESSING','COMPLETED','FAILED','EXPIRED','DECLINED','RECONCILIATION_REQUIRED'))
);

CREATE TABLE upi_collect_requests (
    id UUID NOT NULL,
    upi_transaction_id UUID NOT NULL,
    requested_by VARCHAR(255) NOT NULL,
    requested_from VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    status VARCHAR(255) NOT NULL,
    CONSTRAINT pk_upi_collect_requests PRIMARY KEY (id),
    CONSTRAINT uk_upi_collect_requests_txn UNIQUE (upi_transaction_id),
    CONSTRAINT ck_upi_collect_requests_status CHECK (status IN ('PENDING','ACCEPTED','DECLINED','EXPIRED')),
    CONSTRAINT fk_upi_collect_requests_txn FOREIGN KEY (upi_transaction_id) REFERENCES upi_transactions (id)
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

CREATE TABLE processed_events (
    id UUID NOT NULL,
    payment_reference_number VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    processed_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_processed_events PRIMARY KEY (id),
    CONSTRAINT uk_processed_events_ref_event UNIQUE (payment_reference_number, event_type)
);
