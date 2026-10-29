-- Run after the base schema and exam_session.sql, before deploying publication code.
-- Existing exams remain drafts; review and publish future exams individually.
SET @add_published_at = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE exam_info ADD COLUMN published_at datetime NULL DEFAULT NULL',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'exam_info' AND column_name = 'published_at'
);
PREPARE exam_publish_migration FROM @add_published_at;
EXECUTE exam_publish_migration;
DEALLOCATE PREPARE exam_publish_migration;

CREATE TABLE IF NOT EXISTS exam_question_snapshot (
  exam_id bigint NOT NULL,
  question_id bigint NOT NULL,
  sort int NOT NULL DEFAULT 0,
  category varchar(100) DEFAULT NULL,
  difficulty int DEFAULT NULL,
  question_type varchar(50) NOT NULL,
  title longtext NOT NULL,
  sub_title longtext DEFAULT NULL,
  answer longtext NOT NULL,
  PRIMARY KEY (exam_id, question_id),
  KEY idx_snapshot_order (exam_id, sort, question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
