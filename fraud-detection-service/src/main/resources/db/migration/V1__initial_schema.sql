CREATE TABLE fraud_alerts (
    id UUID NOT NULL,
    account_number VARCHAR(255) NOT NULL,
    transaction_reference VARCHAR(255) NOT NULL,
    rule_triggered VARCHAR(255) NOT NULL,
    risk_score INTEGER NOT NULL,
    action VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    review_remarks VARCHAR(255),
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_fraud_alerts PRIMARY KEY (id),
    CONSTRAINT ck_fraud_alerts_action CHECK (action IN ('ALERT','BLOCK','REVIEW')),
    CONSTRAINT ck_fraud_alerts_status CHECK (status IN ('OPEN','REVIEWED','CLOSED'))
);

CREATE TABLE fraud_rules (
    id UUID NOT NULL,
    rule_name VARCHAR(255) NOT NULL,
    rule_type VARCHAR(255) NOT NULL,
    threshold NUMERIC(19,4) NOT NULL,
    action VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL,
    CONSTRAINT pk_fraud_rules PRIMARY KEY (id),
    CONSTRAINT uk_fraud_rules_rule_name UNIQUE (rule_name),
    CONSTRAINT ck_fraud_rules_action CHECK (action IN ('ALERT','BLOCK','REVIEW')),
    CONSTRAINT ck_fraud_rules_rule_type CHECK (rule_type IN ('VELOCITY','AMOUNT_ANOMALY','ODD_HOURS','DUPLICATE_TRANSACTION','HIGH_RISK_ACCOUNT','DAILY_LIMIT_BREACH','CTR','STRUCTURING'))
);

CREATE TABLE transaction_logs (
    id UUID NOT NULL,
    account_number VARCHAR(255) NOT NULL,
    beneficiary_account_number VARCHAR(255) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    payment_mode VARCHAR(255) NOT NULL,
    payment_reference_number VARCHAR(255) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_transaction_logs PRIMARY KEY (id),
    CONSTRAINT uk_transaction_logs_reference UNIQUE (payment_reference_number)
);
