CREATE TABLE IF NOT EXISTS exam_onsite_code_draft (
  task_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  source MEDIUMTEXT NOT NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY(task_id,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS exam_onsite_code_submission (
  id BIGINT NOT NULL AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  source MEDIUMTEXT NOT NULL,
  source_hash VARCHAR(64) NOT NULL,
  request_key VARCHAR(36) NOT NULL,
  received_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY(id),
  UNIQUE KEY uk_practical_request(task_id,user_id,request_key),
  KEY idx_practical_history(task_id,user_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @ddl = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='exam_onsite_score' AND column_name='submission_id'),
  'SELECT 1',
  'ALTER TABLE exam_onsite_score ADD COLUMN submission_id BIGINT NULL'
);
PREPARE practical_migration FROM @ddl;
EXECUTE practical_migration;
DEALLOCATE PREPARE practical_migration;
