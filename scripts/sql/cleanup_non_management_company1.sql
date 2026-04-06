-- Cleanup non-management module data completeness for company_id=1
-- Rules applied:
-- 1) Null-critical rows removed
-- 2) Invalid/garbled placeholder rows removed
-- 3) Orphan-reference rows removed
-- 4) Duplicates removed (keep latest)

SET @target_company := 1;

START TRANSACTION;

-- 0) Backup snapshots for rollback analysis (lightweight counts can be compared separately)
--   Full SQL dump backup already exists in docs/dedupe_pre_backup_2026-04-05.sql

-- 1) subject_request
DELETE FROM subject_request
WHERE company_id = @target_company
  AND (
    user_id IS NULL
    OR type IS NULL OR TRIM(type) = ''
    OR status IS NULL OR TRIM(status) = ''
  );

DELETE sr FROM subject_request sr
LEFT JOIN sys_user u ON u.id = sr.user_id AND u.company_id = sr.company_id
WHERE sr.company_id = @target_company
  AND u.id IS NULL;

DELETE sr FROM subject_request sr
LEFT JOIN sys_user h ON h.id = sr.handler_id AND h.company_id = sr.company_id
WHERE sr.company_id = @target_company
  AND sr.handler_id IS NOT NULL
  AND h.id IS NULL;

DELETE FROM subject_request
WHERE company_id = @target_company
  AND (
    LOWER(COALESCE(comment, '')) REGEXP '(^|[[:space:]])(n/?a|null|undefined|unknown)([[:space:]]|$)'
    OR LOWER(COALESCE(result, '')) REGEXP '(^|[[:space:]])(n/?a|null|undefined|unknown)([[:space:]]|$)'
    OR COALESCE(comment, '') REGEXP '[Ã¦Ã§Ã¨Ã©Ã¯Ã¼]{2,}'
    OR COALESCE(result, '') REGEXP '[Ã¦Ã§Ã¨Ã©Ã¯Ã¼]{2,}'
  );

DROP TEMPORARY TABLE IF EXISTS tmp_subject_dedupe_keep;
CREATE TEMPORARY TABLE tmp_subject_dedupe_keep (id BIGINT PRIMARY KEY);
INSERT INTO tmp_subject_dedupe_keep (id)
SELECT id FROM (
  SELECT sr.id,
         ROW_NUMBER() OVER (
           PARTITION BY sr.company_id,
                        sr.user_id,
                        LOWER(COALESCE(sr.type,'')),
                        LOWER(COALESCE(sr.status,'')),
                        LOWER(TRIM(REGEXP_REPLACE(COALESCE(sr.comment,''), '\\[TRACE[^\\]]*\\]', ''))),
                        LOWER(TRIM(COALESCE(sr.result,'')))
           ORDER BY COALESCE(sr.update_time, sr.create_time) DESC, sr.id DESC
         ) AS rn
  FROM subject_request sr
  WHERE sr.company_id = @target_company
) t
WHERE t.rn = 1;

DELETE FROM subject_request
WHERE company_id = @target_company
  AND id NOT IN (SELECT id FROM tmp_subject_dedupe_keep);

-- 2) risk_event
DELETE FROM risk_event
WHERE company_id = @target_company
  AND (
    type IS NULL OR TRIM(type) = ''
    OR level IS NULL OR TRIM(level) = ''
    OR status IS NULL OR TRIM(status) = ''
  );

DELETE re FROM risk_event re
LEFT JOIN sys_user h ON h.id = re.handler_id AND h.company_id = re.company_id
WHERE re.company_id = @target_company
  AND re.handler_id IS NOT NULL
  AND h.id IS NULL;

DELETE FROM risk_event
WHERE company_id = @target_company
  AND (
    LOWER(COALESCE(process_log, '')) REGEXP '(^|[[:space:]])(n/?a|null|undefined|unknown)([[:space:]]|$)'
    OR COALESCE(process_log, '') REGEXP '[Ã¦Ã§Ã¨Ã©Ã¯Ã¼]{2,}'
  );

DROP TEMPORARY TABLE IF EXISTS tmp_risk_dedupe_keep;
CREATE TEMPORARY TABLE tmp_risk_dedupe_keep (id BIGINT PRIMARY KEY);
INSERT INTO tmp_risk_dedupe_keep (id)
SELECT id FROM (
  SELECT re.id,
         ROW_NUMBER() OVER (
           PARTITION BY re.company_id,
                        LOWER(COALESCE(re.type,'')),
                        UPPER(COALESCE(re.level,'')),
                        LOWER(COALESCE(re.status,'')),
                        COALESCE(re.related_log_id,0),
                        COALESCE(re.audit_log_ids,''),
                        LOWER(TRIM(REGEXP_REPLACE(COALESCE(re.process_log,''), '\\[TRACE[^\\]]*\\]', '')))
           ORDER BY COALESCE(re.update_time, re.create_time) DESC, re.id DESC
         ) AS rn
  FROM risk_event re
  WHERE re.company_id = @target_company
) t
WHERE t.rn = 1;

DELETE FROM risk_event
WHERE company_id = @target_company
  AND id NOT IN (SELECT id FROM tmp_risk_dedupe_keep);

