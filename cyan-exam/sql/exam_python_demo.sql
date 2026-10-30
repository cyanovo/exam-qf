-- New synthetic fixtures only. Never apply to production or overwrite existing exams.
-- Select an isolated demo database explicitly before running this script.
SET @verification_exam=COALESCE(@verification_exam,92001);
SET @interactive_exam=COALESCE(@interactive_exam,92002);
INSERT INTO exam_info(exam_id,exam_name,start_time,end_time,published_at,owner_user_id)
VALUES(@verification_exam,'Python automatic grading verification',DATE_SUB(NOW(),INTERVAL 1 HOUR),DATE_ADD(NOW(),INTERVAL 7 DAY),NOW(),91001),
(@interactive_exam,'Python and grading rules interactive demo',DATE_SUB(NOW(),INTERVAL 1 HOUR),DATE_ADD(NOW(),INTERVAL 7 DAY),NOW(),91001);
INSERT INTO exam_student(exam_id,user_id) VALUES(@verification_exam,91004),(@interactive_exam,91004);
-- JSON escaping is preserved independently of MySQL backslash SQL mode.
SET @cases=JSON_OBJECT('cases',JSON_ARRAY(
JSON_OBJECT('input',CONCAT('1',CHAR(10 USING utf8mb4)),'expected',CONCAT('2',CHAR(10 USING utf8mb4)),'weight',0,'public',CAST('true' AS JSON),'comparison','EXACT'),
JSON_OBJECT('input',CONCAT('2',CHAR(10 USING utf8mb4)),'expected',CONCAT('4',CHAR(10 USING utf8mb4)),'weight',4,'public',CAST('false' AS JSON),'comparison','EXACT'),
JSON_OBJECT('input',CONCAT('5',CHAR(10 USING utf8mb4)),'expected',CONCAT('10',CHAR(10 USING utf8mb4)),'weight',6,'public',CAST('false' AS JSON),'comparison','EXACT')));
INSERT INTO exam_question_snapshot(exam_id,question_id,question_type,title,answer,max_score,grading_rules)
SELECT exam_id,92001,'PYTHON','Read an integer and print twice its value.',@cases,10,NULL FROM exam_info WHERE exam_id IN(@verification_exam,@interactive_exam);
INSERT INTO exam_question_snapshot(exam_id,question_id,question_type,title,answer,max_score,grading_rules)
SELECT exam_id,92002,'MULTI_CHOICE','Select A and C. A correct subset earns 3 points.','A,C',6,'{"version":1,"mode":"PARTIAL","partialScore":3}' FROM exam_info WHERE exam_id IN(@verification_exam,@interactive_exam);
UPDATE exam_question_snapshot SET sub_title='[{"label":"A","content":"First correct option"},{"label":"B","content":"Incorrect option"},{"label":"C","content":"Second correct option"}]' WHERE exam_id IN(@verification_exam,@interactive_exam) AND question_id=92002;
INSERT INTO exam_question_snapshot(exam_id,question_id,question_type,title,answer,max_score,grading_rules)
SELECT exam_id,92003,'FILL_BLANK','Two blanks: Python;hello world','Python;hello world',4,'{"version":1,"mode":"TEXT","scoring":"PER_BLANK","whitespace":"REMOVE","ignoreCase":true,"normalizeWidth":true,"accepted":[["Python","py"],["hello world"]]}' FROM exam_info WHERE exam_id IN(@verification_exam,@interactive_exam);
