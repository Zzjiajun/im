package com.im.server.repository;

import com.im.server.model.es.MessageDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

/**
 * Elasticsearch 消息搜索仓库。
 * 提供基本的 CRUD 和搜索能力。
 */
@Repository
public interface MessageSearchRepository extends ElasticsearchRepository<MessageDocument, Long> {

}
