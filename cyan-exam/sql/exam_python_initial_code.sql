-- Run after exam_publish.sql; apply before deploying the initial-code backend.
SET @add_question_code = (
  SELECT IF(COUNT(*) = 0, 'ALTER TABLE cyan_question ADD COLUMN initial_code TEXT NULL', 'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'cyan_question' AND column_name = 'initial_code'
);
PREPARE migration FROM @add_question_code;
EXECUTE migration;
DEALLOCATE PREPARE migration;

SET @add_snapshot_code = (
  SELECT IF(COUNT(*) = 0, 'ALTER TABLE exam_question_snapshot ADD COLUMN initial_code TEXT NULL', 'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'exam_question_snapshot' AND column_name = 'initial_code'
);
PREPARE migration FROM @add_snapshot_code;
EXECUTE migration;
DEALLOCATE PREPARE migration;
