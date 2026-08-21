CREATE TABLE notification_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    customer_id VARCHAR(255) NOT NULL,
    channel ENUM('SMS','EMAIL') NOT NULL,
    message VARCHAR(1000) NOT NULL,
    status ENUM('SENT','FAILED') NOT NULL,
    sent_at DATETIME(6) NOT NULL,
    reference_id VARCHAR(255),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
