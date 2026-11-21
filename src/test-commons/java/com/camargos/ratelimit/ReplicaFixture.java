package com.camargos.ratelimit;

import com.camargos.ratelimit.policy.api.Ledger;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.GenericContainer;
import redis.clients.jedis.*;

public abstract class ReplicaFixture {
  @TempDir Path data;
  protected ConfigurableApplicationContext first, second;
  protected JedisPool observer;
  private Process server;
  private GenericContainer<?> container;
  protected int port;
  private String host = "127.0.0.1";
  protected final AtomicLong milliseconds = new AtomicLong(1_800_000_000_000L);
  protected final ObjectMapper json = new ObjectMapper();
  private final HttpClient http = HttpClient.newHttpClient();

  @BeforeEach
  protected void open() throws Exception {
    String binary = System.getenv("REDIS_TEST_BINARY");
    if (binary == null) {
      container = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
      container.start();
      host = container.getHost();
      port = container.getMappedPort(6379);
    } else {
      try (var free = new ServerSocket(0)) {
        port = free.getLocalPort();
      }
      server =
          new ProcessBuilder(
                  binary, "--bind", host, "--port", String.valueOf(port), "--dir", data.toString())
              .directory(data.toFile())
              .redirectErrorStream(true)
              .redirectOutput(data.resolve("redis.log").toFile())
              .start();
    }
    observer = new JedisPool(host, port);
    org.awaitility.Awaitility.await()
        .atMost(Duration.ofSeconds(10))
        .ignoreExceptions()
        .until(
            () -> {
              try (var client = observer.getResource()) {
                return "PONG".equals(client.ping());
              }
            });
    first = start();
    second = start();
  }

  protected ConfigurableApplicationContext start() {
    Clock clock =
        new Clock() {
          public ZoneId getZone() {
            return ZoneOffset.UTC;
          }

          public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant(), zone);
          }

          public Instant instant() {
            return Instant.ofEpochMilli(milliseconds.get());
          }
        };
    return new SpringApplicationBuilder(RateLimitingApplication.class)
        .initializers(ctx -> ctx.getBeanFactory().registerSingleton("testClock", clock))
        .properties(
            "server.port=0",
            "redis.host=" + host,
            "redis.port=" + port,
            "spring.main.banner-mode=off",
            "logging.level.root=WARN")
        .run();
  }

  @AfterEach
  protected void close() throws Exception {
    if (first != null) first.close();
    if (second != null) second.close();
    if (observer != null) observer.close();
    if (server != null) server.destroyForcibly().waitFor();
    if (container != null) container.stop();
  }

  protected HttpResponse<String> post(
      ConfigurableApplicationContext app,
      String path,
      String auth,
      String key,
      String field,
      String value)
      throws Exception {
    int httpPort = ((ServletWebServerApplicationContext) app).getWebServer().getPort();
    var req =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + httpPort + path))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json");
    if (auth != null) req.header("Authorization", auth);
    if (key != null) req.header("Idempotency-Key", key);
    return http.send(
        req.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of(field, value))))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  protected HttpResponse<String> lookup(
      ConfigurableApplicationContext app, String key, String recipient) throws Exception {
    return post(app, "/lookups", "Bearer demo-a", key, "recipientId", recipient);
  }

  protected HttpResponse<String> order(ConfigurableApplicationContext app, String key, String id)
      throws Exception {
    return post(app, "/payment-orders", "Bearer demo-a", key, "lookupId", id);
  }

  protected String id(HttpResponse<String> response) throws Exception {
    return json.readTree(response.body()).required("lookupId").asText();
  }

  protected long balance() {
    return first.getBean(Ledger.class).balance("alpha");
  }

  protected List<String> reads(ConfigurableApplicationContext app) {
    Object directory = app.getBean("directory");
    Object instance =
        org.springframework.test.util.ReflectionTestUtils.getField(directory, "instance");
    try (var client = observer.getResource()) {
      return client.keys("directory:*").stream()
          .filter(k -> instance.equals(client.hget(k, "instance")))
          .map(k -> client.hget(k, "recipient"))
          .sorted(Comparator.reverseOrder())
          .toList();
    }
  }
}
