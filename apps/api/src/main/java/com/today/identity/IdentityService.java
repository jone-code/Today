package com.today.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** MVP：无登录，统一开发用户，表结构仍带 userId */
@Service
public class IdentityService {

  private final String devUserId;

  public IdentityService(@Value("${today.dev-user-id:dev-user}") String devUserId) {
    this.devUserId = devUserId;
  }

  public String getCurrentUserId() {
    return devUserId;
  }
}
