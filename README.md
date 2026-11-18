# 华脉万象 — 非遗文化数字传承平台

融合大模型与AIGC技术的非遗全景探索引擎

---

## 技术架构

### 后端

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17 | 编程语言 |
| Spring Boot | 3.2.4 | 后端框架 |
| Spring Security | 6.2.x | JWT 认证授权 |
| Spring WebFlux | — | SSE 流式响应（AI 对话） |
| MyBatis Plus | 3.5.7 | ORM 框架 |
| MySQL | 8.0+ | 关系型数据库 |
| Redis | 7.0+ | 缓存（视图计数、仪表盘统计、库存预热） |
| jjwt | 0.12.3 | JWT 令牌管理 |
| Lombok | 1.18.32 | 代码简化 |

### 前端

| 技术 | 版本 | 说明 |
|------|------|------|
| Vue 3 | 3.5.x | Composition API (`<script setup>`) |
| Vite | 8.x | 构建工具 |
| Ant Design Vue | 4.x | UI 组件库 |
| Pinia | 3.x | 状态管理 |
| Vue Router | 4.x | 路由管理 |
| ECharts | 6.x | 数据可视化（地图、图表） |
| vue-echarts | 8.x | ECharts Vue 集成 |
| marked | 18.x | Markdown 渲染（AI 对话） |
| element-china-area-data | 6.x | 省市区三级联动数据 |

### AI 服务

| 服务 | 用途 |
|------|------|
| DeepSeek API | 非遗知识智能问答、AI 导游对话 |
| 智谱 AI (BigModel) | AIGC 图片生成 |
| 百度千帆 (ERNIE-Image-Turbo) | AIGC 图片生成（备选） |

---

## 项目结构

```
华脉万象/
├── backend/                        # Spring Boot 后端
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/yiyun/
│       │   ├── YiyunApplication.java      # 启动类 (@EnableAsync, @MapperScan)
│       │   ├── config/                     # 配置类 (8 个)
│       │   │   ├── SecurityConfig.java     # Spring Security + JWT 过滤链
│       │   │   ├── CorsConfig.java         # CORS 跨域配置
│       │   │   ├── WebMvcConfig.java       # 静态资源映射 & 上传路径
│       │   │   ├── MybatisPlusConfig.java  # 分页插件
│       │   │   ├── MyBatisPlusMetaHandler.java  # 自动填充时间戳
│       │   │   ├── WebClientConfig.java    # WebClient Bean（百度千帆）
│       │   │   ├── GlobalExceptionHandler.java  # 全局异常处理
│       │   │   └── DatabaseInitializer.java     # 自动执行 schema.sql
│       │   ├── controller/                 # REST 控制器 (16 个)
│       │   ├── service/                    # 业务服务 (12 个)
│       │   │   ├── BaiduAuthHelper.java    #   百度 Token 缓存与获取（公共组件）
│       │   │   ├── BaiduQianfanService.java
│       │   │   ├── BaiduSpeechService.java
│       │   │   ├── ImageGenerationService.java  # AIGC 生成外观
│       │   │   ├── ZhipuImageService.java
│       │   │   ├── DeepSeekService.java
│       │   │   └── ...
│       │   ├── mapper/                     # MyBatis Plus Mapper (21 个)
│       │   ├── model/
│       │   │   ├── entity/                 # 数据库实体 (21 个)
│       │   │   └── dto/                    # 数据传输对象 (5 个)
│       │   └── util/
│       │       ├── JwtUtil.java            # JWT 工具类
│       │       └── SecurityUtil.java       # 安全工具类
│       └── resources/
│           ├── application.yml             # 主配置（含 dev profile）
│           └── db/
│               └── schema.sql              # 完整建表脚本 + 种子数据
│
├── frontend/                        # Vue 3 前端
│   ├── vite.config.js               # Vite 配置（含 API 代理）
│   ├── package.json
│   └── src/
│       ├── main.js                  # 应用入口
│       ├── App.vue                  # 根组件
│       ├── api/                     # API 接口封装 (16 个)
│       ├── assets/styles/           # 全局样式 & 动画
│       ├── components/              # 公共组件
│       │   ├── Pagination.vue       #   统一分页组件
│       │   ├── StarRating.vue       #   星级评分组件
│       │   └── layout/              #   布局组件
│       │       ├── AppHeader.vue
│       │       └── AppFooter.vue
│       ├── composables/             # 组合式函数
│       │   └── useUserNavigation.js
│       ├── data/                    # 静态数据
│       │   └── regionData.js        #   省市区三级联动数据
│       ├── router/index.js          # 路由配置（22 条路由 + 导航守卫）
│       ├── stores/user.js           # Pinia 用户状态管理
│       ├── utils/                   # 工具函数
│       │   ├── constants.js         #   状态映射常量
│       │   └── storage.js           #   localStorage 封装
│       └── views/                   # 页面组件 (21 个)
│           └── admin/               #   管理员页面 (4 个)
│
├── uploads/                         # 文件上传目录
├── heritage/                        # 非遗项目示例图片
└── README.md
```

