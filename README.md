# IM Server

基于 `Spring Boot 3 + Java 21 + Maven + MySQL 5.7 + Redis + JWT + WebSocket + MinIO + MyBatis-Plus` 的即时通讯后端骨架。

当前已落地的第一版能力：

- 手机号/邮箱注册登录
- JWT 鉴权
- 用户搜索
- 黑名单/拉黑
- 好友申请、通过、好友列表、删除好友、好友备注
- 个人资料、头像修改
- 单聊文本消息
- 会话列表 DTO
- 会话置顶、免打扰、会话备注
- 会话草稿
- 群聊与群成员
- 群详情、群成员列表
- 群主转让、管理员角色
- 群名称、群头像、群公告修改
- 消息收藏
- 收藏中心增强
- 收藏分类
- 消息分页拉取
- 消息搜索
- 消息撤回
- 消息转发
- 消息编辑
- 消息举报
- 消息送达 ACK
- 消息已读/送达详情列表
- 消息反应
- 消息置顶/重要消息
- 消息多选操作
- 逐消息已读记录
- 单边删除消息
- 清空聊天记录
- 系统通知消息
- 会话未读数查询
- 群主拉人、踢人，成员退群
- 删除会话（隐藏当前用户视角）
- 在线状态查询
- WebSocket 正在输入、在线状态推送
- 群聊 @成员 / @全体
- 黑名单校验已接入加好友、建单聊、单聊发消息；群成员列表展示「我拉黑对方 / 对方拉黑我」；@ 提醒在互拉黑时不推送
- 好友分组（标签）、按标签筛选好友列表
- 会话归档、已隐藏会话列表、恢复会话、多端同步游标
- 群全员禁言、群成员禁言（禁言至指定时间）
- 群邀请链接（Token，可选过期与次数上限）
- 合并转发（`MERGE` 类型聊天记录卡片）、位置（`LOCATION`）、名片（`CONTACT`）结构化消息
- 验证码（Redis，开发环境日志打印码）、可选注册强校验、`reset-password`
- Refresh Token、登录会话列表、登出单设备/全部设备
- 第三方占位登录：`POST /api/auth/oauth/login`（`provider` + `openId` 首次自动建号）
- 离线推送占位：登记设备 Token 后，对离线用户写日志（可替换为 FCM/APNs）
- 表情包商店（管理员维护 Pack/Item，全员拉取列表）
- 管理员查看举报列表
- 图片/视频文件上传
- 语音消息基础结构
- 视频消息元信息
- 基于 Agora 的 1v1 语音通话（Web 端 MVP）
- WebSocket 实时消息推送
- Swagger 接口文档

## 注册验证码（短信 / 邮箱）

- **默认策略**：`app.auth.phone-auth-enabled` 默认为 **`false`**，**仅允许邮箱**注册/登录/发码；前端亦只展示邮箱。若需恢复手机号，设为 `true` 并配置短信（见下）。
- **流程**：前端调用 `POST /api/auth/send-code`（`purpose=REGISTER`）→ 后端在 Redis 写入 6 位码并发送 → 用户提交 `POST /api/auth/register` 时携带 `verifyCode`。
- **强制校验**：`app.auth.verify-on-register: true` 时注册必须带正确验证码；为 `false` 时验证码可选（仍可与填写的码校验）。
- **邮箱**：默认按 **Gmail SMTP**（`smtp.gmail.com:587`）写在 `application.yml`；账号口令请用环境变量 **`MAIL_USERNAME`**、**`MAIL_PASSWORD`**（Gmail 须使用「应用专用密码」，勿提交仓库）。亦可本地建 `application-local.yml` 覆盖且勿入库。
- **手机号（开发，`phone-auth-enabled: true` 时）**：默认 `app.auth.sms-stub-mode: true`，验证码只打在服务端日志（`VerificationCodeNotifyService` / `LoggingSmsVerifyCodeSender`）。
- **手机号（生产）**：
  - **方式 A**：配置 `app.auth.sms-webhook.enabled: true` 与 `app.auth.sms-webhook.url`，后端向该地址 **POST JSON**：`phone`、`code`、`purpose`、`ttlSeconds`；可选 `header-name` / `header-value`。由你的服务调用阿里云/腾讯云短信。
  - **方式 B**：自行实现 `SmsVerifyCodeSender` 并注册为 Spring Bean（可配合 `@Primary`），对接运营商 SDK。
