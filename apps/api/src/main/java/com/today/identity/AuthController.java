package com.today.identity;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
public class AuthController {

  private final AuthService authService;
  private final IdentityService identityService;

  public AuthController(AuthService authService, IdentityService identityService) {
    this.authService = authService;
    this.identityService = identityService;
  }

  @PostMapping("/register")
  public AuthTokenResponse register(@Valid @RequestBody AuthRegisterRequest body) {
    return authService.register(
        new AuthRegisterInput(body.email(), body.password(), body.displayName()));
  }

  @PostMapping("/login")
  public AuthTokenResponse login(@Valid @RequestBody AuthLoginRequest body) {
    return authService.login(new AuthLoginInput(body.email(), body.password()));
  }

  @GetMapping("/me")
  public UserDto me() {
    return authService.me(identityService.getCurrentUserId());
  }
}
