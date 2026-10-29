CREATE TABLE IF NOT EXISTS exam_result_version (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  exam_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  state VARCHAR(32) NOT NULL,
  payload LONGTEXT NOT NULL,
  requested_by BIGINT NOT NULL,
  reviewed_by BIGINT NULL,
  reason VARCHAR(500) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  released_at DATETIME(3) NULL,
  UNIQUE KEY uk_result_version (exam_id, version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS exam_result_event (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  version_id BIGINT NOT NULL,
  actor_id BIGINT NOT NULL,
  old_state VARCHAR(32) NOT NULL,
  new_state VARCHAR(32) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY ix_result_event (version_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS exam_reviewer (
  exam_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  assigned_by BIGINT NOT NULL,
  assigned_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (exam_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
