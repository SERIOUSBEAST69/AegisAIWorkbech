-- Normalize all users' real_name and nickname to unique, realistic names.
-- Uses deterministic generation by ordered row number to avoid duplicates.

UPDATE sys_user u
JOIN (
  SELECT
    id,
    ROW_NUMBER() OVER (ORDER BY id) AS rn
  FROM sys_user
) ranked ON ranked.id = u.id
SET
  u.real_name = CONCAT(
    ELT(1 + MOD(ranked.rn, 20),
      'Liam','Noah','Oliver','Elijah','James','Lucas','Mason','Ethan','Logan','Jacob',
      'Ava','Emma','Sophia','Isabella','Mia','Amelia','Harper','Evelyn','Abigail','Emily'
    ),
    ' ',
    ELT(1 + MOD(FLOOR(ranked.rn / 20), 24),
      'Carter','Davis','Miller','Wilson','Moore','Taylor','Anderson','Thomas','Jackson','White','Harris','Martin',
      'Thompson','Garcia','Martinez','Robinson','Clark','Lewis','Lee','Walker','Hall','Allen','Young','King'
    )
  ),
  u.nickname = CONCAT(
    ELT(1 + MOD(ranked.rn, 20),
      'Liam','Noah','Oliver','Elijah','James','Lucas','Mason','Ethan','Logan','Jacob',
      'Ava','Emma','Sophia','Isabella','Mia','Amelia','Harper','Evelyn','Abigail','Emily'
    ),
    ' ',
    ELT(1 + MOD(FLOOR(ranked.rn / 20), 24),
      'Carter','Davis','Miller','Wilson','Moore','Taylor','Anderson','Thomas','Jackson','White','Harris','Martin',
      'Thompson','Garcia','Martinez','Robinson','Clark','Lewis','Lee','Walker','Hall','Allen','Young','King'
    )
  ),
  u.update_time = CURRENT_TIMESTAMP;
