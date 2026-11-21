package com.camargos.ratelimit.monitoring.api;

import java.util.*;

public record CaseSnapshot(String alert, Map<String, Integer> facts) {
  public CaseSnapshot {
    facts = Map.copyOf(facts);
  }

  public static CaseSnapshot synthetic(String scenario) {
    var fixture = Fixture.create(scenario);
    var facts = new TreeMap<String, Integer>();
    var params = new JourneyReport.Parameters(1920, 600, 120, 12);
    for (var row : JourneyReport.aggregate(fixture.events(), params)) {
      if (!row.status().equals("mature")) continue;
      String ref = row.cohort().reference();
      facts.put("metrics/" + ref + "/admitted", row.admitted());
      facts.put("metrics/" + ref + "/orders", row.orders());
      facts.put("health/" + ref + "/confirmationErrors", row.confirmationErrors());
    }
    for (var deploy : fixture.deploys())
      facts.put(
          "deploys/"
              + deploy.integration()
              + "/"
              + deploy.version()
              + "/"
              + deploy.time()
              + "/deployed",
          1);
    return new CaseSnapshot("Order conversion fell across mature cohorts", facts);
  }
}
