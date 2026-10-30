SET NAMES utf8mb4;
SET @ddl = (SELECT IF(COUNT(*)=0,
 'ALTER TABLE cyan_question ADD review_state VARCHAR(16) NOT NULL DEFAULT ''PUBLISHED'', ADD revision INT NOT NULL DEFAULT 1',
 'SELECT 1') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='cyan_question' AND column_name='review_state');
PREPARE migration FROM @ddl;
EXECUTE migration;
DEALLOCATE PREPARE migration;
UPDATE cyan_question SET review_state='DISABLED' WHERE status='1' AND review_state='PUBLISHED';

CREATE TABLE IF NOT EXISTS exam_question_review_event (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, question_id BIGINT NOT NULL, revision INT NOT NULL,
 action VARCHAR(20) NOT NULL, from_state VARCHAR(16), to_state VARCHAR(16) NOT NULL,
 actor VARCHAR(64) NOT NULL, reason VARCHAR(1000) NOT NULL, snapshot_json LONGTEXT NOT NULL,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uq_question_revision(question_id,revision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS exam_workstation (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, code VARCHAR(64) NOT NULL, location VARCHAR(200) NOT NULL,
 contact VARCHAR(100) NOT NULL DEFAULT '', remark VARCHAR(500) NOT NULL DEFAULT '',
 revision INT NOT NULL DEFAULT 1, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uq_workstation_code(code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS exam_equipment (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, code VARCHAR(64) NOT NULL, model VARCHAR(100) NOT NULL,
 asset_state VARCHAR(20) NOT NULL, station_id BIGINT NULL, contact VARCHAR(100) NOT NULL DEFAULT '',
 remark VARCHAR(500) NOT NULL DEFAULT '', revision INT NOT NULL DEFAULT 1,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uq_equipment_code(code), KEY ix_equipment_station(station_id),
 CONSTRAINT fk_equipment_station FOREIGN KEY(station_id) REFERENCES exam_workstation(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

START TRANSACTION;
CREATE TEMPORARY TABLE new_feature_menu (permission_key VARCHAR(100) COLLATE utf8mb4_0900_ai_ci, label VARCHAR(50));
INSERT INTO new_feature_menu VALUES ('question:question:review','题目审核'),('system:equipment:list','设备与工位'),('system:equipment:edit','台账维护');
-- Only grant permissions created by this migration; do not restore later revoked grants.
DELETE n FROM new_feature_menu n JOIN sys_menu m ON m.perms=n.permission_key;
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT label,IF(permission_key LIKE 'question:%',2000,1),20,
 IF(permission_key='system:equipment:list','equipment','#'),
 IF(permission_key='system:equipment:list','system/equipment/index',NULL),
 1,0,IF(permission_key='system:equipment:list','C','F'),'0','0',permission_key,'table','release-migration',NOW()
FROM new_feature_menu;
INSERT IGNORE INTO sys_role_menu(role_id,menu_id)
SELECT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m JOIN new_feature_menu n ON n.permission_key=m.perms
WHERE r.del_flag='0' AND (r.role_key='system_admin'
 OR (r.role_key='reviewer' AND m.perms='question:question:review'));
INSERT IGNORE INTO sys_role_menu(role_id,menu_id)
SELECT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m
WHERE r.del_flag='0' AND r.role_key IN ('reviewer','system_admin')
 AND EXISTS(SELECT 1 FROM new_feature_menu WHERE permission_key='question:question:review')
 AND (m.menu_id=2000 OR m.perms IN ('question:question:list','question:question:query'));
DROP TEMPORARY TABLE new_feature_menu;
COMMIT;
