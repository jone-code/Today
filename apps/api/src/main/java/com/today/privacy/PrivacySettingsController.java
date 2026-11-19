package com.today.privacy;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PrivacySettingsController {
  private final PrivacySettingsService service;
  public PrivacySettingsController(PrivacySettingsService service) { this.service = service; }
  @GetMapping("/v1/profile/privacy") public PrivacySettingsDto get() { return service.get(); }
  @PatchMapping("/v1/profile/privacy") public PrivacySettingsDto update(@Valid @RequestBody PrivacySettingsRequest input) { return service.update(input); }
}
