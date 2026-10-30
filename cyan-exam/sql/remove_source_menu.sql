-- Remove only the project's source-code navigation link.
SET NAMES utf8mb4;
START TRANSACTION;
DELETE rm
FROM sys_role_menu rm
JOIN sys_menu m ON m.menu_id = rm.menu_id
WHERE m.menu_name = '源码地址'
  AND m.path = 'https://github.com/cyanovo/cyan-exam'
  AND m.parent_id = 0;
DELETE FROM sys_menu
WHERE menu_name = '源码地址'
  AND path = 'https://github.com/cyanovo/cyan-exam'
  AND parent_id = 0;
COMMIT;