- **防刷**：同一账号发送验证码有最小间隔（`app.auth.send-code-min-interval-seconds`，默认 60）；注册发码时若账号已存在会拒绝（避免骚扰已注册用户）。

## 运行前准备

1. 创建数据库：`im_server`
2. 启动 `MySQL 5.7`
3. 启动 `Redis`
4. 启动 `MinIO`，并准备 bucket：`im-media`
5. 将本项目 JDK 切换到 `Java 21`

## 初始化数据库

执行：

```sql
source src/main/resources/sql/schema.sql;
```

若库是早期版本、未使用 `utf8mb4` 表结构，发送 **emoji** 时可能出现 `Incorrect string value ... for column 'content' (1366)`。请在 MySQL 中执行：

```sql
source src/main/resources/sql/migrate_utf8mb4.sql;
```

JDBC 的 `characterEncoding` 必须为 **`UTF-8`**（Java 没有名为 `utf8mb4` 的 Charset；写成 `utf8mb4` 会启动报错）。**emoji 能否入库**取决于 MySQL 表是否为 **`utf8mb4`**（执行上面的 `migrate_utf8mb4.sql` 即可）。

若库已存在、需补全消息幂等字段，可执行：

```sql
source src/main/resources/sql/migrate_client_msg_id.sql;
```

### 配置与环境变量（生产必读）

敏感信息**勿提交仓库**。可复制 `src/main/resources/application-local.example.yml` 为 `application-local.yml`（加入 `.gitignore`），或通过环境变量覆盖，例如：`SPRING_DATASOURCE_URL` / `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD`、`SPRING_DATA_REDIS_HOST`、`JWT_SECRET`（须足够长以满足 HMAC）、`MINIO_*`、`AGORA_APP_ID` / `AGORA_APP_CERTIFICATE`、`MAIL_USERNAME` / `MAIL_PASSWORD`。

**WebSocket 水平扩展**：默认使用内存 Simple Broker（仅单实例进程内可靠）。多实例请安装 **RabbitMQ** 并启用 **STOMP 插件**（默认监听 `61613`），设置环境变量 `WS_STOMP_RELAY_ENABLED=true`，并配置 `RABBITMQ_STOMP_HOST` 等（见 `app.websocket.relay`）。

**STOMP 入站限流**：`app.websocket.rate-limit`（依赖 Redis），对 `/app/chat.send`、`/app/chat.typing`、`/app/chat.deliver` 按用户分钟桶限流，避免绕过 HTTP `/api` 限流。

**发消息幂等**：`POST /messages/send` 与 STOMP `/app/chat.send` 可带 `clientMsgId`（≤64 字符）；同一发送者重复提交返回同一条消息。Web 客户端默认每次发送自动生成 UUID。

## 启动

```bash
mvn spring-boot:run
```

### 管理后台（独立前端）

目录 `admin-web`，默认端口 **5174**，开发时通过 Vite 代理访问后端 `/api`。

```bash
cd admin-web
npm install
npm run dev
```

浏览器打开 `http://127.0.0.1:5174`，使用 **`im_user.admin = 1`** 的管理员账号登录（与普通 IM 共用 `POST /api/auth/login`）。后台为 **Vue 3 + Vite + Element Plus**（表格、分页、表单与布局），包含：数据概览、用户列表与封禁/解封、举报列表、**全库消息搜索**（审计用）。

### 限流与消息搜索说明

- **限流**：依赖 Redis；`app.rate-limit` 可关 `enabled: false`。认证相关接口按 IP 更严，`/api/messages/search` 单独限额；超限返回 HTTP **429**。
- **用户消息搜索**：`GET /api/messages/search` 支持 `beforeMessageId`、`size`，关键词**空格分词 AND**；仍只搜索当前用户可见会话。
- **封禁**：`im_user.status = 0` 时禁止登录与刷新 Token；已签发 JWT 在约 60s 内缓存失效后拦截。

## 单聊一条消息：端到端验收检查表

用于逐项打勾，确认「发送 → 持久化 → 推送 → 对端展示」闭环。**两用户 A（发）与 B（收）**，单聊会话已建立。

### 发送端（浏览器 / im-web）

- [ ] A 已登录，WebSocket 显示已连接（或能正常收发）。
- [ ] 在会话输入框输入文本，点击发送；按钮/网络无持续失败提示。
- [ ] A 侧消息列表**立即**出现自己发送的气泡（时间、内容正确）。

