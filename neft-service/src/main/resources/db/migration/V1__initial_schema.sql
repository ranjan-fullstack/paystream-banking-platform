CREATE TABLE neft_batches (
    id UUID NOT NULL,
    batch_number VARCHAR(255) NOT NULL,
    scheduled_at TIMESTAMP(6) NOT NULL,
    processed_at TIMESTAMP(6),
    total_transactions INTEGER NOT NULL,
    total_amount NUMERIC(19,4) NOT NULL,
    success_count INTEGER NOT NULL,
    failure_count INTEGER NOT NULL,
    status VARCHAR(255) NOT NULL,
    CONSTRAINT pk_neft_batches PRIMARY KEY (id),
    CONSTRAINT uk_neft_batches_batch_number UNIQUE (batch_number),
    CONSTRAINT ck_neft_batches_status CHECK (status IN ('SCHEDULED','PROCESSING','COMPLETED','FAILED'))
);

CREATE TABLE neft_transactions (
    id UUID NOT NULL,
    neft_reference_number VARCHAR(255) NOT NULL,
    customer_id VARCHAR(255) NOT NULL,
    sender_account_number VARCHAR(255) NOT NULL,
    sender_ifsc VARCHAR(255) NOT NULL,
    beneficiary_account_number VARCHAR(255) NOT NULL,
    beneficiary_ifsc VARCHAR(255) NOT NULL,
    beneficiary_name VARCHAR(255) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    remarks VARCHAR(255),
    status VARCHAR(255) NOT NULL,
    batch_id VARCHAR(255),
    initiated_at TIMESTAMP(6) NOT NULL,
    batch_processed_at TIMESTAMP(6),
    completed_at TIMESTAMP(6),
    failure_reason VARCHAR(255),
    retry_count INTEGER NOT NULL,
    CONSTRAINT pk_neft_transactions PRIMARY KEY (id),
    CONSTRAINT uk_neft_transactions_reference UNIQUE (neft_reference_number),
    CONSTRAINT ck_neft_transactions_status CHECK (status IN ('INITIATED','VALIDATED','QUEUED','BATCH_PROCESSING','COMPLETED','FAILED','RETURNED','RECONCILIATION_REQUIRED'))
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
