-- One-time recovery for the in-progress exam 2 after exam_publish.sql.
-- Review the current question bank against the actual paper before running.
-- This is a reconstructed snapshot, NOT proof of the original question version.
-- Keep the pre-migration database backup and do not grade pre-existing, unassigned answers.
-- Run with mysql --default-character-set=utf8mb4 -D cyan_exam < recover_legacy_exam_2.sql
CREATE TABLE IF NOT EXISTS exam_legacy_recovery (
  exam_id bigint NOT NULL PRIMARY KEY,
  recovered_at datetime NOT NULL,
  source_description varchar(255) NOT NULL,
  assigned_questions int NOT NULL,
  excluded_answers int NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DELIMITER //
DROP PROCEDURE IF EXISTS recover_legacy_exam_2//
CREATE PROCEDURE recover_legacy_exam_2()
BEGIN
  DECLARE question_count int DEFAULT 0;
  DECLARE excluded_count int DEFAULT 0;
  DECLARE exam_count int DEFAULT 0;
  DECLARE bad_count int DEFAULT 0;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  START TRANSACTION;
  SELECT COUNT(*) INTO exam_count FROM exam_info
    WHERE exam_id = 2 AND status = '0' AND published_at IS NULL
      AND start_time <= NOW() AND end_time > NOW();
  IF exam_count <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Exam 2 is not an unpublished active exam';
  END IF;
  SELECT COUNT(*) INTO bad_count FROM exam_question_snapshot WHERE exam_id = 2;
  IF bad_count <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Exam 2 already has snapshots';
  END IF;
  SELECT COUNT(*) INTO bad_count FROM exam_legacy_recovery WHERE exam_id = 2;
  IF bad_count <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Exam 2 already recovered';
  END IF;
  SELECT COUNT(*), SUM(c.question_id IS NULL OR c.status <> '0'
      OR c.title IS NULL OR TRIM(c.title) = '' OR c.answer IS NULL
      OR TRIM(c.answer) = '' OR q.points NOT BETWEEN 1 AND 100
      OR c.question_type NOT IN ('SINGLE_CHOICE','MULTI_CHOICE','JUDGE','FILL_BLANK','SHORT_ANSWER'))
    INTO question_count, bad_count
    FROM exam_question q LEFT JOIN cyan_question c ON c.question_id = q.question_id
    WHERE q.exam_id = 2;
  IF question_count <> 14 OR bad_count <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Exam 2 questions differ from reviewed assignment';
  END IF;
  SELECT COUNT(*) INTO bad_count FROM exam_answer_record a
    JOIN exam_student s ON s.exam_id = a.exam_id AND s.user_id = a.user_id
    LEFT JOIN exam_question q ON q.exam_id = a.exam_id AND q.question_id = a.question_id
    WHERE a.exam_id = 2 AND (q.question_id IS NULL OR a.create_time < (
      SELECT start_time FROM exam_info WHERE exam_id = 2));
  IF bad_count <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Enrolled answers have invalid questions or timestamps';
  END IF;

  INSERT INTO exam_question_snapshot
    (exam_id, question_id, sort, category, difficulty, question_type, title, sub_title, answer, max_score)
  SELECT q.exam_id, q.question_id, ROW_NUMBER() OVER (ORDER BY q.sort, q.question_id) - 1,
    c.category, c.difficulty, c.question_type, c.title, c.sub_title, c.answer, q.points
  FROM exam_question q JOIN cyan_question c ON c.question_id = q.question_id
  WHERE q.exam_id = 2;

  SELECT COUNT(*) INTO excluded_count FROM exam_answer_record a
    LEFT JOIN exam_student s ON s.exam_id = a.exam_id AND s.user_id = a.user_id
    WHERE a.exam_id = 2 AND s.user_id IS NULL;
  INSERT INTO exam_legacy_recovery
    (exam_id, recovered_at, source_description, assigned_questions, excluded_answers)
  VALUES (2, NOW(), 'Reconstructed from current cyan_question and exam_question; original version unverified',
    question_count, excluded_count);

  INSERT INTO exam_session (exam_id, user_id, started_at, status)
  SELECT a.exam_id, a.user_id, GREATEST(e.start_time, MIN(a.create_time)), 'IN_PROGRESS'
  FROM exam_answer_record a
    JOIN exam_student s ON s.exam_id = a.exam_id AND s.user_id = a.user_id
    JOIN exam_info e ON e.exam_id = a.exam_id
    LEFT JOIN exam_session sess ON sess.exam_id = a.exam_id AND sess.user_id = a.user_id
  WHERE a.exam_id = 2 AND sess.session_id IS NULL
  GROUP BY a.exam_id, a.user_id, e.start_time;

  UPDATE exam_info SET published_at = NOW() WHERE exam_id = 2 AND published_at IS NULL;
  COMMIT;
END//
DELIMITER ;

CALL recover_legacy_exam_2();
DROP PROCEDURE IF EXISTS recover_legacy_exam_2;
