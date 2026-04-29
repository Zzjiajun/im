#!/bin/bash
# Elasticsearch 索引初始化脚本
# 使用方式: bash init-es.sh [es_host]
# 默认连接 http://localhost:9200

ES_HOST="${1:-http://localhost:9200}"
INDEX_NAME="im_messages_v1"

echo "============================================"
echo " IM 消息搜索 - ES 索引初始化"
echo "============================================"
echo "ES Host : ${ES_HOST}"
echo "Index   : ${INDEX_NAME}"
echo ""

# 1. 删除已有索引（若存在）
echo "[1/4] 删除已有索引（如果存在）..."
curl -s -o /dev/null -w "  HTTP %{http_code}\n" -X DELETE "${ES_HOST}/${INDEX_NAME}" || true

# 2. 创建索引 + Mapping
echo "[2/4] 创建索引并设置 Mapping..."
curl -s -X PUT "${ES_HOST}/${INDEX_NAME}" -H "Content-Type: application/json" -d '{
  "settings": {
    "number_of_shards": 3,
    "number_of_replicas": 1,
    "index": {
      "refresh_interval": "10s",
      "max_result_window": 50000,
      "analysis": {
        "analyzer": {
          "ik_smart_analyzer": {
            "type": "custom",
            "tokenizer": "ik_smart"
          },
          "ik_max_word_analyzer": {
            "type": "custom",
            "tokenizer": "ik_max_word"
          }
        }
      }
    }
  },
  "mappings": {
    "dynamic": "strict",
    "properties": {
      "id": {
        "type": "long"
      },
      "conversationId": {
        "type": "long"
      },
      "senderId": {
        "type": "long"
      },
      "senderNickname": {
        "type": "text",
        "fields": {
          "keyword": {
            "type": "keyword",
            "ignore_above": 256
          }
        },
        "analyzer": "ik_max_word",
        "search_analyzer": "ik_smart"
      },
      "type": {
        "type": "keyword"
      },
      "content": {
        "type": "text",
        "analyzer": "ik_max_word",
        "search_analyzer": "ik_smart",
        "fields": {
          "keyword": {
            "type": "keyword",
            "ignore_above": 512
          }
        }
      },
      "mediaUrl": {
        "type": "keyword",
        "index": false,
        "doc_values": false
      },
      "replyMessageId": {
        "type": "long"
      },
      "readCount": {
        "type": "integer"
      },
      "deliveredCount": {
        "type": "integer"
      },
      "favoriteCount": {
        "type": "integer"
      },
      "edited": {
        "type": "byte"
      },
      "editedAt": {
        "type": "date",
        "format": "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis"
      },
      "mentionAll": {
        "type": "byte"
      },
      "mentionUserIds": {
        "type": "keyword"
      },
      "recalled": {
        "type": "byte"
      },
      "recalledBy": {
        "type": "long"
      },
      "recalledAt": {
        "type": "date",
        "format": "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis"
      },
      "createdAt": {
        "type": "date",
        "format": "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis"
      }
    }
  }
}' | python3 -m json.tool 2>/dev/null || cat

echo ""

# 3. 确认索引已创建
echo "[3/4] 验证索引..."
curl -s "${ES_HOST}/_cat/indices/${INDEX_NAME}?v&pretty"

echo ""

# 4. 检查 IK 插件
echo "[4/4] 检查 IK 分词插件..."
curl -s "${ES_HOST}/_cat/plugins?v&pretty" 2>/dev/null | grep -q "analysis-ik" && \
  echo "  ✓ IK 插件已安装" || \
  echo "  ⚠ IK 插件未安装。请进入容器执行: docker exec im-es elasticsearch-plugin install https://github.com/medcl/elasticsearch-analysis-ik/releases/download/v7.17.27/elasticsearch-analysis-ik-7.17.27.zip"

echo ""
echo "============================================"
echo " 索引初始化完成！"
echo "============================================"
echo ""
echo "测试分词:"
echo "  curl -X POST \"${ES_HOST}/${INDEX_NAME}/_analyze\" -H \"Content-Type: application/json\" -d '{\"analyzer\":\"ik_max_word\",\"text\":\"你好世界\"}'"
echo ""
echo "搜索示例:"
echo "  curl \"${ES_HOST}/${INDEX_NAME}/_search?q=content:你好&pretty\""
echo ""