-- 3) security_event
DELETE FROM security_event
WHERE company_id = @target_company
  AND (
    event_type IS NULL OR TRIM(event_type) = ''
    OR severity IS NULL OR TRIM(severity) = ''
    OR status IS NULL OR TRIM(status) = ''
    OR employee_id IS NULL OR TRIM(employee_id) = ''
    OR hostname IS NULL OR TRIM(hostname) = ''
  );

DELETE se FROM security_event se
LEFT JOIN sys_user op ON op.id = se.operator_id AND op.company_id = se.company_id
WHERE se.company_id = @target_company
  AND se.operator_id IS NOT NULL
  AND op.id IS NULL;

DELETE FROM security_event
WHERE company_id = @target_company
  AND (
    LOWER(COALESCE(file_path, '')) REGEXP '(^|[[:space:]])(n/?a|null|undefined|unknown)([[:space:]]|$)'
    OR LOWER(COALESCE(target_addr, '')) REGEXP '(^|[[:space:]])(n/?a|null|undefined|unknown)([[:space:]]|$)'
    OR COALESCE(file_path, '') REGEXP '[Ã¦Ã§Ã¨Ã©Ã¯Ã¼]{2,}'
  );

DROP TEMPORARY TABLE IF EXISTS tmp_security_dedupe_keep;
CREATE TEMPORARY TABLE tmp_security_dedupe_keep (id BIGINT PRIMARY KEY);
INSERT INTO tmp_security_dedupe_keep (id)
SELECT id FROM (
  SELECT se.id,
         ROW_NUMBER() OVER (
           PARTITION BY se.company_id,
                        UPPER(COALESCE(se.event_type,'')),
                        LOWER(COALESCE(se.severity,'')),
                        LOWER(COALESCE(se.status,'')),
                        LOWER(COALESCE(se.employee_id,'')),
                        LOWER(COALESCE(se.hostname,'')),
                        LOWER(COALESCE(se.file_path,'')),
                        LOWER(COALESCE(se.target_addr,''))
           ORDER BY COALESCE(se.event_time, se.update_time, se.create_time) DESC, se.id DESC
         ) AS rn
  FROM security_event se
  WHERE se.company_id = @target_company
) t
WHERE t.rn = 1;

DELETE FROM security_event
WHERE company_id = @target_company
  AND id NOT IN (SELECT id FROM tmp_security_dedupe_keep);

-- 4) approval_request
DELETE FROM approval_request
WHERE company_id = @target_company
  AND (
    applicant_id IS NULL
    OR status IS NULL OR TRIM(status) = ''
    OR reason IS NULL OR TRIM(reason) = ''
  );

DELETE ar FROM approval_request ar
LEFT JOIN sys_user a ON a.id = ar.applicant_id AND a.company_id = ar.company_id
WHERE ar.company_id = @target_company
  AND a.id IS NULL;

DELETE ar FROM approval_request ar
LEFT JOIN sys_user ap ON ap.id = ar.approver_id AND ap.company_id = ar.company_id
WHERE ar.company_id = @target_company
  AND ar.approver_id IS NOT NULL
  AND ap.id IS NULL;

DELETE ar FROM approval_request ar
LEFT JOIN data_asset da ON da.id = ar.asset_id AND da.company_id = ar.company_id
WHERE ar.company_id = @target_company
  AND ar.asset_id IS NOT NULL
  AND da.id IS NULL;

DELETE FROM approval_request
WHERE company_id = @target_company
  AND (
    LOWER(COALESCE(reason, '')) REGEXP '(^|[[:space:]])(n/?a|null|undefined|unknown)([[:space:]]|$)'
    OR COALESCE(reason, '') REGEXP '[Ã¦Ã§Ã¨Ã©Ã¯Ã¼]{2,}'
  );

DROP TEMPORARY TABLE IF EXISTS tmp_approval_dedupe_keep;
CREATE TEMPORARY TABLE tmp_approval_dedupe_keep (id BIGINT PRIMARY KEY);
INSERT INTO tmp_approval_dedupe_keep (id)
SELECT id FROM (
  SELECT ar.id,
         ROW_NUMBER() OVER (
           PARTITION BY ar.company_id,
                        ar.applicant_id,
                        COALESCE(ar.asset_id,0),
                        LOWER(COALESCE(ar.status,'')),
                        LOWER(TRIM(REGEXP_REPLACE(COALESCE(ar.reason,''), '\\[TRACE[^\\]]*\\]', ''))),
                        COALESCE(ar.approver_id,0)
           ORDER BY COALESCE(ar.update_time, ar.create_time) DESC, ar.id DESC
         ) AS rn
  FROM approval_request ar
  WHERE ar.company_id = @target_company
) t
WHERE t.rn = 1;

DELETE FROM approval_request
WHERE company_id = @target_company
  AND id NOT IN (SELECT id FROM tmp_approval_dedupe_keep);

COMMIT;

-- Post-check summary
SELECT 'subject_request' AS table_name, COUNT(*) AS kept_rows FROM subject_request WHERE company_id = @target_company
UNION ALL
SELECT 'risk_event' AS table_name, COUNT(*) AS kept_rows FROM risk_event WHERE company_id = @target_company
UNION ALL
SELECT 'security_event' AS table_name, COUNT(*) AS kept_rows FROM security_event WHERE company_id = @target_company
UNION ALL
SELECT 'approval_request' AS table_name, COUNT(*) AS kept_rows FROM approval_request WHERE company_id = @target_company;
