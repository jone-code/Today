package com.today.reminder;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/reminders")
public class ReminderController {

  private final ReminderService reminders;

  public ReminderController(ReminderService reminders) {
    this.reminders = reminders;
  }

  @GetMapping
  public ReminderListDto list() {
    return reminders.listMine();
  }

  @PostMapping
  public ReminderDto create(@Valid @RequestBody ReminderCreateRequest body) {
    return reminders.create(body);
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
