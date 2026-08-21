CREATE TABLE rtgs_transactions (
    id UUID NOT NULL,
    rtgs_reference_number VARCHAR(255) NOT NULL,
    customer_id VARCHAR(255) NOT NULL,
    sender_account_number VARCHAR(255) NOT NULL,
    sender_ifsc VARCHAR(255) NOT NULL,
    beneficiary_account_number VARCHAR(255) NOT NULL,
    beneficiary_ifsc VARCHAR(255) NOT NULL,
    beneficiary_name VARCHAR(255) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    purpose VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    initiated_at TIMESTAMP(6) NOT NULL,
    settled_at TIMESTAMP(6),
    failure_reason VARCHAR(255),
    rbi_utr_number VARCHAR(255),
    CONSTRAINT pk_rtgs_transactions PRIMARY KEY (id),
    CONSTRAINT uk_rtgs_transactions_reference UNIQUE (rtgs_reference_number),
    CONSTRAINT ck_rtgs_transactions_purpose CHECK (purpose IN ('BUSINESS_PAYMENT','PROPERTY_PURCHASE','LOAN_REPAYMENT','CAPITAL_MARKET','TRADE_SETTLEMENT','OTHER')),
    CONSTRAINT ck_rtgs_transactions_status CHECK (status IN ('INITIATED','VALIDATING','PROCESSING','COMPLETED','FAILED','RETURNED','RECONCILIATION_REQUIRED'))
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
