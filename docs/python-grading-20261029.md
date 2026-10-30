# 评分规则、重评与真实 Python 执行

## 本轮状态

- 已实现多选部分分、填空自动判分、评测版本归档与指定提交重评。
- 独立 Docker Linux 沙箱已运行：go-judge 1.13.3，Python 3.12.14，回环地址 15050。
- Java 适配器已实际调用此沙箱。正常输出、语法错误、运行异常、超时、输出超限、内存限制、无效凭据拒绝、网络及指定宿主路径不可见测试通过。
- 后端 102 项测试通过，包含独立 MySQL 测试和真实沙箱测试，未跳过；前后端构建通过。
- 原有 14 项前端回归及新增 1 项重评桌面/手机浏览器测试通过；浏览器测试 API 为模拟接口，不等于真实全链路验收。
- 新版业务后端已于 2026-10-29 启动，回环 API `http://127.0.0.1:8181`，本次 PID 57116；已完成真实接口→业务后端→沙箱→自动评分→重评→复核发布验证。
- **新版前端启动仍被工具执行策略拦截。5177 未启动，真实浏览器验收未完成，不能称为全部上线或公网生产部署。**
- 原 8080 服务、生产库 `cyan_exam` 未修改。原 8081/5176 演示服务未替换。

## 功能入口

### 题库评分规则

编辑多选题时可设置全对得分，或少选固定部分分、错选零分。少选分必须小于该题在考试中的满分。

填空题可选人工、文本自动、数值自动评分。支持多个可接受答案、大小写、首尾/全部空白处理、Unicode 兼容归一化、绝对数值容差、全对计分或每空等分。多个空以英文分号分隔；当前分数为整数，每空等分时题目满分必须整除空数。

发布考试时规则进入不可变快照。以后改题库不会修改已发布考试；未配置规则的历史填空题继续人工批改。学生接口不下发标准答案和评分规则。

### Python

现有 Monaco 工作台提供样例运行与正式提交。公开样例返回 stdout/stderr；正式提交只使用隐藏测试点，学生端不返回隐藏输出及未发布分数。最后一次被接收的正式提交参与计分。

单测试点 CPU 2 秒、墙钟 5 秒、内存 128 MiB、进程 16、stdout/stderr 各 32 KiB。学生代码只在 Linux 沙箱执行，不在 Windows 或业务 JVM 主机执行。公开/隐藏测试点按权重累计，沿用已有完全匹配、数值、JSON 比较器。

Docker 沙箱为内部网络，无 Windows 项目目录或 Docker socket 挂载；回环 HTTP 网关只转发 `/run`、`/config`、`/version`，执行服务要求 Bearer 凭据。镜像固定 Python 基础镜像摘要，go-judge 二进制校验 SHA256。测试点结果记录配置的运行环境标识。

注意：go-judge 容器在 Docker Desktop Linux VM 内需要 privileged 权限。上述测试不是全面容器逃逸或生产安全审计。正式多人环境应使用专用评测主机，并进行补丁、许可证、资源攻击和容量验收。

### 异常与重评

教师批改详情点击“重新评测”，填写原因。服务端校验考试归属、权限、成绩锁定、任务终态和当前版本；历史版本保留分数、逐点结果、源码/题目摘要、原因、操作人、时间。新版本重新运行原提交和原考试快照，不允许改已提交代码或偷偷替换用例。

重评较早提交不会替换最后一次正式提交的成绩；重评最终提交会清除其当前评分，等待重新评测后自动判分。系统服务错误重试，最终失败保持待处理，不直接算学生零分。学生代码错误则正常完成评测、对应测试点不得分。

“评测记录”查看当前/历史版本及逐点结果，可手动刷新。成绩复核或发布锁定后禁止直接重评，须走原成绩更正审批流程。

## 本机启动准备

已准备独立库 `cyan_exam_python_demo_20261029`，来自本机演示库副本，不与旧后端共用。原演示库和验证库已执行一次 `cyan-exam/sql/exam_grading_rules.sql`，副本已包含迁移，**不要重复执行 ALTER**。

本机沙箱容器为 `cyan-python-demo`、`cyan-python-gateway`，密钥在 `D:/app/cyan-exam/sandbox-token.key`，不进入 Git。

后端包：`cyan-exam/cyan-exam-admin/target/cyan-exam-python-demo.jar`。

首次启动或停止本服务后，从仓库根目录运行（8181 已运行时不要重复启动）：

```powershell
./deploy/go-judge/Start-DemoBackend.ps1
```

该脚本仅启动回环 8181、Redis DB3、独立数据库及独立 token，不停止已有服务。数据库用户名和密码沿用本地忽略的应用配置，可用 Spring 环境变量覆盖。先检查 `D:/app/cyan-exam/python-demo-out.log` 和 `python-demo-err.log` 确认启动成功。

