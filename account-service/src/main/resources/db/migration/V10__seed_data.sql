-- Demo bank account for john_doe (auth-service user id 9004, seeded in
-- auth-service's V10 migration). ON CONFLICT DO NOTHING makes this safe to
-- apply even if a row with the same natural key already exists.

INSERT INTO bank_accounts (
    id, account_number, ifsc_code, account_type, balance, available_balance, hold_amount,
    currency, status, customer_id, user_id, branch_code, created_at, updated_at, version
) VALUES (
    '40000000-0000-0000-0000-000000000001',
    '1000000000000001',
    'PAYS0001001',
    'SAVINGS',
    100000.0000, 100000.0000, 0.0000,
    'INR', 'ACTIVE',
    'CUST000001',
    9004,
    'PAYS0001',
    NOW(), NOW(), 0
) ON CONFLICT DO NOTHING;

-- Payment rail configuration: NEFT + RTGS + IMPS enabled, UPI disabled
INSERT INTO account_payment_configs (
    id, account_id,
    neft_enabled, rtgs_enabled, imps_enabled, upi_enabled,
    neft_daily_limit, rtgs_daily_limit, imps_daily_limit, upi_daily_limit,
    neft_per_transaction_limit, rtgs_per_transaction_limit, imps_per_transaction_limit, upi_per_transaction_limit,
    enabled_by, enabled_at
) VALUES (
    '50000000-0000-0000-0000-000000000001',
    '40000000-0000-0000-0000-000000000001',
    TRUE, TRUE, TRUE, FALSE,
    500000.0000, 10000000.0000, 500000.0000, 100000.0000,
    100000.0000, 2000000.0000, 100000.0000, 100000.0000,
    'EMP001', NOW()
) ON CONFLICT DO NOTHING;

-- Per-mode account limits (one row per payment mode)
INSERT INTO account_limits (id, account_id, transaction_type, daily_limit, per_transaction_limit, used_today_amount, reset_at) VALUES
('60000000-0000-0000-0000-000000000001', '40000000-0000-0000-0000-000000000001', 'NEFT', 500000.0000, 100000.0000, 0.0000, NOW()),
('60000000-0000-0000-0000-000000000002', '40000000-0000-0000-0000-000000000001', 'RTGS', 10000000.0000, 2000000.0000, 0.0000, NOW()),
('60000000-0000-0000-0000-000000000003', '40000000-0000-0000-0000-000000000001', 'IMPS', 500000.0000, 100000.0000, 0.0000, NOW()),
('60000000-0000-0000-0000-000000000004', '40000000-0000-0000-0000-000000000001', 'UPI', 100000.0000, 100000.0000, 0.0000, NOW())
ON CONFLICT DO NOTHING;
