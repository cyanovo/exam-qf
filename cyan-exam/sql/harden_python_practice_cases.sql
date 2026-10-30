-- Run only against the isolated practice database. Published exam snapshots are immutable.
-- Restrict this change to the original, unedited practice question with duplicate hidden outputs.
UPDATE cyan_question
SET answer = JSON_SET(answer, '$.cases[2].input', CONCAT('5', CHAR(10 USING utf8mb4), '5 6 6 6 6', CHAR(10 USING utf8mb4)),
                              '$.cases[2].expected', CONCAT('2', CHAR(10 USING utf8mb4)))
WHERE remark = 'Python练习:20261030:04'
  AND question_type = 'PYTHON'
  AND JSON_LENGTH(answer, '$.cases') = 4
  AND JSON_UNQUOTE(JSON_EXTRACT(answer, '$.cases[2].input')) = CONCAT('5', CHAR(10 USING utf8mb4), '5 5 5 5 5', CHAR(10 USING utf8mb4))
  AND JSON_UNQUOTE(JSON_EXTRACT(answer, '$.cases[2].expected')) = CONCAT('1', CHAR(10 USING utf8mb4));
