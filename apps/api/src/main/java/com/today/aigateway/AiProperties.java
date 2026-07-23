package com.today.aigateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "today.ai")
public class AiProperties {

  /** OpenAI 兼容 API Key；为空则全程 Heuristic */
  private String apiKey = "";

  /** 如 https://api.openai.com/v1 或国内兼容网关 */
  private String baseUrl = "https://api.openai.com/v1";

  private String model = "gpt-4o-mini";

  private String embeddingModel = "text-embedding-3-small";

  private int timeoutMs = 30000;

  /** 部分兼容网关不支持 response_format，可关 */
  private boolean jsonResponseFormat = true;

  private double temperature = 0.3;

  /** proactive 检索返回的记忆条数 */
  private int retrieveTopK = 5;

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public String getEmbeddingModel() {
    return embeddingModel;
  }

  public void setEmbeddingModel(String embeddingModel) {
    this.embeddingModel = embeddingModel;
  }

  public int getTimeoutMs() {
    return timeoutMs;
  }

  public void setTimeoutMs(int timeoutMs) {
    this.timeoutMs = timeoutMs;
  }

  public boolean isJsonResponseFormat() {
    return jsonResponseFormat;
  }

  public void setJsonResponseFormat(boolean jsonResponseFormat) {
    this.jsonResponseFormat = jsonResponseFormat;
  }

  public double getTemperature() {
    return temperature;
  }

  public void setTemperature(double temperature) {
    this.temperature = temperature;
  }

  public int getRetrieveTopK() {
    return retrieveTopK;
  }

  public void setRetrieveTopK(int retrieveTopK) {
    this.retrieveTopK = retrieveTopK;
  }

  public boolean isConfigured() {
    return apiKey != null && !apiKey.isBlank();
  }
}
