CREATE TABLE imps_transactions (
    id UUID NOT NULL,
    imps_reference_number VARCHAR(255) NOT NULL,
    customer_id VARCHAR(255) NOT NULL,
    transfer_mode VARCHAR(255) NOT NULL,
    sender_account_number VARCHAR(255) NOT NULL,
    sender_mobile VARCHAR(255),
    beneficiary_account_number VARCHAR(255),
    beneficiary_ifsc VARCHAR(255),
    beneficiary_mobile VARCHAR(255),
    beneficiary_mmid VARCHAR(255),
    beneficiary_name VARCHAR(255) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    remarks VARCHAR(255),
    status VARCHAR(255) NOT NULL,
    initiated_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6),
    failure_reason VARCHAR(255),
    rrn VARCHAR(255),
    CONSTRAINT pk_imps_transactions PRIMARY KEY (id),
    CONSTRAINT uk_imps_transactions_reference UNIQUE (imps_reference_number),
    CONSTRAINT ck_imps_transactions_status CHECK (status IN ('INITIATED','PROCESSING','COMPLETED','FAILED','RECONCILIATION_REQUIRED')),
    CONSTRAINT ck_imps_transactions_transfer_mode CHECK (transfer_mode IN ('ACCOUNT_IFSC','MOBILE_MMID'))
);

CREATE TABLE mmid_registrations (
    id UUID NOT NULL,
    customer_id VARCHAR(255) NOT NULL,
    account_number VARCHAR(255) NOT NULL,
    mobile_number VARCHAR(255) NOT NULL,
    mmid VARCHAR(7) NOT NULL,
    active BOOLEAN NOT NULL,
    CONSTRAINT pk_mmid_registrations PRIMARY KEY (id),
    CONSTRAINT uk_mmid_registrations_mobile UNIQUE (mobile_number),
    CONSTRAINT uk_mmid_registrations_mmid UNIQUE (mmid)
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
