package com.trustai.repository;

import com.trustai.document.AuditLogDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogEsRepository extends ElasticsearchRepository<AuditLogDocument, String> {
}