### HTTP / 后端写入

- [ ] 浏览器 Network 中 `POST /api/messages`（或项目实际发消息接口）返回 **200**，业务 `code===0`。
- [ ] 服务端日志无异常；数据库 `chat_message` 表新增一行，`conversation_id` 为该单聊，`sender_id` 为 A。

### 实时推送（WebSocket）

- [ ] B 用户**在线**且已订阅个人队列（如 STOMP `/user/queue/messages`）。
- [ ] B 的浏览器/控制台收到 **`MESSAGE`**（或项目约定的）事件，载荷中含**同一条消息**（id、内容与会话一致）。

### 接收端展示

- [ ] B 的会话列表**最后一条预览**更新为该消息（或时间戳更新）。
- [ ] B 打开与 A 的会话，能看到该条消息；未读数合理减少/清零。

### 持久化与增量（刷新验证）

- [ ] B **刷新页面**后，仍能通过 `GET /api/messages/conversation/{id}` 拉到该条（证明已落库，不仅内存推送）。

### 语音消息（VOICE，若已接入）

- [ ] A 上传语音得到 `mediaUrl`（如走 `/api/upload/media`），发送 `type=VOICE` 且带 `mediaUrl`。
- [ ] B 侧以 `<audio>` 或等价组件可播放，**URL 可访问**（预签名或桶策略正确）。
- [ ] 刷新后仍能拉取并播放（同上文本持久化）。

---

## 故障排查

### 聊天里图片/视频黑屏、空白，但消息已发出

常见原因是 **MinIO 桶未开放匿名读**，浏览器用直链 `GET` 对象会 **403**，`<img>` / `<video>` 无法显示。后端在返回 `ChatMessageVO` 时会对 **本服务配置的 MinIO 直链** 自动生成 **预签名 GET URL**（见 `MinioMediaUrlService`），一般无需改桶策略。

若仍失败：检查 `app.minio.endpoint` 与上传时写入的 `media_url` 前缀是否一致（协议、主机、桶名）；控制台若出现 `[im] 媒体加载失败`，把其中的 URL 复制到浏览器地址栏查看 HTTP 状态码。

### 注册/保存用户时出现 `IllegalArgumentException: Illegal group reference`

**若你曾在本地 `pom.xml` 中加入 linkkou 等依赖：请先删除该依赖并重新编译**，与本仓库默认 `pom.xml` 保持一致。

若堆栈中出现 **`com.linkkou.mybatis.log.LogInterceptor`**（或类似「把 SQL 参数拼进完整 SQL 再打印」的 MyBatis 插件），**不是数据库或 `UserMapper.insert` 本身写错**，而是该插件用 `String.replaceFirst` / `Matcher.appendReplacement` 做占位符替换时，**把参数里的 `$数字` 当成正则反向引用**。

Spring Security 写入库的 **BCrypt 密码**形如 `$2a$10$...`，其中的 **`$2`** 会触发上述异常，导致注册/更新用户等带密码字段的 SQL 在「打印日志」阶段就失败。

**处理方式（任选其一）：**

1. **从 `pom.xml` 中移除** `linkkou` 等第三方 MyBatis SQL 美化/完整 SQL 日志依赖，或关闭其 `Interceptor` 注册。
2. 改用 MyBatis-Plus / MyBatis 自带日志，例如在 `application.yml` 中配置：

```yaml
mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl

logging:
  level:
    com.im.server.mapper: debug   # 按需调整，生产勿长期 DEBUG
```

3. 若必须使用第三方插件，需使用**已对替换串做 `Matcher.quoteReplacement` 转义**的版本，或向作者反馈该 Bug。

## 配套 Web 前端（im-web）

仓库内 `im-web/` 为 **Vue 3 + Vite** 客户端（仿微信布局、vue-i18n 中英切换）。本地联调：先启动本服务（默认端口见 `application.yml`，如 `8075`），再在 `im-web` 下执行 `npm install` 与 `npm run dev`。环境变量与功能说明见 **[im-web/README.md](im-web/README.md)**（`VITE_API_BASE`、`VITE_WS_URL` 等）。

