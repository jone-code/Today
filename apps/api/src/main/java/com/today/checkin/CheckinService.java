package com.today.checkin;

import com.today.identity.IdentityService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class CheckinService {

  private final Map<String, CheckinDto> store = new ConcurrentHashMap<>();
  private final IdentityService identity;

  public CheckinService(IdentityService identity) {
    this.identity = identity;
  }

  public String todayDate() {
    return LocalDate.now(ZoneOffset.UTC).toString();
  }

  public CheckinDto getToday() {
    return store.get(key(identity.getCurrentUserId(), todayDate()));
  }

  public CheckinDto upsert(CheckinCreateInput input) {
    String userId = identity.getCurrentUserId();
    String date = input.date() != null ? input.date() : todayDate();
    String now = Instant.now().toString();
    String storeKey = key(userId, date);
    CheckinDto existing = store.get(storeKey);

    CheckinDto dto =
        new CheckinDto(
            existing != null ? existing.id() : UUID.randomUUID().toString(),
            userId,
            date,
            input.rawText().trim(),
            existing != null ? existing.createdAt() : now,
            now);

    store.put(storeKey, dto);
    return dto;
  }

  public List<CheckinDto> listRecent(int limit) {
    String userId = identity.getCurrentUserId();
    return store.values().stream()
        .filter(c -> c.userId().equals(userId))
        .sorted(Comparator.comparing(CheckinDto::date).reversed())
        .limit(limit)
        .toList();
  }

  private String key(String userId, String date) {
    return userId + ":" + date;
  }
}
