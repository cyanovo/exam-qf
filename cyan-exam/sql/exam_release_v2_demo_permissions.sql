-- Deliberately restricted to the isolated demonstration database and synthetic teacher.
INSERT INTO sys_role_menu(role_id,menu_id)
SELECT r.role_id,m.menu_id FROM sys_role r CROSS JOIN sys_menu m
WHERE DATABASE()='cyan_exam_python_demo_20261029' AND r.role_id=91001 AND r.role_name='Demo Owner'
AND m.perms IN ('exam:exam:list','exam:exam:query','exam:exam:add','exam:exam:edit','exam:exam:remove','exam:exam:export')
AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.role_id AND rm.menu_id=m.menu_id);
