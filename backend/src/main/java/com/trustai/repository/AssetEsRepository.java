package com.trustai.repository;

import com.trustai.document.AssetDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AssetEsRepository extends ElasticsearchRepository<AssetDocument, String> {
}
