-- Synthetic fixtures, isolated demo database only. Never apply to production.
USE cyan_exam_demo_20261029;
INSERT INTO sys_user(user_id,user_name,nick_name,password,pwd_update_date,create_time)
VALUES
(91001,'demo_owner','Demo Owner','$2a$10$6MtRCwwqX9aL2AeyYZY.IeGBQ6wYgpJ5I3jb3T.x4/0xaN1/W4LUG',NOW(),NOW()),
(91002,'demo_judge','Demo Judge','$2a$10$6MtRCwwqX9aL2AeyYZY.IeGBQ6wYgpJ5I3jb3T.x4/0xaN1/W4LUG',NOW(),NOW()),
(91003,'demo_reviewer','Demo Reviewer','$2a$10$6MtRCwwqX9aL2AeyYZY.IeGBQ6wYgpJ5I3jb3T.x4/0xaN1/W4LUG',NOW(),NOW()),
(91004,'demo_student','Demo Student','$2a$10$6MtRCwwqX9aL2AeyYZY.IeGBQ6wYgpJ5I3jb3T.x4/0xaN1/W4LUG',NOW(),NOW());
INSERT INTO sys_role(role_id,role_name,role_key,role_sort,status,del_flag)
VALUES(91001,'Demo Owner','demo_owner',10,'0','0'),(91002,'Demo Judge','demo_judge',12,'0','0'),(91003,'Demo Reviewer','demo_reviewer',11,'0','0');
INSERT INTO sys_role_menu(role_id,menu_id)
SELECT 91001,menu_id FROM sys_menu WHERE perms LIKE 'exam:grading:%' OR perms IN ('exam:results:assign','exam:results:export','exam:info:list','exam:info:query');
INSERT INTO sys_role_menu(role_id,menu_id)
SELECT 91003,menu_id FROM sys_menu WHERE perms IN ('exam:results:review','exam:results:export');
INSERT INTO sys_user_role(user_id,role_id) VALUES(91001,91001),(91002,91002),(91003,91003),(91004,100);
INSERT INTO exam_info(exam_id,exam_name,start_time,end_time,published_at,owner_user_id)
VALUES(91001,'Onsite workflow verification',DATE_SUB(NOW(),INTERVAL 2 HOUR),DATE_SUB(NOW(),INTERVAL 1 HOUR),DATE_SUB(NOW(),INTERVAL 3 HOUR),91001),
(91002,'Onsite interactive demo',DATE_SUB(NOW(),INTERVAL 2 HOUR),DATE_SUB(NOW(),INTERVAL 1 HOUR),DATE_SUB(NOW(),INTERVAL 3 HOUR),91001);
INSERT INTO exam_student(exam_id,user_id) VALUES(91001,91004),(91002,91004);
INSERT INTO exam_question_snapshot(exam_id,question_id,question_type,title,answer,max_score)
VALUES(91001,91001,'SHORT','Explain the program output','Sample explanation',10),
(91002,91001,'SHORT','Explain the program output','Sample explanation',10);
INSERT INTO exam_session(exam_id,user_id,started_at,submitted_at,status)
VALUES(91001,91004,DATE_SUB(NOW(),INTERVAL 2 HOUR),DATE_SUB(NOW(),INTERVAL 1 HOUR),'SUBMITTED'),
(91002,91004,DATE_SUB(NOW(),INTERVAL 2 HOUR),DATE_SUB(NOW(),INTERVAL 1 HOUR),'SUBMITTED');
INSERT INTO exam_answer_record(exam_id,user_id,question_id,answer,score,graded_at)
VALUES(91001,91004,91001,'Sample explanation',8,NOW()),(91002,91004,91001,'Sample explanation',8,NOW());