前端已对齐常见消息能力：单条菜单含 **回复、复制、收藏、置顶/取消、反应、转发**；本人消息另有 **送达/已读详情、撤回、编辑（文本）**；**仅为自己删除**、**举报**（填原因）带确认；注册页支持 **可选验证码**（`POST /api/auth/send-code` + `REGISTER`）；登录页可进入 **忘记密码**（`/forgot-password`，`RESET_PASSWORD` 流程）。列表与接口中的长整型 ID 在前端均以 **字符串** 传递，避免雪花 ID 精度丢失。

## 文档地址

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- WebSocket 端点: `ws://localhost:8080/ws-chat`

## WebSocket 说明

- 建议使用 STOMP
- 连接时通过 Header 传 `Authorization: Bearer <token>`
- 发送地址：`/app/chat.send`
- 正在输入地址：`/app/chat.typing`
- 送达回执地址：`/app/chat.deliver`
- 订阅地址：`/user/queue/messages`
- 推送事件类型：`MESSAGE`、`READ`、`RECALL`、`PRESENCE`、`TYPING`、`DELIVERED`、`GROUP_UPDATED`
  当前还包含消息编辑后的 `EDIT` 和群聊提及用的 `MENTION`
  以及语音通话相关：`CALL_INVITE`、`CALL_ACCEPTED`、`CALL_REJECTED`、`CALL_ENDED`

## 新增接口说明

- `GET /api/users/search?keyword=` 搜索用户
- `GET /api/users/online-status?userIds=1&userIds=2` 查询用户在线状态
- `POST /api/users/blacklist/{targetUserId}` 拉黑用户
- `DELETE /api/users/blacklist/{targetUserId}` 取消拉黑
- `GET /api/users/blacklist` 黑名单列表
- `POST /api/auth/profile` 修改个人资料和头像
- `POST /api/upload/media` 支持附带 `mediaType`、`width`、`height`、`durationSeconds`、`coverUrl`
- 语音消息可通过 `type=VOICE` + `mediaUrl` 发送
- `GET /api/messages/conversation/{conversationId}` 支持 `beforeMessageId`、`afterMessageId`（增量同步）、`size`
- `POST /api/messages/forward/merge` 合并转发
- `GET /api/messages/search?keyword=&conversationId=` 搜索消息，支持全局或指定会话
- `GET /api/messages/{messageId}/reads` 查询消息已读详情
- `GET /api/messages/{messageId}/delivers` 查询消息送达详情
- `GET /api/messages/pinned?conversationId=` 查询置顶消息
- `POST /api/messages/forward` 转发消息到一个或多个目标会话
- `POST /api/messages/edit` 编辑文本消息
- `POST /api/messages/report` 举报消息
- `POST /api/messages/deliver` 上报消息送达状态
- `DELETE /api/messages/favorite/{messageId}` 取消收藏
- `POST /api/messages/favorite/update` 更新收藏备注和分类
- `GET /api/messages/favorites?keyword=&categoryName=` 收藏列表、收藏搜索、按分类筛选
- `POST /api/messages/favorite/batch` 批量收藏消息
- `POST /api/messages/pin` 置顶消息
- `POST /api/messages/unpin` 取消置顶消息
- `POST /api/messages/forward/batch` 批量转发多条消息
- `POST /api/messages/react` 添加消息反应
- `POST /api/messages/react/remove` 取消消息反应
- `POST /api/messages/delete-self` 单边删除自己的消息视图
- `POST /api/messages/read` 标记消息已读，并写入逐消息已读记录
- `POST /api/messages/recall` 撤回消息
- `GET /api/conversations/unread` 获取各会话未读数
- `GET /api/conversations/list` 返回前端可直接展示的会话列表 DTO
- `POST /api/conversations/single/{targetUserId}` 创建或复用与指定好友的单聊会话（需满足后端好友等校验）
- `POST /api/conversations/{conversationId}/settings` 更新置顶、免打扰、归档等个人会话设置
- `GET /api/conversations/archived` 归档会话列表
- `GET /api/conversations/hidden` 已删除（隐藏）会话列表
- `POST /api/conversations/{conversationId}/restore` 恢复隐藏会话
- `POST /api/conversations/{conversationId}/sync-cursor` 更新多端同步游标
- `POST /api/conversations/{conversationId}/mute-all` 全员禁言开关
- `POST /api/conversations/{conversationId}/members/mute` 成员禁言（`mutedUntil` 可空表示解除）
- `POST /api/conversations/{conversationId}/invite` 生成群邀请
- `POST /api/conversations/join-invite` 通过邀请加入群
- `POST /api/conversations/{conversationId}/draft` 保存会话草稿
- `POST /api/conversations/{conversationId}/clear` 清空当前用户该会话聊天记录
- `GET /api/conversations/{conversationId}/group-detail` 获取群详情
- `POST /api/conversations/{conversationId}/profile` 修改群名称、头像、公告
- `GET /api/conversations/{conversationId}/members` 获取群成员列表
- `DELETE /api/conversations/{conversationId}` 删除会话（仅隐藏当前用户）
- `POST /api/conversations/{conversationId}/members/add` 群主拉人
- `POST /api/conversations/{conversationId}/members/remove` 群主踢人
- `POST /api/conversations/{conversationId}/admins/add` 设置管理员
- `POST /api/conversations/{conversationId}/admins/remove` 取消管理员
- `POST /api/conversations/{conversationId}/owner/transfer` 转让群主
- `POST /api/conversations/{conversationId}/leave` 成员退群
- `DELETE /api/friends/{friendUserId}` 删除好友
- `POST /api/friends/{friendUserId}/remark` 修改好友备注
- `GET /api/friends/list?tagId=` 好友列表（可选标签过滤，返回中带 `tagIds`）
- `POST /api/friends/tags`、`GET /api/friends/tags`、`DELETE /api/friends/tags/{tagId}`、`POST /api/friends/tags/assign` 好友标签
- `POST /api/conversations/{conversationId}/remark` 修改会话备注
- `GET /api/auth/public-config` 公开配置（`verifyOnRegister`、`emailDeliveryAvailable`、`smsStubMode`、`phoneAuthEnabled`，供前端展示提示）
- `POST /api/auth/send-code` 发送验证码（`purpose`: `REGISTER` | `RESET_PASSWORD`）
- `POST /api/auth/reset-password` 重置密码
- `POST /api/auth/refresh` 刷新 `accessToken`（轮换 `refreshToken`）
- `POST /api/auth/oauth/login` 第三方占位登录
- `POST /api/auth/logout`、`POST /api/auth/logout-all`、`GET /api/auth/sessions`、`DELETE /api/auth/sessions/{sessionId}` 会话管理
- `POST /api/users/push-token` 登记推送 Token（`platform` + `deviceToken`）；离线时若配置了 `app.push.webhook-url`，服务端会向该地址 **POST JSON** 触发第三方推送（见下）
- `POST /api/calls/voice/start` 发起 1v1 语音通话（仅单聊）
- `POST /api/calls/voice/{callId}/accept` 接听语音通话
- `POST /api/calls/voice/{callId}/reject` 拒绝语音通话
- `POST /api/calls/voice/{callId}/end` 挂断 / 结束语音通话
- `GET /api/calls/voice/{callId}/agora-token` 获取 Agora RTC Token（接通后加入频道）
- `GET /api/stickers/packs` 表情包列表；管理员 `POST /api/stickers/packs`、`POST /api/stickers/items`
- `GET /api/admin/reports` 举报列表（需 `im_user.admin=1`，JWT 中带 `admin` 声明）

