-- Physical dedupe for traceable governance datasets (company_id=1)
-- Keep at most 15 unique records per module, preferring traceable + latest rows.

SET @target_company := 1;

START TRANSACTION;

-- subject_request: dedupe by user/type/status/comment(without TRACE)/result
DROP TEMPORARY TABLE IF EXISTS tmp_subject_keep;
CREATE TEMPORARY TABLE tmp_subject_keep (id BIGINT PRIMARY KEY);
INSERT INTO tmp_subject_keep (id)
SELECT t.id
FROM (
  SELECT d.id,
         ROW_NUMBER() OVER (ORDER BY d.has_trace DESC, d.ts DESC, d.id DESC) AS ord
  FROM (
    SELECT sr.id,
           CASE WHEN INSTR(COALESCE(sr.comment, ''), '[TRACE') > 0 THEN 1 ELSE 0 END AS has_trace,
           COALESCE(sr.update_time, sr.create_time) AS ts,
           ROW_NUMBER() OVER (
             PARTITION BY sr.company_id,
                          COALESCE(sr.user_id, 0),
                          LOWER(COALESCE(sr.type, '')),
                          LOWER(COALESCE(sr.status, '')),
                          LOWER(TRIM(REGEXP_REPLACE(COALESCE(sr.comment, ''), '\\[TRACE[^\\]]*\\]', ''))),
                          LOWER(TRIM(COALESCE(sr.result, '')))
             ORDER BY CASE WHEN INSTR(COALESCE(sr.comment, ''), '[TRACE') > 0 THEN 1 ELSE 0 END DESC,
                      COALESCE(sr.update_time, sr.create_time) DESC,
                      sr.id DESC
           ) AS rn
    FROM subject_request sr
    WHERE sr.company_id = @target_company
  ) d
  WHERE d.rn = 1
) t
WHERE t.ord <= 15;
DELETE FROM subject_request
WHERE company_id = @target_company
  AND id NOT IN (SELECT id FROM tmp_subject_keep);

-- risk_event: dedupe by type/level/status/related_log_id/audit_log_ids/process_log(without TRACE)
DROP TEMPORARY TABLE IF EXISTS tmp_risk_keep;
CREATE TEMPORARY TABLE tmp_risk_keep (id BIGINT PRIMARY KEY);
INSERT INTO tmp_risk_keep (id)
SELECT t.id
FROM (
  SELECT d.id,
         ROW_NUMBER() OVER (ORDER BY d.has_trace DESC, d.ts DESC, d.id DESC) AS ord
  FROM (
    SELECT re.id,
           CASE WHEN INSTR(COALESCE(re.process_log, ''), '[TRACE') > 0 THEN 1 ELSE 0 END AS has_trace,
           COALESCE(re.update_time, re.create_time) AS ts,
           ROW_NUMBER() OVER (
             PARTITION BY re.company_id,
                          LOWER(COALESCE(re.type, '')),
                          UPPER(COALESCE(re.level, '')),
                          LOWER(COALESCE(re.status, '')),
                          COALESCE(re.related_log_id, 0),
                          COALESCE(re.audit_log_ids, ''),
                          LOWER(TRIM(REGEXP_REPLACE(COALESCE(re.process_log, ''), '\\[TRACE[^\\]]*\\]', '')))
             ORDER BY CASE WHEN INSTR(COALESCE(re.process_log, ''), '[TRACE') > 0 THEN 1 ELSE 0 END DESC,
                      COALESCE(re.update_time, re.create_time) DESC,
                      re.id DESC
           ) AS rn
    FROM risk_event re
    WHERE re.company_id = @target_company
  ) d
  WHERE d.rn = 1
) t
WHERE t.ord <= 15;
DELETE FROM risk_event
WHERE company_id = @target_company
  AND id NOT IN (SELECT id FROM tmp_risk_keep);

-- security_event: dedupe by event dimensions
DROP TEMPORARY TABLE IF EXISTS tmp_security_keep;
CREATE TEMPORARY TABLE tmp_security_keep (id BIGINT PRIMARY KEY);
INSERT INTO tmp_security_keep (id)
SELECT t.id
FROM (
  SELECT d.id,
         ROW_NUMBER() OVER (ORDER BY d.has_trace DESC, d.ts DESC, d.id DESC) AS ord
  FROM (
    SELECT se.id,
           CASE
             WHEN COALESCE(se.employee_id, '') <> ''
              AND COALESCE(se.hostname, '') <> ''
              AND COALESCE(se.file_path, '') <> '' THEN 1
             ELSE 0
           END AS has_trace,
           COALESCE(se.event_time, se.update_time, se.create_time) AS ts,
           ROW_NUMBER() OVER (
             PARTITION BY se.company_id,
                          UPPER(COALESCE(se.event_type, '')),
                          LOWER(COALESCE(se.severity, '')),
                          LOWER(COALESCE(se.status, '')),
                          LOWER(COALESCE(se.employee_id, '')),
                          LOWER(COALESCE(se.hostname, '')),
                          LOWER(COALESCE(se.file_path, '')),
                          LOWER(COALESCE(se.target_addr, ''))
             ORDER BY COALESCE(se.event_time, se.update_time, se.create_time) DESC,
                      se.id DESC
           ) AS rn
    FROM security_event se
    WHERE se.company_id = @target_company
  ) d
  WHERE d.rn = 1
) t
WHERE t.ord <= 15;
DELETE FROM security_event
WHERE company_id = @target_company
  AND id NOT IN (SELECT id FROM tmp_security_keep);

-- approval_request: dedupe by applicant/asset/status/reason(without TRACE)/approver
DROP TEMPORARY TABLE IF EXISTS tmp_approval_keep;
CREATE TEMPORARY TABLE tmp_approval_keep (id BIGINT PRIMARY KEY);
INSERT INTO tmp_approval_keep (id)
SELECT t.id
FROM (
  SELECT d.id,
         ROW_NUMBER() OVER (ORDER BY d.has_trace DESC, d.ts DESC, d.id DESC) AS ord
  FROM (
    SELECT ar.id,
           CASE WHEN INSTR(COALESCE(ar.reason, ''), '[TRACE') > 0 THEN 1 ELSE 0 END AS has_trace,
           COALESCE(ar.update_time, ar.create_time) AS ts,
           ROW_NUMBER() OVER (
             PARTITION BY ar.company_id,
                          COALESCE(ar.applicant_id, 0),
                          COALESCE(ar.asset_id, 0),
                          LOWER(COALESCE(ar.status, '')),
                          LOWER(TRIM(REGEXP_REPLACE(COALESCE(ar.reason, ''), '\\[TRACE[^\\]]*\\]', ''))),
                          COALESCE(ar.approver_id, 0)
             ORDER BY CASE WHEN INSTR(COALESCE(ar.reason, ''), '[TRACE') > 0 THEN 1 ELSE 0 END DESC,
                      COALESCE(ar.update_time, ar.create_time) DESC,
                      ar.id DESC
           ) AS rn
    FROM approval_request ar
    WHERE ar.company_id = @target_company
  ) d
  WHERE d.rn = 1
) t
WHERE t.ord <= 15;
DELETE FROM approval_request
WHERE company_id = @target_company
  AND id NOT IN (SELECT id FROM tmp_approval_keep);

COMMIT;
