package com.camargos.ratelimit.lookup.api;

import static org.springframework.http.HttpStatus.*;

import org.springframework.web.server.ResponseStatusException;

public final class SyntheticRequest {
  private SyntheticRequest() {}

  public static String identity(String auth) {
    return switch (String.valueOf(auth)) {
      case "Bearer demo-a" -> "alpha";
      case "Bearer demo-b" -> "beta";
      default -> throw new ResponseStatusException(UNAUTHORIZED);
    };
  }

  public static String required(String value) {
    if (value == null || value.isBlank() || value.length() > 128)
      throw new ResponseStatusException(BAD_REQUEST);
    return value;
  }
}
