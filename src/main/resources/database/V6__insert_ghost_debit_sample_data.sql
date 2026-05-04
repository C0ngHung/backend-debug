INSERT INTO CORE_ACCOUNT (account_no, available_balance)
VALUES ('100000001', 10000000);

COMMENT ON COLUMN CORE_ACCOUNT.available_balance IS 'So du ban dau: 10,000,000 VND. Sau bug se bi tru thanh 8,000,000 (sai) thay vi 9,000,000 (dung)';

COMMIT;
