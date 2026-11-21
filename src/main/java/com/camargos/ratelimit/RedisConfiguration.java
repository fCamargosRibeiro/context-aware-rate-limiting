package com.camargos.ratelimit;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisConnectionException;

@Configuration
class RedisConfiguration {
  @Bean
  @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(Clock.class)
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  redis.clients.jedis.UnifiedJedis bucketClient(
      @Value("${redis.host:localhost}") String host, @Value("${redis.port:6379}") int port) {
    return new redis.clients.jedis.JedisPooled(host, port);
  }

  @Bean
  JedisPool pool(
      @Value("${redis.host:localhost}") String host, @Value("${redis.port:6379}") int port) {
    return new JedisPool(host, port);
  }
}

@RestControllerAdvice
class StorageErrors {
  @ExceptionHandler(JedisConnectionException.class)
  ResponseEntity<String> unavailable() {
    return ResponseEntity.status(503).body("Storage unavailable");
  }
}
