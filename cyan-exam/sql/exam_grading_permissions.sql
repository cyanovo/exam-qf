-- Define permissions without granting them to unrelated roles.
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,create_by,create_time)
SELECT '阅卷列表',0,20,'#',1,0,'F','0','0','exam:grading:list','admin',NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='exam:grading:list');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,create_by,create_time)
SELECT '阅卷详情',0,21,'#',1,0,'F','0','0','exam:grading:detail','admin',NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='exam:grading:detail');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,create_by,create_time)
SELECT '评分与提交复核',0,22,'#',1,0,'F','0','0','exam:grading:edit','admin',NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='exam:grading:edit');
