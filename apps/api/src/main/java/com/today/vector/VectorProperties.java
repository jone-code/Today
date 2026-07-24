package com.today.vector;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "today.vector")
public class VectorProperties {

  /** mysql（默认，扫描 embedding_json）| qdrant */
  private String provider = "mysql";

  /** Qdrant HTTP API，如 http://127.0.0.1:6333 */
  private String qdrantUrl = "http://127.0.0.1:6333";

  private String collection = "today_memories";

  /** text-embedding-3-small = 1536；换模型时需重建 collection */
  private int dimensions = 1536;

  private String apiKey = "";

  private int timeoutMs = 5000;

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public String getQdrantUrl() {
    return qdrantUrl;
  }

  public void setQdrantUrl(String qdrantUrl) {
    this.qdrantUrl = qdrantUrl;
  }

  public String getCollection() {
    return collection;
  }

  public void setCollection(String collection) {
    this.collection = collection;
  }

  public int getDimensions() {
    return dimensions;
  }

  public void setDimensions(int dimensions) {
    this.dimensions = dimensions;
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  public int getTimeoutMs() {
    return timeoutMs;
  }

  public void setTimeoutMs(int timeoutMs) {
    this.timeoutMs = timeoutMs;
  }

  public boolean isQdrant() {
    return "qdrant".equalsIgnoreCase(provider);
  }
}
