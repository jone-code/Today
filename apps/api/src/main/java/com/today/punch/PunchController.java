package com.today.punch;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/punch")
public class PunchController {

  private final PunchService punches;

  public PunchController(PunchService punches) {
    this.punches = punches;
  }

  @GetMapping("/habits")
  public PunchHabitListDto listHabits(@RequestParam(required = false) String date) {
    return punches.listHabits(date);
  }

  @PostMapping("/habits")
  public PunchHabitDto createHabit(@Valid @RequestBody PunchHabitCreateRequest body) {
    return punches.createHabit(body);
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
      @PathVariable String id, @Valid @RequestBody(required = false) PunchToggleRequest body) {
    return punches.punch(id, body == null ? new PunchToggleRequest(null, null) : body);
  }

  @PostMapping(
      value = "/habits/{id}/punch",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public PunchLogDto punchWithPhoto(
      @PathVariable String id,
      @RequestParam(required = false) String date,
      @RequestParam(required = false) String note,
      @RequestPart("photo") MultipartFile photo) {
    return punches.punchWithPhoto(id, date, note, photo);
  }

  @DeleteMapping("/habits/{id}/punch")
  public void unpunch(@PathVariable String id, @RequestParam(required = false) String date) {
    punches.unpunch(id, date);
  }
}
