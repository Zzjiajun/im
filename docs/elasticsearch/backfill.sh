#!/bin/bash
# MySQL 历史消息批量回填到 Elasticsearch
# 使用方式: bash backfill.sh [options]
#
# 选项:
#   --es-host       ES 地址 (默认: http://localhost:9200)
#   -m, --mysql      MySQL 连接字符串 (默认: "jdbc:mysql://127.0.0.1:3306/im_server?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8mb4")
#   -u, --user       MySQL 用户名 (默认: root)
#   -p, --password   MySQL 密码 (默认: 123456)
#   --batch-size     每批处理行数 (默认: 500)
#   --from-id        起始消息 ID (默认: 0，从头开始)
#   --to-id          截止消息 ID (默认: 999999999)
#   --index          ES 索引名 (默认: im_messages_v1)
#   --help           显示帮助
#
# 前置依赖: curl, mysql (mysql client), jq (可选，用于格式化)

ES_HOST="${ES_HOST:-http://localhost:9200}"
MYSQL_URL="${MYSQL_URL:-"jdbc:mysql://127.0.0.1:3306/im_server?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8mb4"}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASS="${MYSQL_PASS:-123456}"
BATCH_SIZE=500
FROM_ID=0
TO_ID=999999999
INDEX="im_messages_v1"

# 解析命令行参数
while [[ $# -gt 0 ]]; do
  case "$1" in
    --es-host) ES_HOST="$2"; shift 2 ;;
    -m|--mysql) MYSQL_URL="$2"; shift 2 ;;
    -u|--user) MYSQL_USER="$2"; shift 2 ;;
    -p|--password) MYSQL_PASS="$2"; shift 2 ;;
    --batch-size) BATCH_SIZE="$2"; shift 2 ;;
    --from-id) FROM_ID="$2"; shift 2 ;;
    --to-id) TO_ID="$2"; shift 2 ;;
    --index) INDEX="$2"; shift 2 ;;
    --help)
      echo "用法: bash backfill.sh [options]"
      echo "参见脚本内注释获取完整选项列表"
      exit 0 ;;
    *) echo "未知选项: $1"; exit 1 ;;
  esac
done

# 检查必要工具
for cmd in mysql curl; do
  if ! command -v $cmd &>/dev/null; then
    echo "错误: 未找到 $cmd，请先安装。"
    exit 1
  fi
done

echo "============================================"
echo " 历史消息 ES 回填"
echo "============================================"
echo "ES:       ${ES_HOST}/${INDEX}"
echo "MySQL:    ${MYSQL_URL}"
echo "Batch:    ${BATCH_SIZE}"
echo "Range:    id [${FROM_ID}, ${TO_ID}]"
echo ""

# 检查 ES 索引是否存在
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "${ES_HOST}/${INDEX}")
if [ "$HTTP_CODE" = "404" ]; then
  echo "错误: 索引 ${INDEX} 不存在，请先运行 init-es.sh"
  exit 1
fi

