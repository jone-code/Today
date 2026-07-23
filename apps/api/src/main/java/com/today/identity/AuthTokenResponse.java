package com.today.identity;

public record AuthTokenResponse(String token, String tokenType, UserDto user) {}
