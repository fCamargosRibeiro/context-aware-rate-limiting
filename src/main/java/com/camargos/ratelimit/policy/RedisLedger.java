package com.camargos.ratelimit.policy;

import static org.springframework.http.HttpStatus.*;

import com.camargos.ratelimit.policy.api.Ledger;
import io.github.bucket4j.*;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.jedis.Bucket4jJedis;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import redis.clients.jedis.*;

@Service
class RedisLedger implements Ledger {
  private final JedisPool pool;
  private final Clock clock;
  private final String version;
  private final org.springframework.context.ApplicationEventPublisher publisher;
  private final ProxyManager<byte[]> manager;
  private final BucketConfiguration config =
      BucketConfiguration.builder()
          .addLimit(b -> b.capacity(10).refillIntervally(1, Duration.ofMinutes(1)))
          .build();

  RedisLedger(
      JedisPool pool,
      redis.clients.jedis.UnifiedJedis bucketClient,
      org.springframework.context.ApplicationEventPublisher publisher,
      Clock clock,
      @Value("${journey.version:v1}") String version) {
    this.pool = pool;
    this.publisher = publisher;
    this.clock = clock;
    if (!version.matches("[a-zA-Z0-9._-]{1,32}"))
      throw new IllegalArgumentException("Invalid version");
    this.version = version;
    this.manager =
        Bucket4jJedis.casBasedBuilder(bucketClient)
            .clientClock(
                new TimeMeter() {
                  public long currentTimeNanos() {
                    return clock.millis() * 1_000_000L;
                  }

                  public boolean isWallClockBased() {
                    return true;
                  }
                })
            .build();
  }

  // Run Bucket4j on a private candidate. Publish its bytes and the business receipt in one EXEC.
  private <T> T update(String identity, Function<Change, T> action) {
    String account = "account:" + identity;
    try (Jedis db = pool.getResource()) {
      while (true) {
        db.watch(account);
        byte[] candidate = bytes("candidate:" + UUID.randomUUID());
        try {
          byte[] previous = db.hget(bytes(account), bytes("bucket"));
          if (previous != null) db.set(candidate, previous);
          var change =
              new Change(db, account, identity, manager.builder().build(candidate, config));
          T answer = action.apply(change);
          byte[] snapshot = db.get(candidate);
          try (Transaction tx = db.multi()) {
            if (snapshot != null) tx.hset(bytes(account), bytes("bucket"), snapshot);
            if (!change.fields.isEmpty()) tx.hset(account, change.fields);
            for (String event : change.events) tx.rpush("journey:events", event);
            tx.del(candidate);
            if (tx.exec() != null) return answer;
          }
        } finally {
          db.unwatch();
          db.del(candidate);
        }
      }
    }
  }

  private static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  private class Change {
    final Jedis db;
    final String account, identity;
    final Bucket bucket;
    final Map<String, String> fields = new HashMap<>();
    final List<String> events = new ArrayList<>();

    Change(Jedis db, String account, String identity, Bucket bucket) {
      this.db = db;
      this.account = account;
      this.identity = identity;
      this.bucket = bucket;
    }

    String get(String field) {
      return db.hget(account, field);
    }

    void event(String id, String type) {
      event(id, type, null);
    }

    void event(String id, String type, String order) {
      events.add(
          """
          {"lookupId":"%s","type":"%s","time":%d,"integration":"%s","version":"%s"%s}
          """
              .formatted(
                  id,
                  type,
                  clock.instant().getEpochSecond(),
                  identity,
                  version,
                  order == null ? "" : ",\"paymentOrderId\":\"" + order + "\"")
              .trim());
    }
  }

  public Lookup reserve(String identity, String key, String recipient) {
    Lookup lookup =
        update(
            identity,
            c -> {
              String field = "lookup-key:" + key;
              String id = c.get(field);
              if (id != null) {
                if (!recipient.equals(c.get("recipient:" + id)))
                  throw new ResponseStatusException(CONFLICT);
                String outcome = c.get("result:" + id);
                return new Lookup(id, outcome);
              }
              if (!Objects.toString(c.get("pending"), "").isEmpty())
                throw new ResponseStatusException(SERVICE_UNAVAILABLE);
              id = UUID.randomUUID().toString();
              if (!c.bucket.tryConsume(1)) {
                c.event(id, "refused");
                c.fields.put(field, id);
                c.fields.put("recipient:" + id, recipient);
                c.fields.put("result:" + id, "refused");
                return new Lookup(id, "refused");
              }
              c.fields.put(field, id);
              c.fields.put("recipient:" + id, recipient);
              c.event(id, "admitted");
              return new Lookup(id, null);
            });
    if ("refused".equals(lookup.outcome())) throw new ResponseStatusException(TOO_MANY_REQUESTS);
    return lookup;
  }

  public Lookup result(String identity, String id, String outcome) {
    if (!Set.of("found", "missing", "inconclusive").contains(outcome))
      throw new ResponseStatusException(BAD_REQUEST);
    Lookup applied =
        update(
            identity,
            c -> {
              if (c.get("recipient:" + id) == null) throw new ResponseStatusException(NOT_FOUND);
              String old = c.get("result:" + id);
              if (old != null && !old.equals(outcome)) {
                org.slf4j.LoggerFactory.getLogger(RedisLedger.class)
                    .warn("Conflicting result for recorded lookup {}", id);
                throw new ResponseStatusException(CONFLICT);
              }
              if (old == null) {
                String pending = c.get("pending");
                if (pending != null && !pending.isEmpty())
                  throw new ResponseStatusException(SERVICE_UNAVAILABLE);
                if (outcome.equals("missing")) {
                  c.bucket.consumeIgnoringRateLimits(3);
                  c.fields.put("pending", id);
                }
                c.fields.put("result:" + id, outcome);
                c.event(id, outcome);
              }
              return new Lookup(id, outcome);
            });
    if (outcome.equals("missing")) {
      publisher.publishEvent(new DebitApplied(id));
      update(
          identity,
          c -> {
            if (id.equals(c.get("pending"))) {
              c.fields.put("pending", "");
              c.fields.put("completed:" + id, "true");
            }
            return null;
          });
    }
    return applied;
  }

  record DebitApplied(String lookupId) {}

  public Order confirm(String identity, String key, String id) {
    return update(
        identity,
        c -> {
          String field = "order-key:" + key;
          String linked = c.get(field);
          if (linked != null && !linked.equals(id)) throw new ResponseStatusException(CONFLICT);
          if (!"found".equals(c.get("result:" + id))) throw new ResponseStatusException(NOT_FOUND);
          String order = c.get("order:" + id);
          if (order == null) {
            order = UUID.randomUUID().toString();
            c.bucket.addTokens(1);
            c.fields.put("order:" + id, order);
            c.event(id, "order", order);
          }
          c.fields.put(field, id);
          return new Order(order, id);
        });
  }

  public long balance(String identity) {
    return update(identity, c -> c.bucket.getAvailableTokens());
  }
}
