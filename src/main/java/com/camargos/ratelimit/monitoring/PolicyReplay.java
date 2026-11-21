package com.camargos.ratelimit.monitoring;

import static com.camargos.ratelimit.monitoring.api.JourneyReport.*;

import com.camargos.ratelimit.monitoring.api.*;
import io.github.bucket4j.*;
import java.time.Duration;
import java.util.*;

final class PolicyReplay {
  record Measurement(
      String policy,
      int reads,
      int labeledFalseBlocks,
      long limiterNanos,
      int limiterCalls,
      double meanLimiterNanos,
      Long alertAfterSeconds) {}

  static Measurement run(Fixture fixture, String policy, Parameters p) {
    long[] now = {0};
    TimeMeter clock =
        new TimeMeter() {
          public long currentTimeNanos() {
            return now[0] * 1_000_000_000L;
          }

          public boolean isWallClockBased() {
            return true;
          }
        };
    var buckets = new HashMap<String, Bucket>();
    var admitted = new HashSet<String>();
    var observed = new ArrayList<Event>();
    int falseBlocks = 0;
    long nanos = 0;
    int calls = 0;
    for (var e : fixture.events()) {
      if (e.time() > p.cutoff()) continue;
      now[0] = e.time();
      var bucket =
          buckets.computeIfAbsent(
              e.integration(),
              k ->
                  Bucket.builder()
                      .withCustomTimePrecision(clock)
                      .addLimit(b -> b.capacity(10).refillIntervally(1, Duration.ofMinutes(1)))
                      .build());
      long start = System.nanoTime();
      if (e.type().equals("admitted")) {
        boolean ok = bucket.tryConsume(1);
        nanos += System.nanoTime() - start;
        calls++;
        if (ok) admitted.add(e.lookupId());
        else if (fixture.legitimate().contains(e.lookupId())) falseBlocks++;
      } else if (admitted.contains(e.lookupId()) && !policy.equals("fixed")) {
        if (e.type().equals("missing")) bucket.consumeIgnoringRateLimits(3);
        else if (e.type().equals("order")) bucket.addTokens(1);
        else continue;
        nanos += System.nanoTime() - start;
        calls++;
      }
    }
    for (var e : fixture.events()) if (admitted.contains(e.lookupId())) observed.add(e);
    Long alert = null;
    if (policy.equals("weighted-triage")) {
      var previous = new HashMap<String, Long>();
      for (var row : JourneyReport.aggregate(observed, p)) {
        if (!row.status().equals("mature") || row.orders() > 0) continue;
        long time = row.cohort().start();
        Long last = previous.put(row.cohort().integration(), time);
        if (last != null && last + p.window() == time) {
          long available = time + p.window() + p.grace();
          alert = alert == null ? available : Math.min(alert, available);
        }
      }
    }
    return new Measurement(
        policy,
        admitted.size(),
        falseBlocks,
        nanos,
        calls,
        calls == 0 ? 0 : (double) nanos / calls,
        alert);
  }
}
