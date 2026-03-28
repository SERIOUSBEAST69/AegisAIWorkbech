package com.trustai.repository;

import com.trustai.document.ModelDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModelEsRepository extends ElasticsearchRepository<ModelDocument, String> {
}
