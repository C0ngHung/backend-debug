-- =====================================================
-- Fix 5: CHAR(16) → VARCHAR2(32) + add UNKNOWN status
-- Prevents space-padding confusion during debugging
-- =====================================================

-- Step 1: Drop the old CHECK constraint on status
ALTER TABLE APP_TRANSFER_REQUEST DROP CONSTRAINT ck_app_transfer_status;

-- Step 2: Add new CHECK constraint including UNKNOWN
ALTER TABLE APP_TRANSFER_REQUEST ADD CONSTRAINT ck_app_transfer_status
    CHECK (status IN ('PROCESSING', 'SUCCESS', 'FAILED', 'UNKNOWN'));

COMMENT ON COLUMN APP_TRANSFER_REQUEST.status IS 'Trang thai giao dich: PROCESSING, SUCCESS, FAILED, UNKNOWN. UNKNOWN = timeout xay ra, Core co the da tru tien nhung App chua xac nhan';

-- Step 3: Migrate external_ref from CHAR(16) to VARCHAR2(32)
ALTER TABLE APP_TRANSFER_REQUEST ADD external_ref_new VARCHAR2(32);
UPDATE APP_TRANSFER_REQUEST SET external_ref_new = TRIM(external_ref);
ALTER TABLE APP_TRANSFER_REQUEST DROP COLUMN external_ref;
ALTER TABLE APP_TRANSFER_REQUEST RENAME COLUMN external_ref_new TO external_ref;
ALTER TABLE APP_TRANSFER_REQUEST MODIFY external_ref NOT NULL;

COMMENT ON COLUMN APP_TRANSFER_REQUEST.external_ref IS 'Ma tham chieu tu doi tac. Da chuyen tu CHAR(16) sang VARCHAR2(32) de tranh loi padding khoang trang';

-- Step 4: Migrate business_ref from CHAR(16) to VARCHAR2(32)
ALTER TABLE CORE_DEBIT_LOG ADD business_ref_new VARCHAR2(32);
UPDATE CORE_DEBIT_LOG SET business_ref_new = TRIM(business_ref);
ALTER TABLE CORE_DEBIT_LOG DROP COLUMN business_ref;
ALTER TABLE CORE_DEBIT_LOG RENAME COLUMN business_ref_new TO business_ref;
ALTER TABLE CORE_DEBIT_LOG MODIFY business_ref NOT NULL;

COMMENT ON COLUMN CORE_DEBIT_LOG.business_ref IS 'Ma tham chieu nghiep vu. Da chuyen tu CHAR(16) sang VARCHAR2(32) de tranh loi padding khoang trang';

COMMIT;
