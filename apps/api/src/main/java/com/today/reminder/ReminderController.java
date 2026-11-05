package com.today.reminder;

import com.today.common.CreateIdempotencyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/reminders")
public class ReminderController {

  private final ReminderService reminders;
  private final CreateIdempotencyService creates;

  public ReminderController(ReminderService reminders, CreateIdempotencyService creates) {
    this.reminders = reminders;
    this.creates = creates;
  }

  @GetMapping
  public ReminderListDto list() {
    return reminders.listMine();
  }

  @PostMapping
  public ReminderDto create(@Valid @RequestBody ReminderCreateRequest body,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    return creates.create("reminder", key, body, ReminderDto.class, () -> reminders.create(body));
  }

  @PutMapping("/{id}")
  public ReminderDto update(
      @PathVariable String id, @Valid @RequestBody ReminderUpdateRequest body) {
    return reminders.update(id, body);
  }

  @DeleteMapping("/{id}")
  public void delete(@PathVariable String id) {
    reminders.delete(id);
  }

  @GetMapping("/deliveries")
  public ReminderDeliveryListDto deliveries(@RequestParam(required = false) Integer limit) {
    return reminders.listDeliveries(limit == null ? 30 : limit);
  }

  @PostMapping("/deliveries/{id}/read")
  public ReminderDeliveryDto markRead(@PathVariable String id) {
    return reminders.markRead(id);
  }
}
