-- Apply once to existing cyan_exam databases before deploying the session endpoints.
CREATE TABLE IF NOT EXISTS `exam_session` (
  `session_id` bigint NOT NULL AUTO_INCREMENT,
  `exam_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `started_at` datetime NOT NULL,
  `submitted_at` datetime DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'IN_PROGRESS',
  PRIMARY KEY (`session_id`),
  UNIQUE KEY `uk_exam_session_user` (`exam_id`, `user_id`),
  KEY `idx_exam_session_status` (`exam_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
