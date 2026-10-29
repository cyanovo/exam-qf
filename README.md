# 青考 (CyanExam)

**青考** 是一套基于 **若依 (RuoYi-Vue3)** 框架构建的现代化在线考试系统，集题库管理、智能组卷、学生考试、自动批改与 **AI 辅助评分** 于一体。前端采用 Vue 3 + Element Plus，后端采用 Spring Boot 3，界面借鉴 iOS 设计语言（毛玻璃、大圆角），简洁优雅。

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![RuoYi-Vue3](https://img.shields.io/badge/RuoYi-Vue3-3.9.2-brightgreen)](https://github.com/yangzongzhuan/RuoYi-Vue3)

---

## ✨ 核心功能

### 📚 题库管理
- 支持**单选题**、**多选题**、**判断题**、**填空题**、**简答题**五种题型
- 选项以 JSON 格式存储在 `sub_title` 字段，前端动态渲染编辑
- **AI 智能导入**：调用 DeepSeek Chat API，将自然语言试题转换为标准 JSON 并批量入库
- **Excel 导入/导出**：支持自定义文本格式，后端自动解析
- 题目分类（Java / Python / C / HTML）、难度（1-5 星）、题型筛选，支持关键词搜索

### 🧑‍🎓 学生管理
- 扩展若依用户体系，新增 `cyan_student` 表（学号、班级、年级）
- **一键批量创建学生账号**：前缀 + 起始编号，自动分配学生角色与部门，密码 BCrypt 加密
- 遵循若依逻辑删除策略，保护数据完整性

### 📅 考试管理
- 考试基本信息：名称、开始时间、结束时间（精确到分钟）、状态控制
- **学生分配**：穿梭框批量选择学生，保存至 `exam_student` 关联表
- **试题选择**：带分类、难度、题型筛选的穿梭框，从题库中抽取试题存入 `exam_question`
- 试卷根据关联表动态生成，可设置各题排序

### 🖊️ 学生考试端
- **考试等待页**：自动检测已分配考试，显示考试时间与状态，倒计时或"进入考试"按钮
- **答题页**：
  - 题目导航（题号按钮，已答 / 未答状态区分，颜色标识）
  - 剩余时间实时倒计时，考试结束自动提交
  - 答案实时异步保存，防止丢失（切换题目、点击选项均自动触发保存）
  - 全题型适配：单选 / 判断圆形选项，多选复选框，填空 / 简答文本框
  - 已保存答案自动回显

### 🧑‍🏫 教师批改端
- **批改列表**：按考试查看已分配学生及其答题状态
- **自动批改**：单选 / 多选 / 判断题直接比对标准答案，可自定义每题分值
- **AI 智能批改**：收集主观题（填空、简答）与批改标准，通过 DeepSeek API 返回评分 JSON，自动填入分数
- 手动调分，总分 / 客观分 / 主观分实时统计
- 支持批量提交批改成绩

### 🎨 界面美化
- 侧边栏：毛玻璃效果（`backdrop-filter: blur`）+ 大圆角菜单项
- 全局卡片、对话框采用半透明背景与模糊效果
- 深色顶栏（Google Material 风格），可切换主题
- 自定义加载页：浅青色渐变 + 毛玻璃进度条
- 全局 SCSS 变量定制 Element Plus 主题色系

---

## 🛠️ 技术栈

| 层级     | 技术                                |
| -------- | ----------------------------------- |
| 后端框架 | Spring Boot 3.5.11                  |
| 编程语言 | Java 17                             |
| 安全认证 | Spring Security + JWT               |
| ORM      | MyBatis 3.0.5                       |
| 数据库   | MySQL 8.0+                          |
| 连接池   | Druid 1.2.28                        |
| 缓存     | Redis（Lettuce 客户端）              |
| 前端框架 | Vue 3.5（Composition API）           |
| UI 库    | Element Plus 2.13                   |
| 构建工具 | Vite 6                              |
| 状态管理 | Pinia 3                             |
| 路由     | Vue Router 4                        |
| HTTP 库  | Axios                               |
| AI 集成  | DeepSeek Chat API（前端直接调用）     |
| Excel    | Apache POI 4.1.2                    |
| JSON     | Fastjson2 2.0.61                    |
| 文档     | SpringDoc (OpenAPI 3)               |
| 样式     | SCSS + Sass Embedded                |
| 图表     | ECharts 5.6                         |

---

## 🚀 快速启动

### 1. 环境要求
- JDK 17+
- MySQL 8.0+
- Redis（默认 localhost:6379，无密码）
- Node.js 16+
- Maven 3.6+

### 2. 数据库初始化
1. 创建数据库 `cyan_exam`（UTF-8，utf8mb4）
2. 执行若依框架基础 SQL：`cyan-exam/sql/ry_20260417.sql`
3. 如需定时任务功能，执行 `cyan-exam/sql/quartz.sql`
4. 扩展表（`cyan_question`、`cyan_student`、`exam_info`、`exam_student`、`exam_question`、`exam_answer_record`）由应用启动时自动建表，或见下方附录手动创建
5. 升级已有数据库时，先执行 `cyan-exam/sql/exam_session.sql` 创建考试会话表，再部署新版后端；全新数据库也需执行该脚本。目前项目不会自动迁移此表。

### 3. 后端启动
```bash
cd cyan-exam
# 修改 cyan-exam-admin/src/main/resources/application-druid.yml 中的数据库连接信息
mvn clean install -DskipTests
cd cyan-exam-admin
mvn spring-boot:run
```

### 4. 前端启动
```bash
cd cyan-exam-vue
npm install
npm run dev
```

### 5. 访问系统
- 后端接口：http://localhost:8080
- Druid 监控：http://localhost:8080/druid（账号 `ruoyi`，密码 `123456`）
- Swagger 文档：http://localhost:8080/swagger-ui.html
- 前端页面：http://localhost:80（或根据 Vite 输出）
- 默认管理员：`admin` / `admin123`

---

## 📁 项目结构

```
cyan-exam
├── cyan-exam                     # 后端 Maven 聚合项目
│   ├── cyan-exam-admin           # Spring Boot 启动模块（入口、配置）
│   ├── cyan-exam-common          # 公共工具、注解、异常、基础类
│   ├── cyan-exam-framework       # 框架核心（安全过滤器、JWT 处理、权限）
│   ├── cyan-exam-system          # 业务模块（题库、考试、学生、批改）
│   ├── cyan-exam-quartz          # 定时任务模块
│   ├── cyan-exam-generator       # 代码生成器
│   ├── sql                       # 数据库初始化脚本
│   └── doc                       # 文档
├── cyan-exam-vue                 # Vue 3 前端
│   ├── public
│   ├── src
│   │   ├── api                   # 接口请求（按模块拆分）
│   │   ├── assets/styles         # 自定义主题样式（sidebar.scss 等）
│   │   ├── components            # 公共组件
│   │   ├── layout                # 布局组件（侧边栏、导航栏、标签页）
│   │   ├── router                # 路由配置
│   │   ├── store                 # Pinia 状态管理
│   │   ├── utils                 # 工具函数
│   │   ├── views
│   │   │   ├── exam              # 考试模块
│   │   │   │   ├── wait          # 考试等待页
│   │   │   │   ├── answer        # 学生答题页
│   │   │   │   └── grading       # 教师批改（列表、详情）
│   │   │   ├── question          # 题库管理
│   │   │   ├── system            # 系统管理
│   │   │   │   ├── info          # 考试信息管理
│   │   │   │   ├── student       # 学生管理
│   │   │   │   ├── user          # 用户管理
│   │   │   │   ├── role          # 角色管理
│   │   │   │   ├── dept          # 部门管理
│   │   │   │   ├── menu          # 菜单管理
│   │   │   │   ├── dict          # 字典管理
│   │   │   │   ├── config        # 参数配置
│   │   │   │   ├── notice        # 通知公告
│   │   │   │   └── post          # 岗位管理
│   │   │   ├── monitor           # 监控管理
│   │   │   └── tool              # 开发工具
│   │   └── plugins               # 插件
│   ├── vite.config.js
│   └── package.json
└── Redis-x64-5.0.14.1            # Redis（开发环境自带）
```

---

## 🤖 AI 功能说明

> AI 功能通过前端直接调用 **DeepSeek Chat API**，需自行获取 API Key（[https://platform.deepseek.com/api_keys](https://platform.deepseek.com/api_keys)）。API Key 存储在浏览器 localStorage 中。

### AI 智能导入
1. 在题库管理页面点击 **"AI 导入"** 按钮
2. 粘贴试题文本（支持自然语言、Word 复制内容），填写 DeepSeek API Key
3. AI 自动解析题型、分类、难度、选项与答案，转换为系统标准 JSON 格式
4. 预览解析结果，确认后一键批量导入到题库

### AI 辅助批改
1. 进入**批改详情页**，设置各题型分值（客观题 / 主观题）
2. 填写**批改标准**（评分要点、参考答案、分值说明）
3. 点击 **"AI 智能批改"**，系统自动收集所有主观题（填空、简答）及其学生答案
4. 调用 DeepSeek 返回每道主观题的得分与简短评语，自动填入分数
5. 教师可手动调整任何分数，确认后提交

---

## 📦 扩展数据库表

> 以下扩展表可能与若依基础表一起通过应用自动建表。若未自动创建，请手动执行。

```sql
-- 题库表
CREATE TABLE `cyan_question` (
  `question_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `category` varchar(50) NOT NULL COMMENT '分类',
  `difficulty` tinyint(4) DEFAULT '1' COMMENT '难度 1-5',
  `question_type` varchar(20) NOT NULL COMMENT 'SINGLE_CHOICE/MULTI_CHOICE/JUDGE/FILL_BLANK/SHORT_ANSWER',
  `title` text NOT NULL COMMENT '题干',
  `sub_title` text COMMENT '选项JSON',
  `answer` text NOT NULL COMMENT '答案',
  `status` char(1) DEFAULT '0' COMMENT '0启用 1停用',
  `create_by` varchar(64) DEFAULT '',
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) DEFAULT '',
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 学生扩展表
CREATE TABLE `cyan_student` (
  `student_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `student_no` varchar(50) NOT NULL COMMENT '学号',
  `class_name` varchar(100) DEFAULT '' COMMENT '班级',
  `grade` varchar(50) DEFAULT '' COMMENT '年级',
  `create_by` varchar(64) DEFAULT '',
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) DEFAULT '',
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`student_id`),
  UNIQUE KEY `uk_user_id` (`user_id`),
  UNIQUE KEY `uk_student_no` (`student_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 考试信息表
CREATE TABLE `exam_info` (
  `exam_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `exam_name` varchar(200) NOT NULL,
  `start_time` datetime DEFAULT NULL,
  `end_time` datetime DEFAULT NULL,
  `status` char(1) DEFAULT '0' COMMENT '0正常 1停用',
  `create_by` varchar(64) DEFAULT '',
  `create_time` datetime DEFAULT NULL,
  `update_by` varchar(64) DEFAULT '',
  `update_time` datetime DEFAULT NULL,
  `remark` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`exam_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 考试-学生关联表
CREATE TABLE `exam_student` (
  `exam_id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  PRIMARY KEY (`exam_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 考试-试题关联表
CREATE TABLE `exam_question` (
  `exam_id` bigint(20) NOT NULL,
  `question_id` bigint(20) NOT NULL,
  `sort` int(11) DEFAULT '0' COMMENT '排序',
  PRIMARY KEY (`exam_id`,`question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 答题记录表
CREATE TABLE `exam_answer_record` (
  `record_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `exam_id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  `question_id` bigint(20) NOT NULL,
  `answer` text COMMENT '学生答案',
  `score` int(11) DEFAULT '0' COMMENT '得分',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`record_id`),
  UNIQUE KEY `uk_exam_user_question` (`exam_id`,`user_id`,`question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 插入学生角色（如不存在）
INSERT INTO `sys_role` (`role_name`, `role_key`, `role_sort`, `data_scope`, `status`, `del_flag`, `create_by`, `create_time`, `remark`)
SELECT '学生', 'student', 4, '5', '0', '0', 'admin', NOW(), '学生角色'
WHERE NOT EXISTS (SELECT 1 FROM `sys_role` WHERE `role_key` = 'student');
```

---

## 🏗️ 后端核心类说明

| 类名                       | 说明                   |
| -------------------------- | ---------------------- |
| `CyanQuestionController`   | 题库管理接口            |
| `CyanStudentController`    | 学生管理接口            |
| `CyanExamInfoController`   | 考试信息管理接口         |
| `ExamStudentController`    | 考试-学生关联接口        |
| `TeacherExamController`    | 教师批改接口            |
| `CyanQuestion`             | 题库实体               |
| `CyanStudent`              | 学生实体               |
| `CyanExamInfo`             | 考试信息实体            |
| `ExamAnswerRecord`         | 答题记录实体            |

---

## 📝 许可证

本项目基于 [RuoYi-Vue3](https://github.com/yangzongzhuan/RuoYi-Vue3) 开发，继续遵守其 **MIT** 许可证。您在遵守 MIT 协议的前提下可自由使用、修改、分发。

---

## 🙏 致谢

- [RuoYi-Vue3](https://github.com/yangzongzhuan/RuoYi-Vue3) —— 高效的企业级后台开发框架
- [DeepSeek](https://deepseek.com/) —— 强大且经济的 AI 模型服务
- [Element Plus](https://element-plus.org/) —— 优秀的 Vue 3 组件库
- [Spring Boot](https://spring.io/projects/spring-boot) —— Java 生态领先的应用框架

---

**Enjoy your exam! 📝**
