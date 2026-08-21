CREATE TABLE audit_logs (
    id UUID NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    entity_type VARCHAR(255) NOT NULL,
    entity_id VARCHAR(255),
    performed_by VARCHAR(255),
    ip_address VARCHAR(255),
    old_value TEXT,
    new_value TEXT,
    "timestamp" TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_audit_logs PRIMARY KEY (id)
);
