# 语音通话接口与前端对接说明

## 概述

当前项目的语音通话为：

- 场景：`1v1` 单聊语音
- 信令：后端 REST + WebSocket 事件
- 媒体：前端通过 `agora-rtc-sdk-ng` 直连 Agora RTC
- 通话状态：后端 `Redis` 保存当前通话会话
- 前端状态：`Pinia` 中的 `voiceCall` store 统一管理

核心限制：

- 仅支持单聊会话，不支持群语音
- 手机或非 `localhost` 网页要采集麦克风时，页面必须运行在 `HTTPS` 安全上下文
- 局域网 `http://192.168.x.x:端口` 只适合看页面，不适合测试语音采集

---

## 后端配置

配置位置：`src/main/resources/application.yml`

```yml
app:
  agora:
    enabled: ${AGORA_ENABLED:true}
    app-id: ${AGORA_APP_ID:${APP_AGORA_APP_ID:${AGORA_APPID:}}}
    app-certificate: ${AGORA_APP_CERTIFICATE:${APP_AGORA_APP_CERTIFICATE:${AGORA_APP_CERT:}}}
    rtc-token-expire-seconds: 3600
    session-ttl-seconds: 7200
    ring-timeout-seconds: 45
```

支持的环境变量名：

- `AGORA_ENABLED`
- `AGORA_APP_ID`
- `APP_AGORA_APP_ID`
- `AGORA_APPID`
- `AGORA_APP_CERTIFICATE`
- `APP_AGORA_APP_CERTIFICATE`
- `AGORA_APP_CERT`

启动时会打印配置状态日志：

```text
Agora config loaded: enabled=true, appIdConfigured=true, appCertificateConfigured=true
```

说明：

- 日志不会输出密钥明文
- 如果 `enabled=false`，接口会报 `语音通话未启用，请先配置 Agora`
- 如果 `appId/appCertificate` 为空，接口会报 `Agora 配置不完整：缺少 ...`

---

## 后端接口

控制器：`src/main/java/com/im/server/controller/VoiceCallController.java`

统一前缀：

```text
/api/calls/voice
```

### 1. 查询当前活跃通话

```http
GET /api/calls/voice/current
```

用途：

- 前端刷新页面后恢复通话状态
- 全局弹层初始化时拉取当前通话

返回：

- `VoiceCallVO`
- 无通话时 `data = null`

### 2. 发起通话

```http
POST /api/calls/voice/start
Content-Type: application/json
```

请求体：

```json
{
  "conversationId": 123456789012345678
}
```

用途：

- 主叫发起单聊语音

后端校验：

- Agora 已启用
- 当前会话存在且属于单聊
- 主叫在会话中
- 被叫在线
- 双方都不在其它活跃通话中

### 3. 接听通话

```http
POST /api/calls/voice/{callId}/accept
```

用途：

- 被叫接听来电

### 4. 拒绝通话

```http
POST /api/calls/voice/{callId}/reject
```

用途：

- 被叫拒接

### 5. 挂断通话

```http
POST /api/calls/voice/{callId}/end
```

用途：

- 主叫取消
- 任一方在接通后挂断

### 6. 获取 Agora RTC Token

```http
GET /api/calls/voice/{callId}/agora-token
```

用途：

- 前端在接通后调用，用于加入 Agora channel

返回：

```json
{
  "appId": "your-agora-app-id",
  "channelName": "call-xxxx",
  "uid": "123456789",
  "token": "007....",
  "expiresInSeconds": 3600
}
```

---

## 关键返回对象

### `VoiceCallVO`

定义位置：`src/main/java/com/im/server/model/vo/VoiceCallVO.java`

字段说明：

- `callId`：当前通话唯一 ID
- `conversationId`：所属单聊会话 ID
- `channelName`：Agora 频道名
- `callerUserId`
- `callerNickname`
- `callerAvatar`
- `calleeUserId`
- `calleeNickname`
- `calleeAvatar`
- `status`：`RINGING | ACCEPTED | REJECTED | ENDED`
- `reason`：结束原因，如 `REJECTED`、`CANCELLED`、`HANGUP`、`TIMEOUT`
- `createdAt`
- `answeredAt`
- `endedAt`

