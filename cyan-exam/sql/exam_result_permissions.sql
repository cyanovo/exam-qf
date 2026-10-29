INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT '成绩复核',0,8,'resultReview','exam/grading/reviewList',1,0,'C','0','0','exam:results:review','education','admin',NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='exam:results:review');
SET @results_menu = (SELECT MIN(menu_id) FROM sys_menu WHERE perms='exam:results:review');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,create_by,create_time)
SELECT '绑定复核员',@results_menu,1,'#',1,0,'F','0','0','exam:results:assign','admin',NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='exam:results:assign');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,create_by,create_time)
SELECT '导出锁定成绩',@results_menu,2,'#',1,0,'F','0','0','exam:results:export','admin',NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='exam:results:export');
-- Deliberately do not grant review permission to all teachers.
-- An administrator grants menu permissions AND the exam owner binds the reviewer.