---

## 功能模块

### 用户系统
- 注册/登录，JWT 无状态认证
- 三角色权限模型：普通用户（USER） / 传承人（INHERITOR） / 管理员（ADMIN）
- 路由导航守卫 + 角色级访问控制
- 个人中心：资料编辑、密码修改、收藏/预约/作品管理
- 传承人认证申请（实名 + 资质证书），管理员审核

### 非遗探索
- 分类筛选（传统技艺/民间艺术/曲艺戏剧/民俗节庆/传统医药/民间音乐）
- 级别筛选（国家级/省级/市级）
- 地区筛选 + 关键词搜索，分页浏览
- 项目详情页：图片轮播、介绍、关联体验/活动、评论（含回复、表情、图片）
- 点赞（toggle）、收藏、关注传承人
- Redis 视图计数（IP + 用户级别去重）

### 体验预约
- 三种类型：线下工坊（WORKSHOP）/ 线上活动（ONLINE）/ 直播（LIVE）
- 传承人发布体验项目，管理员审核
- 用户预约：选择日期/时间/人数，填写联系信息
- 名额管理（quota / booked），防重复预约
- 三端状态流转：用户取消 → 传承人确认/完成 → 管理员审核

### AI 文创工坊
- **AI 对话**：非遗知识智能问答，DeepSeek 大语言模型驱动，含非遗文化 System Prompt
- **AI 导游**：会话式导览，快捷提示词，流式 SSE 响应 + Markdown 渲染
- **AIGC 生成**：文字/图片/风格迁移/3D 建模，智谱 AI + 百度千帆双引擎
- **模板市场**：提示词模板库，一键复用
- **作品画廊**：生成作品保存、浏览、管理

### 知识图谱
- ECharts 力导向图可视化
- 5 种节点：非遗项目 / 地区 / 传承人 / 技艺 / 朝代
- 5 种关系：发源于 / 继承自 / 运用技艺 / 源于朝代 / 相关
- 节点搜索、类型过滤、聚焦展示
- MySQL 关系型数据库模拟图结构（无需图数据库）

### 社区互动
- 非遗项目评论（支持回复嵌套、表情、图片）
- 点赞/取消点赞（评论 + 非遗项目双目标类型）
- 收藏非遗项目
- 关注传承人（含互相关注标记）

### 数据大屏
- ECharts 可视化仪表盘，五层布局
- **今日实时**：活跃用户 / 生成任务 / 新增作品 / 预约 / 收藏
- **核心指标**：非遗项目总数、用户总数、AIGC 作品总数、预约总数
- **图表分析**：分类占比饼图、AIGC 类型柱状图、月度预约趋势折线图、热门非遗 TOP5、日活统计
- 每 5 分钟自动刷新统计 Redis 缓存

### 管理后台
- **非遗项目管理**：审核非遗项目（通过/驳回），多条件筛选，上/下架
- **体验项目管理**：审核体验项目，状态筛选（待审核/已通过/已驳回/已下架）
- **预约订单管理**：全平台订单管理，状态流转
- **系统设置**：用户管理（封禁/解封）、传承人认证审核

---

## 数据库设计

21 张表，`utf8mb4_unicode_ci` 字符集，完整外键约束 + 级联删除。

### 核心业务表

