# App 前端适配指南

> 本文档面向 **App 前端开发人员**，说明后端与 Web 管理端完成优化改造后，App 端需要配合调整的内容。
>
> 未提及的接口行为不变，App 端无需改动。

---

## 目录

1. [WebSocket / STOMP 变更](#1-websocket--stomp-变更)
2. [消息搜索（ES 全文检索）](#2-消息搜索es-全文检索)
3. [通知系统变更](#3-通知系统变更)
4. [用户在线状态变更](#4-用户在线状态变更)
5. [API 错误响应变更](#5-api-错误响应变更)
6. [新增管理后台 API（仅参考）](#6-新增管理后台-api仅参考)
7. [环境变量 / 部署配置](#7-环境变量--部署配置)
8. [FAQ](#8-faq)

---

## 1. WebSocket / STOMP 变更

### 1.1 连接地址

```
ws://{host}:8075/ws-chat
```

不变。App 端连接地址无需修改。

### 1.2 心跳（Heartbeat）调整

| 参数 | 旧值 | 新值 |
|------|------|------|
| 客户端发送心跳间隔 | 10s | **20s** |
| 服务端心跳超时阈值 | — | **90s** |
| 心跳清理周期 | — | **30s** |

**App 端需要：**

- 将 STOMP 心跳发送间隔改为 **20 秒**（如使用 Stomp.js 或其他 STOMP 库）：
  ```js
  // 示例：Stomp.js
  client.heartbeatOutgoing = 20000;
  client.heartbeatIncoming = 20000;
  ```
- 客户端 **不需要** 手动实现重连逻辑。STOMP 协议层自动处理重连，服务端在检测到断线后 90 秒内自动清理过期连接。
- **不要** 在应用层使用 `setTimeout` 手动重连，否则会导致重复订阅和资源泄漏。

### 1.3 推送队列（不变）

服务端统一推送地址不变，所有事件（消息、通知、在线状态等）都推送到此队列：

```
/user/queue/messages
```

App 端只需订阅此队列一次。

### 1.4 事件类型（不变）

服务端推送的事件格式：

```json
{
  "type": "MESSAGE | NOTIFICATION | PRESENCE | CONVERSATION_UPDATED | TYPING",
  "data": { ... }
}
```

事件类型和数据格式未变，App 端按原逻辑解析即可。

---

## 2. 消息搜索（ES 全文检索）

### 2.1 搜索接口

```
GET /api/messages/search
```

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `keyword` | string | 是 | 搜索关键词，多个词用空格分隔（AND 语义：所有词必须匹配） |
| `conversationId` | long | 否 | 限定会话搜索 |
| `beforeMessageId` | long | 否 | 游标分页：加载更早的消息 |
| `size` | int | 否 | 每页条数，默认 30，上限 100 |

**响应格式（不变）：**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [ /* ChatMessageVO 数组 */ ],
    "hasMore": true,
    "nextBeforeMessageId": 12345
  }
}
```

### 2.2 行为变化

| 场景 | 旧行为 | 新行为 |
|------|--------|--------|
| ES 已部署并启用 | — | **ES 全文搜索**：IK 中文分词，AND 匹配，搜索结果更精准 |
| ES 未部署/未启用 | MySQL `LIKE %keyword%` | 降级为 MySQL LIKE 搜索（行为不变） |

**搜索行为变化仅在 `app.elasticsearch.enabled=true` 时生效**，默认为 `false`，即默认行为不变。

### 2.3 ES 搜索特点

- **中文分词**：使用 IK 分词器（`ik_max_word` 索引 / `ik_smart` 搜索），支持中文搜索
- **AND 语义**：多个关键词用空格分隔，结果必须包含所有词（不要求顺序连续）
- **过滤已撤回消息**：搜索结果不包含已撤回消息
- **过滤用户已删除/清空**的消息（与原有行为一致）
- **自动降级**：ES 不可用时自动降级为 LIKE 搜索，App 端无感知

### 2.4 ES 部署（运维人员操作）

App 端无需关心，仅供了解：

```bash
cd docs/elasticsearch
docker-compose up -d
bash init-es.sh
```

- ES 版本：7.17.27
- IK 中文分词已内置
- 端口：9200（REST API）、5601（Kibana 管理界面）
- 索引名（可配置）：`im_messages_v1`

---

## 3. 通知系统变更

### 3.1 NotificationVO 新增字段

通知对象新增 `createdBy` 字段，表示该通知的创建者（管理员 ID，系统通知使用）：

```json
{
  "id": 1,
  "type": "SYSTEM_ANNOUNCEMENT",
  "title": "系统维护通知",
  "content": "系统将于...",
  "senderId": null,
  "senderNickname": "系统管理员",
  "senderAvatar": null,
  "relatedId": null,
  "createdBy": 1,          // ← 新增
  "isRead": false,
  "readAt": null,
  "createdAt": "2026-04-29T10:00:00"
}
```

**App 端：** 如需要展示通知来源（管理员发布），可读取 `createdBy` 字段。`null` 表示系统自动生成的通知。

### 3.2 普通用户通知接口（未变）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/notifications` | 通知列表（默认 20 条/页） |
| GET | `/api/notifications/unread` | 未读数 |
| POST | `/api/notifications/{id}/read` | 标记已读 |
| POST | `/api/notifications/read-all` | 一键已读 |
| DELETE | `/api/notifications/{id}` | 删除通知 |
| DELETE | `/api/notifications/clear` | 清空通知 |

### 3.3 新增通知类型：`SYSTEM_ANNOUNCEMENT`

管理员从管理后台发布系统公告时，通知类型为 `SYSTEM_ANNOUNCEMENT`。

App 端可单独展示此类通知（如用特殊样式）：

```typescript
enum NotificationType {
  FRIEND_REQUEST = "FRIEND_REQUEST",
  FRIEND_ACCEPTED = "FRIEND_ACCEPTED",
  GROUP_INVITE = "GROUP_INVITE",
  GROUP_MEMBER_CHANGE = "GROUP_MEMBER_CHANGE",
  MENTION = "MENTION",
  SYSTEM_ANNOUNCEMENT = "SYSTEM_ANNOUNCEMENT", // ← 新增
}
```

`SYSTEM_ANNOUNCEMENT` 通知的 `data` 字段包含：

```json
{
  "announcementTitle": "公告标题",
  "announcementContent": "公告内容"
}
```

`senderNickname` 固定为 `"系统管理员"`。

---

## 4. 用户在线状态变更

### 4.1 心跳检测机制

服务端新增 WebSocket 心跳兜底机制：

- 客户端每 **20 秒** 发送一次 STOMP 心跳帧（心跳帧由 STOMP 协议栈自动发送，无需应用层处理）
- 服务端记录每个连接的最后心跳时间
- 服务端每 **30 秒** 扫描一次，清除超过 **90 秒** 无心跳的过期连接
- 连接被判定为断线后，自动触发离线流程：用户状态设为离线，好友收到 `PRESENCE` 事件

### 4.2 Presence 事件（不变）

```json
{
  "type": "PRESENCE",
  "data": {
    "userId": 123,
    "online": false,
    "lastOnline": "2026-04-29T10:30:00"
  }
}
```

**App 端无变更。** 只是在网络闪断场景下，好友看到对方变为"离线"的延迟从"永不"变为 **最多 90 秒**。

---

## 5. API 错误响应变更

### 5.1 全局异常处理

服务端全局异常处理已加固，生产环境下**不返回内部错误细节**。

```json
// 旧行为（可能返回内部细节）：
{
  "code": 500,
  "message": "Cannot invoke ... because ... is null",
  "data": null
}

// 新行为（统一隐藏内部细节）：
{
  "code": 500,
  "message": "服务器内部错误",
  "data": null
}
```

**App 端：** 所有错误已通过 `code` 字段判断，`message` 仅用于展示。如需要区分具体错误类型，请使用 `code` 而非解析 `message` 文本。

### 5.2 常见错误码

| code | 说明 | App 处理建议 |
|------|------|-------------|
| 0 | 成功 | — |
| 400 | 请求参数错误 | 展示 message 内容 |
| 401 | 未登录/Token 过期 | 跳转登录页 |
| 403 | 无权限（非管理员操作） | 提示无权限 |
| 404 | 资源不存在 | 提示资源不存在 |
| 409 | 冲突（重复请求等） | 按需处理 |
| 429 | 频率限制 | 展示"操作过于频繁"提示 |
| 500 | 服务器内部错误 | 展示"服务器内部错误" |

---

## 6. 新增管理后台 API（仅参考）

以下 API 仅为 **Web 管理后台** 新增，App 端**不需要**对接。列出供了解系统全貌：

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/reports?page=1&size=20` | 举报列表（分页） |
| POST | `/api/admin/users/{userId}/ban` | 封禁用户 |
| POST | `/api/admin/users/{userId}/unban` | 解封用户 |
| GET | `/api/admin/dashboard` | 仪表盘数据 |
| GET | `/api/admin/users?page=1&size=20` | 用户管理列表 |
| GET | `/api/admin/messages/search` | 管理员全库搜索消息 |
| GET | `/api/admin/notifications` | 管理员通知管理 |
| POST | `/api/admin/notifications/announcement` | 发布系统公告 |
| DELETE | `/api/admin/notifications/{id}` | 管理员删除通知 |
| DELETE | `/api/admin/notifications/user/{userId}/clear` | 管理员清空用户通知 |

---

## 7. 环境变量 / 部署配置

### 7.1 ES 搜索开关

```yaml
app:
  elasticsearch:
    enabled: ${ES_ENABLED:false}    # 部署 ES 后设为 true
    uris: ${ES_URIS:http://localhost:9200}
    index: ${ES_INDEX:im_messages_v1}
```

- 默认关闭（`false`），此时搜索行为与改造前完全一致
- 部署 ES 后设为 `true`，自动启用 ES 全文搜索
- 切换过程对 App 端**完全透明**，无需修改代码

### 7.2 CORS 配置

```yaml
app:
  websocket:
    allowed-origins: ${WEBSOCKET_ALLOWED_ORIGIN:*}
```

CORS 来源可通过环境变量 `WEBSOCKET_ALLOWED_ORIGIN` 配置，生产环境建议设置具体的域名。

### 7.3 JWT 配置（不变）

```yaml
app:
  jwt:
    secret: ${JWT_SECRET:...}
    access-token-expire-seconds: 86400   # 24小时
    refresh-token-expire-seconds: 2592000  # 30天
```

**Token 过期时间不变。** App 端 Token 刷新逻辑无需修改。

---

## 8. FAQ

### Q1: App 端需要升级 STOMP 库版本吗？

不需要。标准 STOMP 协议，现有库兼容。仅需调整心跳间隔为 20000ms。

### Q2: ES 启用后，搜索返回结果有什么变化？

搜索结果更精准（中文分词 + AND 语义），不再受 `LIKE %keyword%` 的左匹配限制。例如搜索"你好世界"，ES 会将"你好"和"世界"分别分词匹配，结果更全面。

### Q3: 如果 ES 宕机了怎么办？

搜索自动降级为 MySQL LIKE 搜索，App 端无感知。日志中会记录 `[ES] 搜索异常，降级为 LIKE 搜索` 警告。

### Q4: 通知的 `createdBy` 字段有什么用途？

标识通知的创建来源。`null`=系统自动创建，非 `null`=管理员手动创建。App 端可按需展示"管理员发布"标识。

### Q5: App 端需要对 `SYSTEM_ANNOUNCEMENT` 做特殊处理吗？

建议单独展示此类通知（如使用不同图标或样式），但不是必须的。即使不做特殊处理，通知列表也能正常展示。

### Q6: 断线重连的体验有什么变化？

- 原有：网络闪断后，用户可能永远显示"在线"
- 现在：网络闪断后，**最多 90 秒** 内自动判定为离线，好友能及时看到真实状态

### Q7: 管理后台的公告发布功能，App 端需要对接吗？

不需要。公告通过通知系统推送给用户，App 端收到 `SYSTEM_ANNOUNCEMENT` 类型的通知即可。发布操作仅在管理后台进行。

---

## 变更摘要（App 端需要做的事）

| # | 事项 | 操作 |
|---|------|------|
| 1 | STOMP 心跳间隔 | 改为 **20000ms** |
| 2 | 移除手动重连逻辑 | 删除 `setTimeout` 等方式的自动重连，交给 STOMP 协议层 |
| 3 | 通知新增 `createdBy` 字段 | 类型定义中补充该字段（可选） |
| 4 | 通知新增 `SYSTEM_ANNOUNCEMENT` 类型 | 类型定义中补充枚举值（可选，建议做展示优化） |
| 5 | 错误处理 | 确认已按 `code` 判断而非解析 `message` 文本 |
| 6 | 消息搜索 | **无改动**，ES 部署后自动生效，对 App 透明 |

---

> 如有疑问，请联系后端开发人员。