配置说明（`application.yml`）：

- `app.jwt.access-token-expire-seconds`：访问令牌有效期（默认 86400 秒）
- `app.jwt.refresh-token-expire-seconds`：刷新令牌会话有效期（默认 30 天，轮换策略下为单次 refresh 链周期参考）
- `app.auth.verify-on-register`：为 `true` 时注册必须传正确验证码
- `app.auth.phone-auth-enabled`：为 `true` 时开放手机号注册/登录/发码；默认 `false` 仅邮箱
- `spring.mail.*`：配置后服务端可向邮箱投递验证码（未配置时仍可能仅写日志，视 `app.auth.verify-code-log` 等而定）
- 短信：默认 `LoggingSmsVerifyCodeSender` 为桩实现；生产可自定义 Bean 实现 `SmsVerifyCodeSender` 并 `@Primary` 对接阿里云/腾讯云等
- `app.auth.verify-code-log`：是否在日志中打印验证码（生产建议关闭，仅依赖邮件/短信通道）
- **Agora 语音通话**（`app.agora`）：
  - `enabled`：是否启用语音通话
  - `app-id`：Agora App ID（建议环境变量 `AGORA_APP_ID`）
  - `app-certificate`：Agora App Certificate（建议环境变量 `AGORA_APP_CERTIFICATE`，勿入库）
  - `rtc-token-expire-seconds`：RTC Token 有效期
  - `session-ttl-seconds`：Redis 中通话状态保留时间
  - `ring-timeout-seconds`：振铃超时秒数
