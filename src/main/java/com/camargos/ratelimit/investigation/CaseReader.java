package com.camargos.ratelimit.investigation;

import com.camargos.ratelimit.monitoring.api.CaseSnapshot;
import java.util.*;

final class CaseReader {
  private final CaseSnapshot snapshot;
  private int remaining;
  private final List<String> audit = new ArrayList<>();

  CaseReader(CaseSnapshot snapshot, int budget) {
    if (budget < 0) throw new IllegalArgumentException("Invalid budget");
    this.snapshot = snapshot;
    remaining = budget;
  }

  synchronized Map<String, Integer> read(
      String source, Set<String> integrations, Set<String> versions, long from, long until) {
    if (remaining == 0) throw denied("Read budget exhausted");
    remaining--;
    if (!Set.of("metrics", "health", "deploys").contains(source)
        || from < 0
        || until > 1800
        || until <= from
        || !allowed(1).containsAll(integrations)
        || !allowed(2).containsAll(versions)
        || integrations.isEmpty()
        || versions.isEmpty()) throw denied("Outside case scope");
    var result = new TreeMap<String, Integer>();
    snapshot
        .facts()
        .forEach(
            (ref, value) -> {
              String[] parts = ref.split("/");
              long time = Long.parseLong(parts[3]);
              if (parts[0].equals(source)
                  && integrations.contains(parts[1])
                  && versions.contains(parts[2])
                  && time >= from
                  && time < until) result.put(ref, value);
            });
    return result;
  }

  Set<String> allowed(int dimension) {
    var result = new HashSet<String>();
    snapshot.facts().keySet().forEach(k -> result.add(k.split("/")[dimension]));
    return Set.copyOf(result);
  }

  private IllegalStateException denied(String reason) {
    audit.add(reason);
    org.slf4j.LoggerFactory.getLogger(CaseReader.class)
        .warn("Investigation read denied: {}", reason);
    return new IllegalStateException(reason);
  }

  List<String> audit() {
    return List.copyOf(audit);
  }
}
