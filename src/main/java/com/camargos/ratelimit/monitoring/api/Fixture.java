package com.camargos.ratelimit.monitoring.api;

import static com.camargos.ratelimit.monitoring.api.JourneyReport.Event;

import java.nio.charset.StandardCharsets;
import java.util.*;

public record Fixture(
    String name, List<Event> events, Set<String> legitimate, List<Deploy> deploys) {
  public record Deploy(String integration, String version, long time) {}

  public static final List<String> NAMES =
      List.of("deployment", "concentrated", "legitimate-peak", "stale-directory");

  public static Fixture create(String name) {
    if (!NAMES.contains(name)) throw new IllegalArgumentException("Unknown fixture");
    var events = new ArrayList<Event>();
    var legitimate = new HashSet<String>();
    var deploys = new ArrayList<Deploy>();
    for (String integration : List.of("alpha", "beta")) {
      deploys.add(new Deploy(integration, "v1", 0));
      if (name.equals("deployment")) deploys.add(new Deploy(integration, "v2", 600));
      for (int window = 0; window < 3; window++) {
        boolean regression = name.equals("deployment") && window > 0;
        boolean gap = name.equals("concentrated") && integration.equals("alpha") && window > 0;
        String version = regression ? "v2" : "v1";
        for (int i = 0; i < 24; i++) {
          long time = 600L * window + i * (name.equals("legitimate-peak") ? 1 : 25);
          String id =
              UUID.nameUUIDFromBytes(
                      (integration + "/" + window + "/" + i).getBytes(StandardCharsets.UTF_8))
                  .toString();
          if (!gap) legitimate.add(id);
          events.add(new Event(id, "admitted", time, integration, version));
          boolean missing = name.equals("stale-directory") && i % 2 == 0;
          events.add(new Event(id, missing ? "missing" : "found", time + 1, integration, version));
          if (!missing && !gap && i % 3 != 2)
            events.add(
                new Event(
                    id,
                    regression ? "confirmation-error" : "order",
                    time + 2,
                    integration,
                    version));
        }
      }
    }
    events.sort(Comparator.comparingLong(Event::time));
    return new Fixture(name, List.copyOf(events), Set.copyOf(legitimate), List.copyOf(deploys));
  }
}
