"""Export local account inventory; only verify documented seed passwords, never reset."""
import datetime
import json
import os
from pathlib import Path
import subprocess
import bcrypt

root = Path(__file__).resolve().parents[2]
cfg = json.loads((root / "deploy/windows/config.local.json").read_text(encoding="utf-8-sig"))
env = dict(os.environ, MYSQL_PWD=cfg["DatabasePassword"])
query = """SELECT JSON_OBJECT('id',u.user_id,'name',u.user_name,'nickname',u.nick_name,
'hash',u.password,'status',u.status,'deleted',u.del_flag,
'roles',COALESCE((SELECT GROUP_CONCAT(r.role_name ORDER BY r.role_id SEPARATOR ', ')
 FROM sys_user_role ur JOIN sys_role r ON ur.role_id=r.role_id
 WHERE ur.user_id=u.user_id AND r.del_flag='0'),''))
FROM sys_user u ORDER BY u.user_id"""
result = subprocess.run(
    [cfg["Mysql"], "--protocol=TCP", f"--host={cfg['DatabaseHost']}",
     f"--port={cfg['DatabasePort']}", f"--user={cfg['DatabaseUser']}",
     "--default-character-set=utf8mb4", "--batch", "--raw", "--skip-column-names",
     cfg["Database"], "--execute", query],
    env=env, check=True, capture_output=True, encoding="utf-8")
# These three values are explicitly recorded in README/docs and the V2 test fixture.
candidates = ["admin123", "Demo2026!Onsite", "V2Changed2026!"]
lines = ["# 当前账号与密码（仅限本机管理员）", "",
         f"生成时间：{datetime.datetime.now():%Y-%m-%d %H:%M:%S}",
         f"数据库：`{cfg['Database']}`", "",
         "本表包含当前数据库全部用户记录，含停用、已删除标记。密码只核验项目已记录的初始密码，未修改任何账号。",
         "未匹配的密码无法由哈希恢复，请由管理员在用户管理中重置。已匹配密码也仅代表生成时刻。",
         "此文件含明文凭据，不得公开、上传 Git 或发给考生；正式考试前请更换演示密码。", "",
         "| ID | 账号 | 姓名 | 角色 | 状态 | 已核验密码 |",
         "|---|---|---|---|---|---|"]
known = 0
def cell(value):
    return str(value or "").replace("|", "\\|").replace("\n", " ").replace("\r", "")
for text in result.stdout.splitlines():
    user = json.loads(text)
    password = None
    for candidate in candidates:
        try:
            if bcrypt.checkpw(candidate.encode(), user["hash"].encode()):
                password = candidate
                break
        except (ValueError, AttributeError):
            break
    known += password is not None
    state = "已删除" if user["deleted"] != "0" else ("启用" if user["status"] == "0" else "停用")
    lines.append("| " + " | ".join(cell(x) for x in
                 [user["id"], user["name"], user["nickname"], user["roles"], state,
                  password or "未知；需管理员重置"]) + " |")
directory = root / "docs/private"
directory.mkdir(parents=True, exist_ok=True)
identity = subprocess.check_output(["whoami"], text=True).strip()
subprocess.run(["icacls", str(directory), "/inheritance:r", "/grant:r",
                f"{identity}:(OI)(CI)F", "*S-1-5-18:(OI)(CI)F"], check=True, capture_output=True)
target = directory / "current-accounts.md"
target.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(f"Exported {len(result.stdout.splitlines())} users; {known} passwords verified. {target}")
