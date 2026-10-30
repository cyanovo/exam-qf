-- Run once on the chosen database before starting the new application.
ALTER TABLE cyan_question ADD COLUMN grading_rules TEXT NULL;
ALTER TABLE exam_question_snapshot ADD COLUMN grading_rules TEXT NULL;
ALTER TABLE exam_code_submission ADD COLUMN run_version INT NOT NULL DEFAULT 1;

CREATE TABLE IF NOT EXISTS exam_code_evaluation_history (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  submission_id BIGINT NOT NULL,
  run_version INT NOT NULL,
  state VARCHAR(16) NOT NULL,
  score INT NULL,
  attempts INT NOT NULL,
  feedback MEDIUMTEXT NULL,
  progress MEDIUMTEXT NULL,
  source_hash CHAR(64) NOT NULL,
  snapshot_hash CHAR(64) NOT NULL,
  completed_at DATETIME(3) NULL,
  actor_id BIGINT NOT NULL,
  reason VARCHAR(500) NOT NULL,
  archived_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_code_evaluation(submission_id, run_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