| 表名 | 说明 | 核心字段 |
|------|------|----------|
| `user` | 用户表 | id, username, password, role (USER/ADMIN/INHERITOR), status (ACTIVE/BANNED) |
| `heritage` | 非遗项目表 | id, name, category, region, level, description, image_url, views, status, audit_note |
| `heritage_image` | 非遗图片表 | id, heritage_id, image_url, sort_order |
| `experience` | 体验项目表 | id, name, heritage_id, type (WORKSHOP/ONLINE/LIVE), price, quota, booked_num, status, document_url |
| `booking_order` | 预约订单表 | id, order_no, user_id, experience_id, booking_date, people, status |
| `activity` | 活动表 | id, name, heritage_id, type, location, start_time, end_time, status |
| `comment` | 评论表 | id, heritage_id, user_id, content, parent_id, images, status |
| `follow` | 关注关系表 | id, follower_id, followed_id, mutual |
| `favorite` | 收藏表 | id, user_id, heritage_id |
| `like_record` | 点赞记录表 | id, user_id, target_type (HERITAGE/COMMENT), target_id |
| `inheritor_application` | 传承人申请表 | id, user_id, real_name, id_card, skill, certificates, status |
| `file_upload` | 文件上传表 | id, user_id, file_path, file_url, file_type, file_size |

### AI & 图谱表

| 表名 | 说明 |
|------|------|
| `ai_chat_history` | AI 对话历史（user_id, session_id, question, answer, model） |
| `ai_generate_task` | AIGC 生成任务（task_type, prompt, status, result_url） |
| `ai_artwork` | AIGC 作品（title, file_url, category, tags, like_count） |
| `ai_prompt_template` | 提示词模板（title, prompt, type, is_public, use_count） |
| `ai_heritage_relation` | AI 生成任务与非遗项目关联 |
| `ai_statistics` | 平台统计日表（仪表盘数据聚合） |
| `graph_node` | 知识图谱节点（node_id, name, type, properties） |
| `graph_relationship` | 知识图谱关系（source_id, target_id, relation_type） |
| `emoji` | 表情包（name, image_url, category） |

### 种子数据

- 41 个用户（1 管理员 + 2 普通用户 + 38 传承人）
- 46 个非遗项目（覆盖 30+ 省区市，国家级/省级/市级三级）
- 28 个体验项目（线下工坊 + 线上活动）、12 条预约订单
- 16 条评论（含传承人回复）、23 条点赞、16 条收藏、12 条关注关系
- 39 条传承人认证申请（38 已通过 + 1 待审核）
- 4 个 AIGC 生成任务、3 个 AI 作品、5 条 AI 对话历史
- 35 个知识图谱节点 + 29 条关系

---

## API 接口概览

| 基础路径 | 说明 | 主要端点 |
|----------|------|----------|
| `/api/user` | 用户管理 | login, register, getUserInfo, changePassword, check*, list, ban/unban |
| `/api/heritage` | 非遗项目 | list (分页+筛选), detail, search, categories, CRUD, admin approve/reject |
| `/api/experiences` | 体验项目 | list, detail, CRUD, my, admin approve/reject, deactivate |
| `/api/booking` | 预约订单 | create, my, cancel, inheritor approve/reject/complete, admin list |
| `/api/activity` | 活动管理 | list, detail, CRUD, admin approve/reject |
| `/api/comment` | 评论系统 | list (含用户+点赞信息), replies, create, reply, delete, like/unlike |
| `/api/follow` | 关注关系 | follow, unfollow, check, following, followers |
| `/api/favorite` | 收藏管理 | add, remove, list, check |
| `/api/likes` | 点赞系统 | toggle, check, count, my |
| `/api/deepseek` | AI 对话 | chat (SSE 流式) |
| `/api/ai` | AIGC 创作 | generate, templates, artworks, save/delete, stream (SSE) |
| `/api/inheritor` | 传承人 | list, apply, my application, admin approve/reject |
| `/api/file` | 文件上传 | upload (image/video) |
| `/api/statistics` | 数据统计 | dashboard, heritage count/category/top5, AIGC types, booking trend, daily active |
| `/api/graph` | 知识图谱 | full graph, node neighbors, search, type filter |
| `/api` | 健康检查 | health check |

统一响应格式：`{ code: 200, msg: "success", data: ... }`

---

## 安装运行

### 环境要求

| 依赖 | 版本 | 说明 |
|------|------|------|
| JDK | 17+ | Java 开发环境 |
| Maven | 3.6+ | 后端构建工具 |
| Node.js | 20+ | 前端开发环境 |
| MySQL | 8.0+ | 关系型数据库 |
| Redis | 7.0+ | 缓存数据库 |

### 1. 数据库初始化

