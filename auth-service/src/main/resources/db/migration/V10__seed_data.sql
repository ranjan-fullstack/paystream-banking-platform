-- Demo/reference seed data. Uses INSERT IGNORE throughout so this migration is
-- safe to apply to an environment that already has overlapping manually-created
-- data (matching natural keys: username, branch_code) — those rows are left as
-- they are, only genuinely missing rows get created.
--
-- Reserved id range 9000+ for seeded users, to avoid colliding with ids assigned
-- to organically-registered users.

-- Admin (password: Admin@1234)
INSERT IGNORE INTO users (id, username, password, role, is_first_login) VALUES
(9000, 'admin', '$2a$10$KyJ5jai6XFJfLTG0BkgwaOqhKcqi5Q6torKE7IigdD73.7MfJgnD.', 'ADMIN', FALSE);

-- Branches
INSERT IGNORE INTO branches (id, branch_code, branch_name, city, state, branch_phone, is_active, created_at) VALUES
(UUID_TO_BIN(UUID(), 0), 'PAYS0001', 'PayStream Chennai Main Branch', 'Chennai', 'Tamil Nadu', '044-12345678', TRUE, NOW()),
(UUID_TO_BIN(UUID(), 0), 'PAYS0002', 'PayStream Mumbai Branch', 'Mumbai', 'Maharashtra', '022-12345678', TRUE, NOW()),
(UUID_TO_BIN(UUID(), 0), 'PAYS0003', 'PayStream Delhi Branch', 'Delhi', 'Delhi', '011-12345678', TRUE, NOW());

-- Branch managers (password: Manager@1234 — same hash reused across all three demo managers)
INSERT IGNORE INTO users (id, username, password, role, branch_code, employee_id, is_first_login) VALUES
(9001, 'manager_chennai', '$2a$10$UdrbQPxvv7zUFax2UbSJ3e1.HnlRWbXXesOOivUocGSd3Z3q.5Rde', 'BRANCH_MANAGER', 'PAYS0001', 'EMP001', FALSE),
(9002, 'manager_mumbai', '$2a$10$UdrbQPxvv7zUFax2UbSJ3e1.HnlRWbXXesOOivUocGSd3Z3q.5Rde', 'BRANCH_MANAGER', 'PAYS0002', 'EMP002', FALSE),
(9003, 'manager_delhi', '$2a$10$UdrbQPxvv7zUFax2UbSJ3e1.HnlRWbXXesOOivUocGSd3Z3q.5Rde', 'BRANCH_MANAGER', 'PAYS0003', 'EMP003', FALSE);

-- Demo customer john_doe (password: TempPass@1234, must change it on first login)
INSERT IGNORE INTO users (id, username, password, role, is_first_login, created_by_branch, created_by_manager) VALUES
(9004, 'john_doe', '$2a$10$j7uwSZEHKY9Rl0j8GRc4M.L1u7Yeu6mDlBfZri6nVqpYHGyWE.n.y', 'CUSTOMER', TRUE, 'PAYS0001', 'EMP001');
