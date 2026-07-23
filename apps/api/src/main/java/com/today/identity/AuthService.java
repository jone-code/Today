package com.today.identity;

import com.today.common.EntityMapper;
import com.today.persistence.UserEntity;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

  private final UserMapper userMapper;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;

  public AuthService(
      UserMapper userMapper, PasswordEncoder passwordEncoder, JwtService jwtService) {
    this.userMapper = userMapper;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
  }

  @Transactional
  public AuthTokenResponse register(AuthRegisterInput input) {
    String email = normalizeEmail(input.email());
    if (userMapper.findByEmail(email) != null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "email already registered");
    }
    if (input.password() == null || input.password().length() < 6) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "password too short");
    }
    if (input.displayName() == null || input.displayName().trim().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "displayName required");
    }

    Instant now = EntityMapper.now();
    UserEntity user = new UserEntity();
    user.setId(UUID.randomUUID().toString());
    user.setEmail(email);
    user.setDisplayName(input.displayName().trim());
    user.setPasswordHash(passwordEncoder.encode(input.password()));
    user.setCreatedAt(now);
    user.setUpdatedAt(now);
    userMapper.insert(user);

    return tokenResponse(user);
  }

  public AuthTokenResponse login(AuthLoginInput input) {
    String email = normalizeEmail(input.email());
    UserEntity user = userMapper.findByEmail(email);
    if (user == null || !passwordEncoder.matches(input.password(), user.getPasswordHash())) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid email or password");
    }
    return tokenResponse(user);
  }

  public UserDto me(String userId) {
    UserEntity user = userMapper.findById(userId);
    if (user == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthorized");
    }
    return toDto(user);
  }

  private AuthTokenResponse tokenResponse(UserEntity user) {
    String token = jwtService.createToken(user.getId(), user.getEmail());
    return new AuthTokenResponse(token, "Bearer", toDto(user));
  }

  private UserDto toDto(UserEntity user) {
    return new UserDto(
        user.getId(), user.getEmail(), user.getDisplayName(), user.getCreatedAt().toString());
  }

  private String normalizeEmail(String email) {
    if (email == null || email.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email required");
    }
    return email.trim().toLowerCase();
  }
}