另一个终端启动匹配前端：

```powershell
Set-Location D:/work/cyan-exam-main/cyan-exam-vue
$env:EXAM_API_TARGET='http://127.0.0.1:8181'
& D:/app/nodejs/node.exe node_modules/vite/bin/vite.js --host 127.0.0.1 --port 5177 --strictPort
```

启动成功后地址为 `http://127.0.0.1:5177`。账号 `demo_student`、`demo_owner`、`demo_reviewer`，本地演示密码均为 `Demo2026!Onsite`。这些仅为隔离的虚构演示账号，不能直接用于生产。

- 考试 92001/92002：旧初始化数据存在二进制换行编码问题，不再用于演示；保留历史数据不改快照。
- 考试 92011：真实运行、异常、超时、评分、重评和发布验证已执行，总分 15。已结束并发布，不能重复运行测试。
- 考试 92012：修正数据后的交互演示考试。学生 `/exam/answer?examId=92012`；教师 `/exam/grading/detail/92012/91004`。
- 考试 92021：修正后的真实运行、自动评分与版本重评自动化测试已通过（104 秒）；已交卷，不重复运行。92022 为额外独立演示考试。
- 两套题包含 10 分 Python、6 分多选部分分、4 分填空。自动创建时截止日期为创建日起 7 天；逾期需创建新的演示考试。

## 自动验收

真实 API 脚本已执行。它验证 Python 正常/错误/超时、隐藏信息保密、部分分、交卷后理论自动判分、旧版本归档、并发版本拒绝、旧提交不替换成绩、权限与发布锁定。首次联调发现并修复了初始化 SQL 的 `CHAR(10)` 二进制 JSON 编码问题，现显式使用 utf8mb4；不修改历史快照。

```powershell
Set-Location D:/work/cyan-exam-main/cyan-exam-vue
$env:EXAM_PYTHON_DEMO_TEST='1'
$env:EXAM_PYTHON_DEMO_EXAM='<全新测试考试 ID>'
& D:/app/nodejs/node.exe --test tests/python.demo.test.js
```

需使用全新的测试考试，脚本不清空已有成绩。初始化 SQL 现在要求调用方显式选中独立演示库，并允许设置 `@verification_exam`、`@interactive_exam`。

发布验证单独通过 `EXAM_PYTHON_PUBLICATION_TEST=1` 启用，需先完成上述评测，再等待该测试考试结束；交卷不代表整场考试结束。测试 92011 在隔离演示库中结束后已通过发布验证，生产规则未放宽。

本轮最终结果：运行/评分/重评测试 1 项通过（92021），复核/发布/锁定测试 1 项通过（92011）；两阶段分别运行，非目标阶段显示 SKIP。真实 UI 测试未执行，不计入通过数。

`EXAM_PYTHON_UI_TEST=1` 可另行启用真实 UI 检查，需配置 `PLAYWRIGHT_MODULE_PATH` 和已安装浏览器，`EXAM_PYTHON_INTERACTIVE_EXAM` 默认为 92012；教师已发布详情检查可设置 `EXAM_PYTHON_DEMO_EXAM=92011`。本轮未启动前端，不能把跳过的 UI 测试计为通过。

## 发布与回退边界

- 已保存后端包到 `D:/app/cyan-exam/releases/20261029-python/cyan-exam-python-demo.jar`，SHA256 `0BC3B10DE4C7EC949FFEDF3B58C225D7BEFED402962EAAFDD4B29D016B9E8077`。
- 当前进程仍从仓库 target 包启动；不是 Windows 服务，没有配置开机自启。
- 原 8080/8081 后端与 5174/5176 前端保持原状，生产库未迁移。要查看新功能，须启动上述 5177 前端，不要把旧页面当成新版入口。
- 停用本次独立环境只需确认进程命令行和端口后停止对应 8181 后端；不要停止其他 Java 服务，不要删除数据库。原环境不需要数据库回滚。

单独重复运行真实沙箱 Java 测试：

```powershell
Set-Location D:/work/cyan-exam-main/cyan-exam
$env:CYAN_REAL_SANDBOX_TEST='1'
$env:CYAN_SANDBOX_URL='http://127.0.0.1:15050'
$env:CYAN_SANDBOX_TOKEN_FILE='D:/app/cyan-exam/sandbox-token.key'
& D:/app/apache-maven-3.9.12/apache-maven-3.9.12/bin/mvn.cmd test -pl cyan-exam-system -am
```

## 仍不包含

项目目录与附件、第三方库依赖环境、断言脚本、任意成果文件、最高分计分策略、小数与扣分配置、已发布用例更正后的批量重评、200 人性能验收、生产安全及许可证交付审查。
