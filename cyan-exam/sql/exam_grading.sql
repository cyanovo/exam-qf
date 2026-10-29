-- Run after exam_publish.sql. Safe to rerun.
SET @add_points = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE exam_question ADD COLUMN points int NOT NULL DEFAULT 10',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'exam_question' AND column_name = 'points'
);
PREPARE grading_migration FROM @add_points;
EXECUTE grading_migration;
DEALLOCATE PREPARE grading_migration;

SET @add_graded_at = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE exam_answer_record ADD COLUMN graded_at datetime NULL DEFAULT NULL',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'exam_answer_record' AND column_name = 'graded_at'
);
PREPARE grading_migration FROM @add_graded_at;
EXECUTE grading_migration;
DEALLOCATE PREPARE grading_migration;

CREATE TABLE IF NOT EXISTS exam_score_revision (
  revision_id bigint NOT NULL AUTO_INCREMENT,
  record_id bigint NOT NULL,
  exam_id bigint NOT NULL,
  user_id bigint NOT NULL,
  question_id bigint NOT NULL,
  previous_score int DEFAULT NULL,
  new_score int NOT NULL,
  grader_id bigint NOT NULL,
  changed_at datetime NOT NULL,
  reason varchar(500) NOT NULL DEFAULT '首次批改',
  PRIMARY KEY (revision_id),
  KEY idx_score_revision_record (record_id, changed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @add_revision_reason = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE exam_score_revision ADD COLUMN reason varchar(500) NOT NULL DEFAULT ''历史批改（原因未记录）''',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'exam_score_revision' AND column_name = 'reason'
);
PREPARE grading_migration FROM @add_revision_reason;
EXECUTE grading_migration;
DEALLOCATE PREPARE grading_migration;

SET @add_max_score = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE exam_question_snapshot ADD COLUMN max_score int NOT NULL DEFAULT 10',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'exam_question_snapshot' AND column_name = 'max_score'
);
PREPARE grading_migration FROM @add_max_score;
EXECUTE grading_migration;
DEALLOCATE PREPARE grading_migration;