# 从 MySQL 读取 MySQL 连接参数（从 JDBC URL 中解析）
MYSQL_HOST="127.0.0.1"
MYSQL_PORT=3306
MYSQL_DB="im_server"
# 简单解析
if [[ "$MYSQL_URL" =~ jdbc:mysql://([^:]+):([0-9]+)/([^?]+) ]]; then
  MYSQL_HOST="${BASH_REMATCH[1]}"
  MYSQL_PORT="${BASH_REMATCH[2]}"
  MYSQL_DB="${BASH_REMATCH[3]}"
fi

TOTAL=0
BATCH=0
CURRENT_MAX=$FROM_ID

echo "开始回填..."
echo "-------------------"

while [ $CURRENT_MAX -lt $TO_ID ]; do
  # 查询一批消息
  SQL="
    SELECT
      m.id,
      m.conversation_id,
      m.sender_id,
      COALESCE(u.nickname, '') AS sender_nickname,
      m.type,
      m.content,
      m.media_url,
      m.reply_message_id,
      m.read_count,
      m.delivered_count,
      m.favorite_count,
      m.edited,
      m.edited_at,
      m.mention_all,
      m.mention_user_ids,
      m.recalled,
      m.recalled_by,
      m.recalled_at,
      m.created_at
    FROM chat_message m
    LEFT JOIN user u ON u.id = m.sender_id
    WHERE m.id > ${CURRENT_MAX}
    ORDER BY m.id ASC
    LIMIT ${BATCH_SIZE}
  "

  # 输出到临时文件
  TMPFILE=$(mktemp)
  mysql -h "${MYSQL_HOST}" -P "${MYSQL_PORT}" -u "${MYSQL_USER}" -p"${MYSQL_PASS}" "${MYSQL_DB}" -N -B -e "${SQL}" > "$TMPFILE"

  # 检查是否有数据
  ROWS=$(wc -l < "$TMPFILE")
  if [ "$ROWS" -eq 0 ]; then
    rm -f "$TMPFILE"
    echo "没有更多数据，回填完成。"
    break
  fi

  # 构建 bulk API payload
  BULKFILE=$(mktemp)
  LAST_ID=0
  while IFS=$'\t' read -r \
    id conversation_id sender_id sender_nickname type content \
    media_url reply_message_id read_count delivered_count favorite_count \
    edited edited_at mention_all mention_user_ids recalled recalled_by recalled_at \
    created_at; do

    LAST_ID=$id

    # 转义特殊字符（简单处理）
    # 使用 jq 构建 JSON（如果有 jq）或者手动构建
    if command -v jq &>/dev/null; then
      DOC=$(jq -n \
        --arg id "$id" \
        --arg conversationId "$conversation_id" \
        --arg senderId "$sender_id" \
        --arg senderNickname "$sender_nickname" \
        --arg type "$type" \
        --arg content "$content" \
        --arg mediaUrl "$media_url" \
        --arg replyMessageId "$reply_message_id" \
        --arg readCount "$read_count" \
        --arg deliveredCount "$delivered_count" \
        --arg favoriteCount "$favorite_count" \
        --arg edited "$edited" \
        --arg editedAt "$edited_at" \
        --arg mentionAll "$mention_all" \
        --arg mentionUserIds "$mention_user_ids" \
        --arg recalled "$recalled" \
        --arg recalledBy "$recalled_by" \
        --arg recalledAt "$recalled_at" \
        --arg createdAt "$created_at" \
        '{
          id: ($id | tonumber),
          conversationId: ($conversationId | tonumber),
          senderId: ($senderId | tonumber),
          senderNickname: $senderNickname,
          type: $type,
          content: $content,
          mediaUrl: $mediaUrl,
          replyMessageId: ($replyMessageId // "" | if . == "" then null else tonumber end),
          readCount: ($readCount // "0" | tonumber),
          deliveredCount: ($deliveredCount // "0" | tonumber),
          favoriteCount: ($favoriteCount // "0" | tonumber),
          edited: ($edited // "0" | tonumber),
          editedAt: (if $editedAt == "" or $editedAt == "\\N" then null else $editedAt end),
          mentionAll: ($mentionAll // "0" | tonumber),
          mentionUserIds: ($mentionUserIds // "" | if . == "" or . == "\\N" then [] else (. | split(",")) end),
          recalled: ($recalled // "0" | tonumber),
          recalledBy: ($recalledBy // "" | if . == "" or . == "\\N" then null else tonumber end),
          recalledAt: (if $recalledAt == "" or $recalledAt == "\\N" then null else $recalledAt end),
          createdAt: (if $createdAt == "" or $createdAt == "\\N" then null else $createdAt end)
        }')
    else
      # 简易 fallback：仅索引必要字段
      DOC="{\"id\":$id,\"conversationId\":$conversation_id,\"senderId\":$sender_id,\"type\":\"$type\",\"content\":$(echo "$content" | python3 -c 'import sys,json; print(json.dumps(sys.stdin.read()))' 2>/dev/null || echo "\"\""),\"recalled\":$recalled,\"createdAt\":\"$created_at\"}"
    fi

    echo "{\"index\":{\"_index\":\"${INDEX}\",\"_id\":$id}}" >> "$BULKFILE"
    echo "$DOC" >> "$BULKFILE"
  done < "$TMPFILE"

  # 发送 bulk 请求
  RESP=$(curl -s -X POST "${ES_HOST}/_bulk" \
    -H "Content-Type: application/x-ndjson" \
    --data-binary @"$BULKFILE")

  # 检查结果
  ERRORS=$(echo "$RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('errors',True))" 2>/dev/null)
  if [ "$ERRORS" = "False" ]; then
    BATCH=$((BATCH + 1))
    TOTAL=$((TOTAL + ROWS))
    CURRENT_MAX=$LAST_ID
    echo "  [批次 ${BATCH}] 已索引 ${ROWS} 条 (至 ID ${LAST_ID})，累计 ${TOTAL} 条"
  else
    echo "  [错误] 批次索引失败，响应:"
    echo "$RESP" | python3 -m json.tool 2>/dev/null | head -20
    rm -f "$TMPFILE" "$BULKFILE"
    exit 1
  fi

  rm -f "$TMPFILE" "$BULKFILE"

  # 如果返回行数不足 batch size，说明已到末尾
  if [ "$ROWS" -lt "$BATCH_SIZE" ]; then
    echo "所有数据回填完毕。"
    break
  fi
done

echo "-------------------"
echo "回填完成！共索引 ${TOTAL} 条消息。"
echo ""

# 刷新索引
echo "刷新索引..."
curl -s -o /dev/null -X POST "${ES_HOST}/${INDEX}/_refresh"
echo ""

# 验证
echo "文档数量:"
curl -s "${ES_HOST}/${INDEX}/_count?pretty" | python3 -c "import sys,json; print('  ', json.load(sys.stdin).get('count', 'N/A'))"
echo ""

echo "============================================"
echo " 回填完成！"
echo " 可在 Kibana (http://localhost:5601) 中查看"
echo "============================================"
