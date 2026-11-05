package com.today.punch;

import com.today.common.CreateIdempotencyService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/v1/punch")
public class PunchController {

  private final PunchService punches;
  private final CreateIdempotencyService creates;

  public PunchController(PunchService punches, CreateIdempotencyService creates) {
    this.punches = punches;
    this.creates = creates;
  }

  @GetMapping("/habits")
  public PunchHabitListDto listHabits(@RequestParam(required = false) String date) {
    return punches.listHabits(date);
  }

  @PostMapping("/habits")
  public PunchHabitDto createHabit(@Valid @RequestBody PunchHabitCreateRequest body,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    return creates.create("habit", key, body, PunchHabitDto.class, () -> punches.createHabit(body));
  }

  @PutMapping("/habits/{id}")
  public PunchHabitDto updateHabit(
      @PathVariable String id, @Valid @RequestBody PunchHabitUpdateRequest body) {
    return punches.updateHabit(id, body);
  }

  @DeleteMapping("/habits/{id}")
  public void deleteHabit(@PathVariable String id) {
    punches.deleteHabit(id);
  }

  @PostMapping(
      value = "/habits/{id}/punch",
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public PunchLogDto punch(
      @PathVariable String id, @Valid @RequestBody(required = false) PunchToggleRequest body,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    PunchToggleRequest input = body == null ? new PunchToggleRequest(null, null) : body;
    return creates.create("punch_text", key, new TextPunch(id, input), PunchLogDto.class,
        () -> punches.punch(id, input));
  }

  @PostMapping(
      value = "/habits/{id}/punch",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public PunchLogDto punchWithPhoto(
      @PathVariable String id,
      @RequestParam(required = false) String date,
      @RequestParam(required = false) String note,
      @RequestPart("photo") MultipartFile photo,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    if (key == null) return punches.punchWithPhoto(id, date, note, photo);
    return creates.create("punch_photo", key,
        new PhotoPunch(id, date, note, photo.getContentType(), photo.getOriginalFilename(), sha256(photo)),
        PunchLogDto.class, () -> punches.punchWithPhoto(id, date, note, photo));
  }

  private static String sha256(MultipartFile photo) {
    try (InputStream input = photo.getInputStream()) {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] chunk = new byte[8192];
      int read;
      while ((read = input.read(chunk)) != -1) digest.update(chunk, 0, read);
      return HexFormat.of().formatHex(digest.digest());
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unreadable photo", e);
    } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
  }

  private record TextPunch(String habitId, PunchToggleRequest input) {}
  private record PhotoPunch(String habitId, String date, String note, String contentType,
      String fileName, String sha256) {}

  @DeleteMapping("/habits/{id}/punch")
  public void unpunch(@PathVariable String id, @RequestParam(required = false) String date) {
    punches.unpunch(id, date);
  }
}
