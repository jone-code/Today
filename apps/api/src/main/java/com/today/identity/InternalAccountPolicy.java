package com.today.identity;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InternalAccountPolicy {
  private final String allowlist;
  public InternalAccountPolicy(@Value("${today.trial.internal-email-allowlist:}") String allowlist) { this.allowlist = allowlist; }
  public boolean isAllowedInternalEmail(String email) {
    if (allowlist == null || allowlist.isBlank() || email == null) return false;
    for (String allowed : allowlist.split(",")) if (email.trim().toLowerCase(Locale.ROOT).equals(allowed.trim().toLowerCase(Locale.ROOT))) return true;
    return false;
  }
}
