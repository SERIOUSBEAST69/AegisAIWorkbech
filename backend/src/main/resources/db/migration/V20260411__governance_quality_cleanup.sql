-- Governance quality cleanup: remove demo markers and deduplicate residues.
-- Safe to run multiple times.

-- 1) Normalize observability seed-style governance events into production wording.
UPDATE governance_event
SET title = '员工行为监控观测事件',
    description = '用于运维观测与员工行为监控联动展示',
    source_event_id = CONCAT('OBS-TRACE-', id)
WHERE title LIKE '[seed]%'
   OR source_event_id LIKE 'SEED-OBS-%';

-- 2) Remove .demo suffix residue from usernames and payload traces.
UPDATE governance_event
SET username = REPLACE(IFNULL(username, ''), '.demo', ''),
    payload_json = REPLACE(IFNULL(payload_json, ''), '.demo', '')
WHERE IFNULL(username, '') LIKE '%.demo%'
   OR IFNULL(payload_json, '') LIKE '%.demo%';

UPDATE ai_call_log
SET username = REPLACE(IFNULL(username, ''), '.demo', ''),
    input_preview = REPLACE(IFNULL(input_preview, ''), '.demo', ''),
    output_preview = REPLACE(IFNULL(output_preview, ''), '.demo', '')
WHERE IFNULL(username, '') LIKE '%.demo%'
   OR IFNULL(input_preview, '') LIKE '%.demo%'
   OR IFNULL(output_preview, '') LIKE '%.demo%';

-- 3) Deduplicate governance_event by business signature, keep smallest id.
DELETE g
FROM governance_event g
JOIN (
  SELECT
    company_id,
    user_id,
    event_type,
    source_module,
    severity,
    status,
    title,
    description,
    DATE_FORMAT(event_time, '%Y-%m-%d %H:%i:%s') AS event_time_key,
    MIN(id) AS keep_id,
    COUNT(*) AS c
  FROM governance_event
  GROUP BY
    company_id,
    user_id,
    event_type,
    source_module,
    severity,
    status,
    title,
    description,
    DATE_FORMAT(event_time, '%Y-%m-%d %H:%i:%s')
  HAVING c > 1
) d
  ON g.company_id <=> d.company_id
 AND g.user_id <=> d.user_id
 AND g.event_type <=> d.event_type
 AND g.source_module <=> d.source_module
 AND g.severity <=> d.severity
 AND g.status <=> d.status
 AND g.title <=> d.title
 AND g.description <=> d.description
 AND DATE_FORMAT(g.event_time, '%Y-%m-%d %H:%i:%s') = d.event_time_key
 AND g.id <> d.keep_id;

-- 4) Deduplicate risk_event by business signature, keep smallest id.
DELETE r
FROM risk_event r
JOIN (
  SELECT
    company_id,
    type,
    level,
    status,
    process_log,
    DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s') AS create_time_key,
    MIN(id) AS keep_id,
    COUNT(*) AS c
  FROM risk_event
  GROUP BY
    company_id,
    type,
    level,
    status,
    process_log,
    DATE_FORMAT(create_time, '%Y-%m-%d %H:%i:%s')
  HAVING c > 1
) d
  ON r.company_id <=> d.company_id
 AND r.type <=> d.type
 AND r.level <=> d.level
 AND r.status <=> d.status
 AND r.process_log <=> d.process_log
 AND DATE_FORMAT(r.create_time, '%Y-%m-%d %H:%i:%s') = d.create_time_key
 AND r.id <> d.keep_id;
