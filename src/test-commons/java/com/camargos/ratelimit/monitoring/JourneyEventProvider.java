package com.camargos.ratelimit.monitoring;

import com.camargos.ratelimit.monitoring.api.JourneyReport.Event;
import java.util.*;

final class JourneyEventProvider {
  static List<Event> numericalCohort() {
    var input = new ArrayList<Event>();
    for (int i = 0; i < 11; i++) {
      String id = UUID.nameUUIDFromBytes(new byte[] {(byte) i}).toString();
      input.add(new Event(id, "admitted", 590, "alpha", "v1"));
      input.add(
          new Event(id, i < 8 ? "found" : i < 10 ? "missing" : "inconclusive", 592, "alpha", "v1"));
      if (i < 4) input.add(new Event(id, "order", 610, "alpha", "v2"));
    }
    input.add(input.get(2));
    input.add(new Event("c7373bc2-349d-4203-956f-05e1e08d20b2", "refused", 599, "alpha", "v1"));
    return List.copyOf(input);
  }
}