```sql
CREATE DATABASE yiyun CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

应用首次启动时会自动执行 `backend/src/main/resources/db/schema.sql`（含建表语句和种子数据），无需手动导入。

### 2. 后端配置

编辑 `backend/src/main/resources/application.yml`，或通过环境变量覆盖默认值：

| 环境变量 | 用途 | 默认值 |
|----------|------|--------|
| `YIYUN_DATASOURCE_URL` | 数据库连接 | `jdbc:mysql://localhost:3306/yiyun?...` |
| `YIYUN_DATASOURCE_USERNAME` | 数据库用户名 | `root` |
| `YIYUN_DATASOURCE_PASSWORD` | 数据库密码 | `123456` |
| `YIYUN_REDIS_HOST` | Redis 地址 | `localhost` |
| `YIYUN_REDIS_PORT` | Redis 端口 | `6379` |
| `YIYUN_DEEPSEEK_API_KEY` | DeepSeek API Key | 内置默认 Key |
| `YIYUN_BAIDU_API_KEY` | 百度千帆 API Key | 空（不使用则留空） |
| `YIYUN_BAIDU_SECRET_KEY` | 百度千帆 Secret Key | 空 |
| `YIYUN_JWT_SECRET` | JWT 签名密钥 | 内置默认值 |
| `YIYUN_UPLOAD_PATH` | 文件上传路径 | `../uploads`（项目根目录的 uploads/） |

### 3. 后端启动

```bash
cd backend
mvn spring-boot:run
# 服务运行在 http://localhost:8080
```

### 4. 前端启动

```bash
cd frontend
npm install
npm run dev
# 开发服务器运行在 http://localhost:5173
```

前端开发服务器已配置 API 代理：
- `/api` → `http://localhost:8080`
- `/uploads` → `http://localhost:8080`
- `/deepseek-api` → `https://api.deepseek.com`
- `/zhipu-api` → `https://open.bigmodel.cn`

### 5. 默认账户

| 角色 | 用户名 | 密码 |
|------|--------|------|
| 管理员 | admin | admin123 |
| 普通用户 | zhangsan | 123456 |
| 普通用户 | lisi | 123456 |
| 传承人 | wangxiuying | 123456 |
| 传承人 | zhengriqing | 123456 |

---

## 代码质量

本项目已完成前后端代码清理与优化：

### 后端清理
- **实体完整性** — Heritage 实体补充 `audit_note`、`audited_at` 字段；Experience 实体补充 `document_url` 字段，与数据库 schema 完全对齐
- **Mapper 精简** — 移除 ExperienceMapper 中无 SQL 映射的死方法（`selectByHeritageId`、`searchByName`），全部通过 MyBatis-Plus `LambdaQueryWrapper` 实现
- **去重** — WebMvcConfig 移除重复 CORS 配置（统一由 CorsConfig 管理）；提取 BaiduAuthHelper 公共组件，消除百度千帆、语音服务中的 Token 获取重复代码
- **清理** — 删除硬编码占位定时任务（StatTask、StockLoaderTask），移除 `@EnableScheduling` 注解

### 前端清理
- **运行时 Bug 修复** — AdminLoginView 引用不存在的 Pinia store 成员（getUser→userInfo, logoutAction→logout）；streamChat 回调静默失效导致 AI 导游无响应
- **分页去重** — 提取 Pagination.vue 公共组件，消除 10 个视图页面约 450 行重复代码（JS + HTML + CSS）
- **死文件清理** — 删除未使用的 style.css（Vite 模板遗留）、vite.svg、vue.svg、空 mock/ 目录
- **导入清理** — 移除 4 个视图中未使用的 import（computed、watch、request、getNodeNeighbors）
- **配置统一** — index.html 标题修正，deepseek.js 默认 API URL 统一为 `/api`

---

## 项目特点

1. **AI 深度融合** — DeepSeek 大模型智能问答 + 智谱/百度千帆双引擎 AIGC 创作
2. **知识图谱可视化** — 基于 ECharts 的力导向图，MySQL 模拟图结构，零额外依赖
3. **完整的审核工作流** — 非遗项目 → 体验发布 → 用户预约 → 订单流转，全链路状态管理
4. **三角色权限模型** — 普通用户 / 传承人 / 管理员，路由守卫 + 接口鉴权双重保障
5. **数据大屏** — 实时仪表盘，ECharts 多图表可视化，Redis 缓存聚合
6. **传统美学设计** — 朱砂红/竹青绿/金色配色系统，玻璃拟态卡片，Noto Serif SC 衬线字体
7. **模块化架构** — 前后端分离，Vue 3 Composition API，16 个独立 API 模块
8. **开箱即用** — 自动建表 + 种子数据，Redis 缓存预热，环境变量覆盖配置

---

## License

MIT License

---

*以 AI 为引，以文化为根，让非遗在数字时代焕发新生*
