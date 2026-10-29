-- Run before deploying the exam resource authorization code.
-- Deliberately do not trust legacy create_by values for ownership.
-- NULL owners are accessible only to the built-in administrator (user_id = 1).
SET @add_exam_owner = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE exam_info ADD COLUMN owner_user_id bigint NULL DEFAULT NULL, ADD INDEX idx_exam_owner (owner_user_id)',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'exam_info' AND column_name = 'owner_user_id'
);
PREPARE exam_owner_migration FROM @add_exam_owner;
EXECUTE exam_owner_migration;
DEALLOCATE PREPARE exam_owner_migration;
