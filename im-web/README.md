# IM Web（Vue 3）

对接仓库根目录 Spring Boot IM 后端的 **仿微信风格** Web 客户端，支持 **中文 / English**（`vue-i18n`，语言保存在 `localStorage`）。

## 技术栈

- Vue 3 + TypeScript + Vite
- Vue Router、Pinia
- vue-i18n 10
- Axios（`ApiResponse` 约定与后端一致）
- STOMP（`@stomp/stompjs`）订阅 `/user/queue/messages`，处理 `MESSAGE`、`RECALL` 等事件

## 环境变量

通过根目录下的 `.env.development` / `.env.production` 配置（Vite 仅暴露以 `VITE_` 开头的变量）。

| 变量 | 含义 |
|------|------|
| `VITE_API_BASE` | HTTP API 的 baseURL，默认 `/api`。开发时由 Vite 代理到后端；生产若前后端不同源，可改为完整地址，例如 `https://api.example.com/api`。 |
| `VITE_WS_URL` | WebSocket（STOMP）完整地址，须包含 SockJS/STOMP 路径。开发示例：`ws://127.0.0.1:8080/ws-chat`；生产建议使用 `wss://你的域名/ws-chat`。 |

未设置 `VITE_WS_URL` 时，聊天页不会建立实时连接（仍可用轮询或手动刷新会话；当前实现以 WS 为主）。

## 运行与构建

```bash
cd im-web
npm install
npm run dev
```

默认前端：`http://127.0.0.1:5173`。开发环境下 `/api` 由 Vite 代理到 `http://127.0.0.1:8080`（见 `vite.config.ts`）。

```bash
npm run build
```

产物在 `dist/`，需由支持 SPA 的路由回退（或部署到静态托管并配置 `try_files` / `fallback`）以免刷新 404。

## 功能说明

- **登录 / 注册**：手机号或邮箱，与后端 `AuthType` 一致。
- **主界面布局**：左侧窄栏在「聊天 / 通讯录」之间切换；中间为会话列表或通讯录面板；右侧为当前会话消息与输入区。
- **会话**：列表展示、未读角标、进入会话、上拉加载更多历史、进入会话时调用已读接口同步未读。
- **通讯录**：好友申请（通过 / 拒绝）、按关键字搜索用户、加好友、从好友列表 **发消息**（调用后端创建或复用单聊会话后进入聊天）。
- **消息**：文本发送；**图片**：选择本地图片 → `POST /api/upload/media` 上传 → 发送 `IMAGE` 类型消息；展示图片、视频控件、合并转发卡片摘要、位置摘要、名片占位、文件链接等。
- **群聊**：会话标题旁 **群资料** 弹层，拉取 `GET .../group-detail` 展示群名、公告、成员与群主标识。
- **多语言**：顶栏语言切换；文案位于 `src/locales/*.json`。

## 后端要求

- 已启动 IM 服务（开发默认 `8080`）。
- CORS：若前后端不同源且不走反向代理，需在 Spring 中放行前端源；本地开发使用 Vite 代理即可。

## 相关后端接口（前端已用）

- `POST /api/auth/login`、`POST /api/auth/register`、资料与 Token 等
- `GET /api/conversations/list`、`POST /api/conversations/single/{targetUserId}`、`POST .../read`、`GET .../group-detail`
- `GET /api/messages/conversation/{id}`、`POST /api/messages/send`
- `GET /api/users/search`、`GET/POST .../friends/*`（好友与申请）
- `POST /api/upload/media`（字段名 `file`，图片可传 `mediaType=IMAGE`）

具体路径以后端 Swagger 为准。
