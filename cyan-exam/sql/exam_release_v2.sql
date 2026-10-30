-- Apply to the selected database before starting the new backend.
DELIMITER //
DROP PROCEDURE IF EXISTS migrate_exam_release_v2//
CREATE PROCEDURE migrate_exam_release_v2()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='exam_info' AND column_name='policy_json') THEN
    ALTER TABLE exam_info
      ADD COLUMN policy_json LONGTEXT NULL,
      ADD COLUMN duration_minutes INT NOT NULL DEFAULT 0,
      ADD COLUMN early_entry_minutes INT NOT NULL DEFAULT 30,
      ADD COLUMN lifecycle VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
  END IF;
END//
CALL migrate_exam_release_v2()//
DROP PROCEDURE migrate_exam_release_v2//
DELIMITER ;

CREATE TABLE IF NOT EXISTS exam_management_event (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, exam_id BIGINT NOT NULL, actor_id BIGINT NOT NULL,
 action VARCHAR(32) NOT NULL, reason VARCHAR(1000) NOT NULL, payload LONGTEXT,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), INDEX(exam_id,id)
);
CREATE TABLE IF NOT EXISTS exam_incident (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, exam_id BIGINT NOT NULL, task_id BIGINT NULL,
 user_id BIGINT NOT NULL, created_by BIGINT NOT NULL, category VARCHAR(32) NOT NULL,
 title VARCHAR(150) NOT NULL, description TEXT NOT NULL, state VARCHAR(24) NOT NULL DEFAULT 'OPEN',
 revision INT NOT NULL DEFAULT 1, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), INDEX(exam_id,user_id,id)
);
CREATE TABLE IF NOT EXISTS exam_incident_event (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, incident_id BIGINT NOT NULL, actor_id BIGINT NOT NULL,
 action VARCHAR(24) NOT NULL, opinion TEXT NOT NULL, before_state VARCHAR(24) NOT NULL,
 after_state VARCHAR(24) NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 INDEX(incident_id,id)
);
CREATE TABLE IF NOT EXISTS exam_file_asset (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, exam_id BIGINT NOT NULL, task_id BIGINT NULL,
 user_id BIGINT NOT NULL, incident_id BIGINT NULL, uploaded_by BIGINT NOT NULL,
 purpose VARCHAR(24) NOT NULL, original_name VARCHAR(180) NOT NULL, storage_key VARCHAR(250) NOT NULL,
 media_type VARCHAR(80) NOT NULL, byte_size BIGINT NOT NULL, sha256 CHAR(64) NOT NULL,
 retain_until DATETIME NOT NULL, deleted TINYINT NOT NULL DEFAULT 0, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 INDEX(exam_id,user_id,task_id), INDEX(incident_id)
);

-- Identity exports require an explicit grant; existing export roles remain pseudonymized.
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,create_by,create_time)
SELECT 'Export candidate identity',0,30,'#',1,0,'F','0','0','exam:results:exportIdentity','admin',NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='exam:results:exportIdentity');

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT '考试与编排',0,8,'exam-management','system/info/index',1,0,'C','0','0','exam:exam:list','education','admin',NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='exam:exam:list');
SET @exam_management_menu=(SELECT MIN(menu_id) FROM sys_menu WHERE perms='exam:exam:list');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,create_by,create_time)
SELECT p.label,@exam_management_menu,p.sort,'#',1,0,'F','0','0',p.permission,'admin',NOW()
FROM (
 SELECT '查看考试' label,1 sort,'exam:exam:query' permission UNION ALL
 SELECT '新增考试',2,'exam:exam:add' UNION ALL SELECT '编辑与发布考试',3,'exam:exam:edit' UNION ALL
 SELECT '删除草稿',4,'exam:exam:remove' UNION ALL SELECT '导出考试',5,'exam:exam:export'
) p WHERE NOT EXISTS (SELECT 1 FROM sys_menu m WHERE m.perms=p.permission);
