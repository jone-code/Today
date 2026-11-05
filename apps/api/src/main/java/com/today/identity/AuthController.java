package com.today.identity;

import com.today.common.RateLimitService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
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
  private final RateLimitService rateLimits;
  @Value("${today.rate-limit.register-per-hour:60}") private int registerPerHour = 60;
  @Value("${today.rate-limit.login-ip-per-five-minutes:100}") private int loginIpPerFiveMinutes = 100;
  @Value("${today.rate-limit.login-email-per-five-minutes:10}") private int loginEmailPerFiveMinutes = 10;

  public AuthController(AuthService authService, IdentityService identityService, RateLimitService rateLimits) {
    this.authService = authService;
    this.identityService = identityService;
    this.rateLimits = rateLimits;
  }

  @PostMapping("/register")
  public AuthTokenResponse register(@Valid @RequestBody AuthRegisterRequest body, HttpServletRequest request) {
    rateLimits.check("register_ip", request.getRemoteAddr(), registerPerHour, Duration.ofHours(1));
    return authService.register(
        new AuthRegisterInput(body.email(), body.password(), body.displayName()));
  }

  @PostMapping("/login")
  public AuthTokenResponse login(@Valid @RequestBody AuthLoginRequest body, HttpServletRequest request) {
    rateLimits.check("login_ip", request.getRemoteAddr(), loginIpPerFiveMinutes, Duration.ofMinutes(5));
    rateLimits.check("login_email", body.email().trim().toLowerCase(Locale.ROOT),
        loginEmailPerFiveMinutes, Duration.ofMinutes(5));
    return authService.login(new AuthLoginInput(body.email(), body.password()));
  }

  @GetMapping("/me")
  public UserDto me() {
    return authService.me(identityService.getCurrentUserId());
  }
}
