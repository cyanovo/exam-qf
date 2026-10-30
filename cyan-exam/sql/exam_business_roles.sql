-- PRD 2.1: five business roles; keep existing accounts and custom grants.
SET NAMES utf8mb4;
START TRANSACTION;

UPDATE sys_menu SET perms='question:question:list'
WHERE menu_id=2000 AND (perms IS NULL OR perms='');

CREATE TEMPORARY TABLE exam_question_permissions (
    menu_name VARCHAR(50), permission_key VARCHAR(100) COLLATE utf8mb4_0900_ai_ci
);
INSERT INTO exam_question_permissions VALUES
('题目查询','question:question:query'),('题目新增','question:question:add'),
('题目修改','question:question:edit'),('题目删除','question:question:remove'),
('题目导入','question:question:import'),('题目导出','question:question:export');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT p.menu_name,2000,1,'#',1,0,'F','0','0',p.permission_key,'#','role-migration',NOW()
FROM exam_question_permissions p
WHERE NOT EXISTS(SELECT 1 FROM sys_menu m WHERE m.perms=p.permission_key);

CREATE TEMPORARY TABLE exam_new_roles (
    role_key VARCHAR(100) COLLATE utf8mb4_0900_ai_ci, role_name VARCHAR(30), role_sort INT, data_scope CHAR(1), remark VARCHAR(500)
);
INSERT INTO exam_new_roles VALUES
('system_admin','系统管理员',1,'1','账号、组织、角色授权、场次配置、系统维护与审计'),
('teacher','命题教师',2,'5','题库维护、试卷编排、创建考试、基础题批改与提交复核'),
('student','考生',3,'5','参加获准考试、运行样例、提交答案、查看本人成绩'),
('judge','裁判',4,'5','公共评分池查看机器人实操任务，认领并提交评分'),
('reviewer','成绩复核员',5,'5','按场次授权复核成绩、审批更正、发布及导出锁定成绩');
DELETE n FROM exam_new_roles n JOIN sys_role r ON r.role_key=n.role_key AND r.del_flag='0';
INSERT INTO sys_role(role_name,role_key,role_sort,data_scope,menu_check_strictly,dept_check_strictly,status,del_flag,create_by,create_time,remark)
SELECT role_name,role_key,role_sort,data_scope,1,1,'0','0','role-migration',NOW(),remark FROM exam_new_roles;

-- Seed only newly created roles: re-running must not restore intentionally revoked permissions.
INSERT INTO sys_role_menu(role_id,menu_id)
SELECT DISTINCT r.role_id,m.menu_id FROM sys_role r
JOIN exam_new_roles n ON n.role_key=r.role_key
JOIN sys_menu m ON m.status='0'
WHERE
 (r.role_key='system_admin' AND (
    m.menu_id IN (1,2,108,100,101,103,105,106,109,112,113,500,501,2014)
    OR m.perms LIKE 'system:user:%' OR m.perms LIKE 'system:role:%'
    OR m.perms LIKE 'system:dept:%' OR m.perms LIKE 'system:config:%'
    OR m.perms IN ('system:dict:query','monitor:operlog:query','monitor:operlog:export',
       'monitor:logininfor:query','monitor:logininfor:export',
       'monitor:online:query','monitor:online:forceLogout','exam:results:assign',
       'exam:grading:list','exam:grading:detail')
    OR m.perms LIKE 'exam:exam:%'))
 OR (r.role_key='teacher' AND (
    m.menu_id IN (2000,2014,2005) OR m.perms LIKE 'question:question:%'
    OR m.perms LIKE 'exam:exam:%' OR m.perms LIKE 'exam:grading:%'
    OR m.perms IN ('exam:results:assign','exam:results:export')))
 OR (r.role_key='student' AND m.menu_id=2004)
 OR (r.role_key='judge' AND m.perms='exam:onsite:score')
 OR (r.role_key='reviewer' AND (
    m.menu_id IN (1,108,500) OR m.perms IN ('exam:results:review','exam:results:export','monitor:operlog:query')));

UPDATE sys_role SET role_name='考生',role_sort=3,
remark='参加获准考试、运行样例、提交答案、查看本人成绩'
WHERE role_key='student' AND role_name='学生' AND del_flag='0';

DROP TEMPORARY TABLE exam_question_permissions;
DROP TEMPORARY TABLE exam_new_roles;
COMMIT;
