package com.today.identity;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class InternalAccountPolicyTest {
  @Test void allowlistIsExplicitAndExact() {
    assertFalse(new InternalAccountPolicy("").isAllowedInternalEmail("user@example.test"));
    var policy = new InternalAccountPolicy(" USER@example.test,other@example.test ");
    assertTrue(policy.isAllowedInternalEmail("user@example.test"));
    assertFalse(policy.isAllowedInternalEmail("user@example.test.evil"));
  }
}
