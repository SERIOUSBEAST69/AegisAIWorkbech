-- Traceability hardening backfill for demo datasets

-- 1) governance_event: bind pseudo/empty user rows to company secops/admin
UPDATE governance_event ge
LEFT JOIN sys_user su_name
  ON su_name.company_id = ge.company_id
 AND LOWER(su_name.username) = LOWER(COALESCE(ge.username, ''))
LEFT JOIN sys_user su_secops
  ON su_secops.company_id = ge.company_id
 AND su_secops.username = 'secops'
LEFT JOIN sys_user su_admin
  ON su_admin.company_id = ge.company_id
 AND su_admin.username = 'admin'
SET ge.user_id = COALESCE(su_name.id, su_secops.id, su_admin.id),
    ge.username = COALESCE(su_name.username, su_secops.username, su_admin.username)
WHERE ge.user_id IS NULL
   OR ge.username IS NULL
   OR LOWER(ge.username) IN ('system', 'anonymous', '匿名');

-- 2) security_event: keep only resolvable employee ids (canonical username)
UPDATE security_event se
JOIN sys_user su
  ON su.company_id = se.company_id
 AND LOWER(su.username) = LOWER(se.employee_id)
SET se.employee_id = su.username;

-- 3) client_report: canonicalize os_username to existing account username
UPDATE client_report cr
JOIN sys_user su
  ON su.company_id = cr.company_id
 AND LOWER(su.username) = LOWER(cr.os_username)
SET cr.os_username = su.username;
