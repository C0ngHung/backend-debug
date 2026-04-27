
-- 1. ROLES
INSERT INTO tbl_role (name) VALUES ('ADMIN');
INSERT INTO tbl_role (name) VALUES ('USER');
INSERT INTO tbl_role (name) VALUES ('MANAGER');

-- 2. PERMISSIONS
INSERT INTO tbl_permission (name) VALUES ('USER_READ');
INSERT INTO tbl_permission (name) VALUES ('USER_WRITE');
INSERT INTO tbl_permission (name) VALUES ('TRANSFER_EXECUTE');

-- 3. ROLE_HAS_PERMISSION
INSERT INTO tbl_role_has_permission (role_id, permission_id)
    SELECT r.id, p.id FROM tbl_role r, tbl_permission p WHERE r.name = 'ADMIN';

-- MANAGER can read users + execute transfers
INSERT INTO tbl_role_has_permission (role_id, permission_id)
    SELECT r.id, p.id FROM tbl_role r, tbl_permission p
    WHERE r.name = 'MANAGER' AND p.name IN ('USER_READ', 'TRANSFER_EXECUTE');

-- USER can only read
INSERT INTO tbl_role_has_permission (role_id, permission_id)
    SELECT r.id, p.id FROM tbl_role r, tbl_permission p
    WHERE r.name = 'USER' AND p.name = 'USER_READ';

-- 4. USERS ("password123")
INSERT INTO tbl_user (first_name, last_name, gender, email, username, password, status, user_type)
VALUES ('Nguyen Van', 'Admin', 'MALE', 'admin@smartosc.com', 'admin',
        '$2a$10$dXJ3SW6G7P50lGmMQgel2u7p8V5YFqKBGqL1dKFTVvaG6z5AxTO2G', 'ACTIVE', 'ADMIN');

INSERT INTO tbl_user (first_name, last_name, gender, email, username, password, status, user_type)
VALUES ('Tran Thi', 'Binh', 'FEMALE', 'binh@smartosc.com', 'binh',
        '$2a$10$dXJ3SW6G7P50lGmMQgel2u7p8V5YFqKBGqL1dKFTVvaG6z5AxTO2G', 'ACTIVE', 'USER');

INSERT INTO tbl_user (first_name, last_name, gender, email, username, password, status, user_type)
VALUES ('Le Minh', 'Cuong', 'MALE', 'cuong@smartosc.com', 'cuong',
        '$2a$10$dXJ3SW6G7P50lGmMQgel2u7p8V5YFqKBGqL1dKFTVvaG6z5AxTO2G', 'ACTIVE', 'USER');

-- 5. USER_HAS_ROLE
INSERT INTO tbl_user_has_role (user_id, role_id)
    SELECT u.id, r.id FROM tbl_user u, tbl_role r WHERE u.username = 'admin' AND r.name = 'ROLE_ADMIN';

INSERT INTO tbl_user_has_role (user_id, role_id)
    SELECT u.id, r.id FROM tbl_user u, tbl_role r WHERE u.username = 'binh' AND r.name = 'ROLE_USER';

INSERT INTO tbl_user_has_role (user_id, role_id)
    SELECT u.id, r.id FROM tbl_user u, tbl_role r WHERE u.username = 'cuong' AND r.name = 'ROLE_MANAGER';

-- 6. ACCOUNTS
INSERT INTO tbl_account (account_number, owner_name, balance, status)
VALUES ('ACC001', 'Nguyen Van Admin', 10000000.00, 'ACTIVE');

INSERT INTO tbl_account (account_number, owner_name, balance, status)
VALUES ('ACC002', 'Tran Thi Binh', 5000000.00, 'ACTIVE');

INSERT INTO tbl_account (account_number, owner_name, balance, status)
VALUES ('ACC003', 'Le Minh Cuong', 8000000.00, 'ACTIVE');

-- Partner bank account
INSERT INTO tbl_account (account_number, owner_name, balance, status)
VALUES ('PARTNER999', 'Doi Tac Ngan Hang Y', 0.00, 'ACTIVE');
