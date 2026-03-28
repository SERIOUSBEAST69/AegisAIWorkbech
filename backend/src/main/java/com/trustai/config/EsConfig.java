package com.trustai.config;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

@Configuration
public class EsConfig {

    @Value("${spring.elasticsearch.uris:http://localhost:9200}")
    private String elasticsearchUri;

    @Bean
    public RestHighLevelClient esClient() {
        try {
            URI uri = URI.create(elasticsearchUri.split(",")[0].trim());
            int port = uri.getPort() > 0 ? uri.getPort() : 9200;
            String scheme = uri.getScheme() != null ? uri.getScheme() : "http";
            String host = uri.getHost() != null ? uri.getHost() : "localhost";
            return new RestHighLevelClient(
                RestClient.builder(new HttpHost(host, port, scheme))
            );
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(EsConfig.class)
                .warn("[EsConfig] 解析 spring.elasticsearch.uris='{}' 失败，回退到 localhost:9200。原因: {}",
                    elasticsearchUri, e.getMessage());
            return new RestHighLevelClient(
                RestClient.builder(new HttpHost("localhost", 9200, "http"))
            );
        }
    }
}
