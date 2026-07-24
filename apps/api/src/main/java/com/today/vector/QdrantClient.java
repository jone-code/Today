package com.today.vector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Qdrant REST 客户端（无官方 SDK 依赖） */
public class QdrantClient {

  private static final Logger log = LoggerFactory.getLogger(QdrantClient.class);

  private final VectorProperties properties;
  private final ObjectMapper mapper;
  private final RestClient restClient;

  public QdrantClient(VectorProperties properties, ObjectMapper mapper) {
    this.properties = properties;
    this.mapper = mapper;
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
    factory.setReadTimeout(Duration.ofMillis(Math.max(1000, properties.getTimeoutMs())));
    String base = properties.getQdrantUrl();
    if (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    this.restClient = RestClient.builder().baseUrl(base).requestFactory(factory).build();
  }

  public static UUID pointId(String memoryId) {
    return UUID.nameUUIDFromBytes(("today-memory:" + memoryId).getBytes(StandardCharsets.UTF_8));
  }

  public boolean collectionExists() {
    try {
      restClient
          .get()
          .uri("/collections/{name}", properties.getCollection())
          .headers(this::auth)
          .retrieve()
          .toBodilessEntity();
      return true;
    } catch (RestClientResponseException ex) {
      if (ex.getStatusCode().value() == 404) {
        return false;
      }
      throw ex;
    }
  }

  public void ensureCollection() {
    if (collectionExists()) {
      return;
    }
    ObjectNode body = mapper.createObjectNode();
    ObjectNode vectors = body.putObject("vectors");
    vectors.put("size", properties.getDimensions());
    vectors.put("distance", "Cosine");
    restClient
        .put()
        .uri("/collections/{name}", properties.getCollection())
        .contentType(MediaType.APPLICATION_JSON)
        .headers(this::auth)
        .body(body)
        .retrieve()
        .toBodilessEntity();
    log.info(
        "qdrant collection created name={} dim={}",
        properties.getCollection(),
        properties.getDimensions());
  }

  public void upsertPoints(List<MemoryVectorRecord> records) {
    if (records == null || records.isEmpty()) {
      return;
    }
    ensureCollection();
    ObjectNode body = mapper.createObjectNode();
    ArrayNode points = body.putArray("points");
    for (MemoryVectorRecord r : records) {
      if (r.vector() == null || r.vector().length == 0) {
        continue;
      }
      ObjectNode point = points.addObject();
      point.put("id", pointId(r.memoryId()).toString());
      ArrayNode vector = point.putArray("vector");
      for (float v : r.vector()) {
        vector.add(v);
      }
      ObjectNode payload = point.putObject("payload");
      payload.put("memoryId", r.memoryId());
      payload.put("userId", r.userId());
      payload.put("category", r.category() == null ? "" : r.category());
      payload.put("text", r.text() == null ? "" : r.text());
      payload.put("strength", r.strength());
      payload.put("archived", r.archived());
    }
    if (points.isEmpty()) {
      return;
    }
    restClient
        .put()
        .uri("/collections/{name}/points?wait=true", properties.getCollection())
        .contentType(MediaType.APPLICATION_JSON)
        .headers(this::auth)
        .body(body)
        .retrieve()
        .toBodilessEntity();
  }

  public void deletePoints(List<String> memoryIds) {
    if (memoryIds == null || memoryIds.isEmpty() || !collectionExists()) {
      return;
    }
    ObjectNode body = mapper.createObjectNode();
    ArrayNode pointIds = body.putArray("points");
    for (String memoryId : memoryIds) {
      pointIds.add(pointId(memoryId).toString());
    }
    restClient
        .post()
        .uri("/collections/{name}/points/delete?wait=true", properties.getCollection())
        .contentType(MediaType.APPLICATION_JSON)
        .headers(this::auth)
        .body(body)
        .retrieve()
        .toBodilessEntity();
  }

  public void setArchived(String memoryId, String userId, boolean archived) {
    if (!collectionExists()) {
      return;
    }
    ObjectNode body = mapper.createObjectNode();
    ObjectNode payload = body.putObject("payload");
    payload.put("archived", archived);
    if (userId != null) {
      payload.put("userId", userId);
    }
    ArrayNode points = body.putArray("points");
    points.add(pointId(memoryId).toString());
    restClient
        .post()
        .uri("/collections/{name}/points/payload?wait=true", properties.getCollection())
        .contentType(MediaType.APPLICATION_JSON)
        .headers(this::auth)
        .body(body)
        .retrieve()
        .toBodilessEntity();
  }

  public List<ScoredMemoryId> search(String userId, float[] query, int topK) {
    if (!collectionExists()) {
      return List.of();
    }
    ObjectNode body = mapper.createObjectNode();
    ArrayNode vector = body.putArray("vector");
    for (float v : query) {
      vector.add(v);
    }
    body.put("limit", Math.max(1, topK));
    body.put("with_payload", true);

    ObjectNode filter = body.putObject("filter");
    ArrayNode must = filter.putArray("must");

    ObjectNode userCond = must.addObject();
    userCond.put("key", "userId");
    userCond.putObject("match").put("value", userId);

    ObjectNode archivedCond = must.addObject();
    archivedCond.put("key", "archived");
    archivedCond.putObject("match").put("value", false);

    JsonNode response =
        restClient
            .post()
            .uri("/collections/{name}/points/search", properties.getCollection())
            .contentType(MediaType.APPLICATION_JSON)
            .headers(this::auth)
            .body(body)
            .retrieve()
            .body(JsonNode.class);

    List<ScoredMemoryId> out = new ArrayList<>();
    if (response == null || !response.path("result").isArray()) {
      return out;
    }
    for (JsonNode hit : response.path("result")) {
      String memoryId = hit.path("payload").path("memoryId").asText(null);
      if (memoryId == null || memoryId.isBlank()) {
        continue;
      }
      out.add(new ScoredMemoryId(memoryId, hit.path("score").asDouble(0)));
    }
    return out;
  }

  private void auth(org.springframework.http.HttpHeaders headers) {
    String key = properties.getApiKey();
    if (key != null && !key.isBlank()) {
      headers.set("api-key", key);
    }
  }
}