- **离线推送 Webhook**（`app.push`）：用户离线且存在 device token 时，若设置 `webhook-url`，后端 **POST** 到该 URL，JSON 体字段包括：`recipientUserId`、`conversationId`、`messageId`、`preview`（文本预览）、`tokens`（数组，每项为 `{ platform, deviceToken }`）。可选 `webhook-header-name` / `webhook-header-value` 用于鉴权。未配置时仍为日志桩，便于本地开发。

将用户设为管理员（执行一次 SQL）：`UPDATE im_user SET admin = 1 WHERE id = <用户ID>;`

## 上线前检查清单（可贴用）

以下为 **Go-Live / 发版评审** 用条目，按需勾选；数字与阈值请按实际环境填写。

### 配置与安全

- [ ] **生产密钥**：`app.jwt.secret` 已更换为**足够长且随机**的值，且未提交到公开仓库。
- [ ] **数据库**：`spring.datasource.password` 等为强密码；生产库与开发库**隔离**。
- [ ] **验证码**：生产环境 `app.auth.verify-code-log` 建议为 **false**（不依赖日志里的明文码）。
- [ ] **注册策略**：`app.auth.verify-on-register` 是否与产品要求一致（强制验证码时设为 `true`）。
- [ ] **短信 / 邮件**：`spring.mail.*` 或 `app.auth.sms-webhook` / 自建 `SmsVerifyCodeSender` 已在**预发/生产**验证可达。
- [ ] **MinIO**：`access-key` / `secret-key` 已轮换；桶策略与预签名行为在**浏览器直链访问**下已验证（见上文「故障排查」）。
- [ ] **HTTPS**：对外 API 与 WebSocket 使用 **WSS**；前端 `VITE_API_BASE` / `VITE_WS_URL` 指向正确域名与端口。
- [ ] **CORS / 安全头**：若经 Nginx/网关，已按需限制来源、限流、防刷。

### 数据与中间件

- [ ] **MySQL**：已执行 `schema.sql` / 必要迁移；库与表为 **utf8mb4**；核心查询有**索引**（会话、消息、用户等热点路径）。
- [ ] **备份**：库备份策略已明确（频率、保留天数、恢复演练时间点）。
- [ ] **Redis**：内存上限、持久化策略与业务容忍度一致；验证码等 key 的 TTL 行为已确认。
- [ ] **磁盘**：消息与媒体持续增长，已规划**容量告警**与归档/清理策略（如需要）。

### 应用与运行时

- [ ] **JVM**：堆大小与 GC 日志（或 APM）已配置；生产日志级别为 **INFO**（避免长期全量 DEBUG）。
- [ ] **连接池**：HikariCP 最大连接数与 MySQL `max_connections`、实例规格匹配。
- [ ] **文件句柄**：Linux `ulimit -n`、内核参数可满足预期 WebSocket 连接数（略大于目标峰值）。

### 实时与网关

- [ ] **WebSocket**：经反向代理时，**超时**与 **Upgrade** 相关配置已调优（避免空闲断连过短）。
- [ ] **多实例**：若计划水平扩展，已评估 **会话粘性** 或 **跨节点推送**（Redis Pub/Sub / MQ 等），避免消息只推到单节点。

### 可观测性与应急

- [ ] **监控**：CPU、内存、磁盘、网络、JVM、HTTP 错误率、DB 慢查询、Redis 命中率等至少覆盖核心指标。
- [ ] **告警**：磁盘将满、DB 连接打满、错误率突增、依赖（MySQL/Redis）不可达等已配置通知渠道。
- [ ] **回滚**：发版包/镜像版本可回退；数据库变更若有**不可逆**脚本，已准备降级或补丁方案。

### 容量与验收

- [ ] **目标**：已写明预期**同时在线**与**峰值 QPS/消息量**（或「几百人在线」等明确口径）。
- [ ] **压测**：在**类生产规格**下完成至少一次压测或演练，记录延迟（P95/P99）与错误率，结论**可接受**后再全量放开。

## 后续建议

- 对接真实短信/邮件发送与 OAuth 平台
- 生产环境将 `app.push.webhook-url` 指向自建服务，由该服务对接 FCM/APNs/厂商通道（Webhook 体字段见上文）
- 更完整的后台审核工作流（举报处置、封禁策略）
