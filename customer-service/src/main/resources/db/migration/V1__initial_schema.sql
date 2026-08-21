CREATE TABLE customers (
    id BINARY(16) NOT NULL,
    customer_id VARCHAR(255) NOT NULL,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    mobile VARCHAR(255) NOT NULL,
    pan_number VARCHAR(255) NOT NULL,
    aadhaar_number VARCHAR(255) NOT NULL,
    date_of_birth DATE NOT NULL,
    kyc_status ENUM('PENDING','IN_PROGRESS','VERIFIED','REJECTED') NOT NULL,
    risk_rating ENUM('LOW','MEDIUM','HIGH') NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_customers_customer_id UNIQUE (customer_id),
    CONSTRAINT uk_customers_email UNIQUE (email),
    CONSTRAINT uk_customers_mobile UNIQUE (mobile)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE kyc_documents (
    id BINARY(16) NOT NULL,
    customer_id BINARY(16) NOT NULL,
    document_type ENUM('PAN','AADHAAR','PASSPORT','DRIVING_LICENSE') NOT NULL,
    document_number VARCHAR(255) NOT NULL,
    status ENUM('PENDING','VERIFIED','REJECTED') NOT NULL,
    verified_at DATETIME(6),
    submitted_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_kyc_documents_customer FOREIGN KEY (customer_id) REFERENCES customers (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