### `AgoraRtcTokenVO`

定义位置：`src/main/java/com/im/server/model/vo/AgoraRtcTokenVO.java`

字段说明：

- `appId`
- `channelName`
- `uid`
- `token`
- `expiresInSeconds`

---

## WebSocket 事件

后端通过用户队列推送：

```text
/user/queue/messages
```

相关事件：

- `CALL_INVITE`
- `CALL_ACCEPTED`
- `CALL_REJECTED`
- `CALL_ENDED`

事件体都是 `VoiceCallVO`。

前端处理入口：

- `im-web/src/components/GlobalRealtimeBridge.vue`
- `im-web/src/stores/voiceCall.ts`

### 事件语义

#### `CALL_INVITE`

- 被叫收到来电
- 前端切换到 `incoming` 状态

#### `CALL_ACCEPTED`

- 双方进入已接通状态
- 主叫收到后开始执行 Agora join
- 被叫在本地接听成功后也会 join Agora

#### `CALL_REJECTED`

- 被叫拒接
- 前端关闭通话 UI
- 后端会写入聊天系统消息：`语音通话：已拒绝`

#### `CALL_ENDED`

- 已取消 / 已挂断 / 已超时
- 前端关闭通话 UI
- 后端会写入聊天系统消息，例如：
  - `语音通话 03:21`
  - `语音通话：已取消`
  - `语音通话：无人接听`

---

## 前端接入结构

### 1. API 封装

文件：`im-web/src/api/call.ts`

已封装方法：

- `fetchCurrentVoiceCall()`
- `startVoiceCall()`
- `acceptVoiceCall()`
- `rejectVoiceCall()`
- `endVoiceCall()`
- `fetchAgoraVoiceToken()`

### 2. 全局状态

文件：`im-web/src/stores/voiceCall.ts`

主要职责：

- 保存当前通话 `currentCall`
- 管理前端阶段 `phase`
- 管理本地静音状态 `muted`
- 管理远端是否已入会 `remoteJoined`
- 在接通时加入 Agora
- 接收并处理 `CALL_*` WebSocket 事件
- 页面刷新后通过 `/current` 恢复通话状态

关键状态：

- `idle`
- `incoming`
- `outgoing`
- `connecting`
- `connected`

### 3. 全局 UI

文件：`im-web/src/components/GlobalVoiceCallOverlay.vue`

职责：

- 来电时显示全屏弹层
- 已接通后显示悬浮通话框
- 展示头像、昵称、当前状态、通话计时
- 提供：
  - 接听
  - 拒绝
  - 挂断
  - 静音

### 4. 聊天页入口

文件：`im-web/src/views/ChatView.vue`

职责：

- 在单聊顶部/输入区提供“语音”发起按钮
- 不再自行维护整套通话状态
- 只负责发起和会话选中

---

## 前端接听/发起流程

### 主叫流程

1. 点击“语音”
2. 前端先做麦克风预检查
3. 调用 `POST /start`
4. 本地状态进入 `outgoing`
5. 被叫收到 `CALL_INVITE`
6. 被叫接听后，主叫收到 `CALL_ACCEPTED`
7. 主叫调用 `/agora-token`
8. 主叫执行 `client.join(...)` + 发布麦克风音轨

### 被叫流程

1. 收到 `CALL_INVITE`
2. UI 显示来电弹层
3. 点击“接听”
4. 前端先做麦克风预检查
5. 调用 `POST /{callId}/accept`
6. 获取 `VoiceCallVO`
7. 调用 `/agora-token`
8. 执行 `client.join(...)` + 发布麦克风音轨

---

## 安全上下文限制

这是当前手机端测试最容易踩坑的地方。

### 现象

控制台会看到 Agora 警告：

```text
The website must be running in a secure context
```

### 原因

浏览器限制麦克风采集：

- `localhost`：通常允许
- `https://...`：允许
- `http://192.168.x.x:5174`：很多浏览器会限制

所以：

