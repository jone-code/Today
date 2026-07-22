package com.today.identity;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** 从 JWT SecurityContext 解析当前用户；无鉴权上下文时抛错 */
@Service
public class IdentityService {

  public String getCurrentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof AuthUserPrincipal principal)) {
      throw new IllegalStateException("unauthenticated");
    }
    return principal.getUserId();
  }

  public AuthUserPrincipal requirePrincipal() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof AuthUserPrincipal principal)) {
      throw new IllegalStateException("unauthenticated");
    }
    return principal;
  }
}
