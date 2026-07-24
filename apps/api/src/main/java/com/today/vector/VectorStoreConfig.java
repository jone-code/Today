package com.today.vector;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class VectorStoreConfig {

  @Bean
  QdrantClient qdrantClient(VectorProperties properties, ObjectMapper mapper) {
    return new QdrantClient(properties, mapper);
  }

  @Bean
  QdrantVectorStore qdrantVectorStore(QdrantClient client, VectorProperties properties) {
    return new QdrantVectorStore(client, properties);
  }

  @Bean
  @Primary
  VectorStore vectorStore(
      VectorProperties properties,
      MysqlJsonVectorStore mysqlJsonVectorStore,
      QdrantVectorStore qdrantVectorStore) {
    if (properties.isQdrant()) {
      return new FallbackVectorStore(qdrantVectorStore, mysqlJsonVectorStore);
    }
    return mysqlJsonVectorStore;
  }
}