- 局域网 HTTP 地址可以看页面
- 但不一定能采集麦克风

### 当前前端处理

文件：`im-web/src/stores/voiceCall.ts`

在真正接听/发起前，会先检查：

- 是否安全上下文
- 是否存在 `navigator.mediaDevices.getUserMedia`
- 是否能申请到麦克风权限

如果不满足，会直接提示：

- `语音通话需要在 HTTPS 或 localhost 环境下打开页面，局域网 http 地址会被浏览器禁止麦克风`
- `无法使用麦克风，请检查浏览器权限`

### 建议测试方式

手机真机测语音时，优先使用：

- HTTPS 域名
- HTTPS 隧道（如 cpolar / ngrok / cloudflare tunnel）
- 本地 HTTPS 证书方案

不建议直接用：

- `http://192.168.x.x:5174`

---

## Agora UID 警告说明

控制台常见警告：

```text
You input a string as the user ID
```

说明：

- 这是 Agora 的建议告警
- 不是通话失败的主因

当前前端已处理：

- 如果 `uid` 是纯数字且在 JS 安全整数范围内，会转成数字传给 Agora
- 否则仍按字符串使用

---

## 后端语音日志

文件：`src/main/java/com/im/server/service/VoiceCallService.java`

当前后端会打印如下结构化日志：

- `start`
- `accept`
- `reject`
- `end`
- `timeout`
- `token`

示例：

```text
voice-call action=start, detail=发起语音通话, callId=..., conversationId=..., actorUserId=..., callerUserId=..., calleeUserId=..., status=RINGING, reason=null
voice-call action=accept, detail=被叫接听, ...
voice-call action=end, detail=已接通后挂断通话, ..., reason=HANGUP
voice-call action=timeout, detail=振铃超时自动结束, ..., reason=TIMEOUT
```

可用于判断：

- 谁发起的
- 谁接听/拒绝/挂断的
- 是取消、挂断、超时还是拒接
- 有没有下发 Agora Token

---

## 当前已实现的附加行为

- 通话结束后会写入聊天系统消息
- 全局弹层会显示通话计时
- 刷新页面后会自动恢复当前通话状态
- 联系人页点击联系人会创建/恢复单聊并跳转聊天页

---

## 联调建议

### 最小联调顺序

1. 确认后端启动日志里 Agora 配置已加载
2. 确认前端能正常调用：
   - `/api/calls/voice/current`
   - `/api/calls/voice/start`
   - `/api/calls/voice/{callId}/accept`
   - `/api/calls/voice/{callId}/agora-token`
3. 浏览器控制台确认麦克风权限正常
4. 检查后端是否打印：
   - `voice-call action=start`
   - `voice-call action=accept`
   - `voice-call action=token`

### 如果“点击接听后弹层消失”

优先排查：

1. 前端是否因为麦克风预检查失败直接报错
2. 后端是否很快打印了：
   - `end`
   - `timeout`
   - `reject`
3. 控制台是否出现安全上下文警告

### 如果“聊天里没有通话记录”

排查：

1. 后端是否有 `CALL_ENDED` / `CALL_REJECTED`
2. 后端是否打印了对应 `voice-call action=end|reject|timeout`
3. 对应会话是否成功写入 `SYSTEM` 消息

---

## 相关文件

后端：

- `src/main/java/com/im/server/controller/VoiceCallController.java`
- `src/main/java/com/im/server/service/VoiceCallService.java`
- `src/main/java/com/im/server/model/dto/StartVoiceCallRequest.java`
- `src/main/java/com/im/server/model/vo/VoiceCallVO.java`
- `src/main/java/com/im/server/model/vo/AgoraRtcTokenVO.java`
- `src/main/resources/application.yml`

前端：

- `im-web/src/api/call.ts`
- `im-web/src/stores/voiceCall.ts`
- `im-web/src/components/GlobalVoiceCallOverlay.vue`
- `im-web/src/components/GlobalRealtimeBridge.vue`
- `im-web/src/views/ChatView.vue`
- `im-web/src/locales/zh-CN.json`
- `im-web/src/locales/en.json`

