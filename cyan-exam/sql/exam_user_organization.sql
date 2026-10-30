SET NAMES utf8mb4;
START TRANSACTION;
-- Match original seed names as well as IDs; preserve administrator customizations.
UPDATE sys_dept SET dept_name=CASE dept_id
 WHEN 100 THEN '考试中心' WHEN 101 THEN '考务组织' WHEN 102 THEN '考生分组'
 WHEN 103 THEN '命题组' WHEN 104 THEN '监考组' WHEN 105 THEN '评审组'
 WHEN 106 THEN '成绩管理组' WHEN 107 THEN '技术保障组'
 WHEN 108 THEN '考生一组' WHEN 109 THEN '考生二组' END,
 leader=IF(leader='若依','',leader),update_by='exam-upgrade',update_time=NOW()
WHERE (dept_id=100 AND dept_name='若依科技')
 OR (dept_id=101 AND dept_name='深圳总公司') OR (dept_id=102 AND dept_name='长沙分公司')
 OR (dept_id=103 AND dept_name='研发部门') OR (dept_id=104 AND dept_name='市场部门')
 OR (dept_id=105 AND dept_name='测试部门') OR (dept_id=106 AND dept_name='财务部门')
 OR (dept_id=107 AND dept_name='运维部门') OR (dept_id=108 AND dept_name='市场部门')
 OR (dept_id=109 AND dept_name='财务部门');
UPDATE sys_post SET post_name=CASE post_id
 WHEN 1 THEN '考务负责人' WHEN 2 THEN '考务人员' WHEN 3 THEN '监考人员' WHEN 4 THEN '考生' END,
 update_by='exam-upgrade',update_time=NOW()
WHERE (post_id=1 AND post_name='董事长') OR (post_id=2 AND post_name='项目经理')
 OR (post_id=3 AND post_name='人力资源') OR (post_id=4 AND post_name='普通员工');
UPDATE sys_menu SET menu_name=REPLACE(menu_name,'部门','分组')
WHERE menu_id IN (103,1016,1017,1018,1019) AND menu_name IN ('部门管理','部门查询','部门新增','部门修改','部门删除');
UPDATE sys_menu SET menu_name=REPLACE(menu_name,'岗位','考务岗位')
WHERE menu_id IN (104,1020,1021,1022,1023,1024) AND menu_name IN ('岗位管理','岗位查询','岗位新增','岗位修改','岗位删除','岗位导出');
COMMIT;
