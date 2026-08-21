CREATE TABLE branches (
    id BINARY(16) NOT NULL,
    branch_code VARCHAR(255) NOT NULL,
    branch_name VARCHAR(255) NOT NULL,
    city VARCHAR(255),
    state VARCHAR(255),
    branch_phone VARCHAR(255),
    is_active BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_branches_branch_code UNIQUE (branch_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
