package com.today.aigateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** OpenAI 兼容 HTTP 客户端（Chat + Embeddings） */
@Component
public class OpenAiCompatibleClient {

  private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleClient.class);

  private final AiProperties properties;
  private final ObjectMapper mapper;
  private final RestClient restClient;

  public OpenAiCompatibleClient(AiProperties properties, ObjectMapper mapper) {
    this.properties = properties;
    this.mapper = mapper;
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
    factory.setReadTimeout(Duration.ofMillis(Math.max(1000, properties.getTimeoutMs())));
    String base = properties.getBaseUrl();
    if (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    this.restClient = RestClient.builder().baseUrl(base).requestFactory(factory).build();
  }

  public String chatJson(String systemPrompt, String userPrompt) {
    try {
      return doChat(systemPrompt, userPrompt, properties.isJsonResponseFormat());
    } catch (RestClientResponseException ex) {
      if (properties.isJsonResponseFormat() && ex.getStatusCode().is4xxClientError()) {
        log.warn(
            "chat with response_format failed ({}), retrying without it",
            ex.getStatusCode().value());
        return doChat(systemPrompt, userPrompt, false);
      }
      throw ex;
    }
  }

  public List<float[]> embed(List<String> texts) {
    if (texts == null || texts.isEmpty()) {
      return List.of();
    }
    ObjectNode body = mapper.createObjectNode();
    body.put("model", properties.getEmbeddingModel());
    ArrayNode input = body.putArray("input");
    for (String text : texts) {
      input.add(text == null ? "" : text);
    }

    JsonNode response =
        restClient
            .post()
            .uri("/embeddings")
            .contentType(MediaType.APPLICATION_JSON)
            .headers(h -> h.setBearerAuth(properties.getApiKey()))
            .body(body)
            .retrieve()
            .body(JsonNode.class);

    if (response == null || !response.path("data").isArray()) {
      throw new IllegalStateException("empty embedding response");
    }

    // OpenAI 可能乱序，按 index 排
    JsonNode data = response.path("data");
    float[][] vectors = new float[texts.size()][];
    for (JsonNode item : data) {
      int index = item.path("index").asInt(-1);
      JsonNode embedding = item.path("embedding");
      if (index < 0 || index >= texts.size() || !embedding.isArray()) {
        throw new IllegalStateException("invalid embedding item");
      }
      float[] vec = new float[embedding.size()];
      for (int i = 0; i < embedding.size(); i++) {
        vec[i] = (float) embedding.get(i).asDouble();
      }
      vectors[index] = vec;
    }

    List<float[]> out = new ArrayList<>(texts.size());
    for (int i = 0; i < vectors.length; i++) {
      if (vectors[i] == null) {
        throw new IllegalStateException("missing embedding for index " + i);
      }
      out.add(vectors[i]);
    }
    return out;
  }

  private String doChat(String systemPrompt, String userPrompt, boolean jsonFormat) {
    ObjectNode body = mapper.createObjectNode();
    body.put("model", properties.getModel());
    body.put("temperature", properties.getTemperature());
    ArrayNode messages = body.putArray("messages");
    messages.addObject().put("role", "system").put("content", systemPrompt);
    messages.addObject().put("role", "user").put("content", userPrompt);
    if (jsonFormat) {
      body.putObject("response_format").put("type", "json_object");
    }

    JsonNode response =
        restClient
            .post()
            .uri("/chat/completions")
            .contentType(MediaType.APPLICATION_JSON)
            .headers(h -> h.setBearerAuth(properties.getApiKey()))
            .body(body)
            .retrieve()
            .body(JsonNode.class);

    if (response == null) {
      throw new IllegalStateException("empty LLM response");
    }
    JsonNode content = response.path("choices").path(0).path("message").path("content");
    if (content.isMissingNode() || content.isNull() || content.asText().isBlank()) {
      throw new IllegalStateException("LLM response missing message.content");
    }
    return content.asText();
  }
}
