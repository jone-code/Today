package com.today.aigateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** OpenAI 兼容 Chat Completions 客户端（唯一出网调模型的地方） */
@Component
public class OpenAiChatClient {

  private static final Logger log = LoggerFactory.getLogger(OpenAiChatClient.class);

  private final AiProperties properties;
  private final ObjectMapper mapper;
  private final RestClient restClient;

  public OpenAiChatClient(AiProperties properties, ObjectMapper mapper) {
    this.properties = properties;
    this.mapper = mapper;
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
    factory.setReadTimeout(Duration.ofMillis(Math.max(1000, properties.getTimeoutMs())));
    String base = properties.getBaseUrl();
    if (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    this.restClient =
        RestClient.builder().baseUrl(base).requestFactory(factory).build();
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
