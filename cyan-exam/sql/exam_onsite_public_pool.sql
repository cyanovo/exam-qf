-- Public onsite grading permission. Task judge_id=0 means no designated judge;
-- exam_onsite_score.judge_id continues to record the actual scoring judge.
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,create_by,create_time)
SELECT '机器人实操评分',0,23,'#',1,0,'F','0','0','exam:onsite:score','admin',NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='exam:onsite:score');

INSERT INTO sys_role_menu(role_id,menu_id)
SELECT DISTINCT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m ON m.perms='exam:onsite:score'
WHERE r.status='0' AND r.del_flag='0'
AND (r.role_key IN ('judge','demo_judge') OR EXISTS (
    SELECT 1 FROM sys_role_menu rm JOIN sys_menu gm ON gm.menu_id=rm.menu_id
    WHERE rm.role_id=r.role_id AND gm.perms='exam:grading:edit'))
AND NOT EXISTS (SELECT 1 FROM sys_role_menu existing WHERE existing.role_id=r.role_id AND existing.menu_id=m.menu_id);
