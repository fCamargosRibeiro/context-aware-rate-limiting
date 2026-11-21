package com.camargos.ratelimit.investigation;

import java.util.*;

final class Triage {
  enum Hypothesis {
    CONFIRMATION_REGRESSION(
        "A shared confirmation regression after deployment",
        "A downstream outage coincided with rollout",
        "Dependency health and rollback observations",
        "Compare confirmation traces before and after rollback"),
    INTEGRATION_GAP(
        "Persistent absence of orders in one integration",
        "An intentional lookup-only workflow or an interrupted client",
        "Client intent and confirmation attempts",
        "Check client-side confirmation telemetry before investigating possible misuse");
    final String main, alternative, missing, next;

    Hypothesis(String main, String alternative, String missing, String next) {
      this.main = main;
      this.alternative = alternative;
      this.missing = missing;
      this.next = next;
    }
  }

  record Answer(Hypothesis hypothesis, Map<String, Integer> evidence) {}

  record Assessment(
      String hypothesis,
      Map<String, Integer> evidence,
      String alternative,
      String missing,
      String next,
      int verified,
      int total,
      int unsupported,
      long elapsedNanos) {}

  static Answer local(Map<String, Integer> facts) {
    Hypothesis hypothesis =
        Arrays.stream(Hypothesis.values())
            .filter(h -> supports(h, facts))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Insufficient comparative evidence"));
    var evidence = new TreeMap<String, Integer>();
    facts.forEach(
        (ref, n) -> {
          if (!ref.endsWith("/admitted")) evidence.put(ref, n);
        });
    return new Answer(hypothesis, evidence);
  }

  private static boolean supports(Hypothesis hypothesis, Map<String, Integer> facts) {
    // Both synthetic alerts compare the baseline with two subsequent mature windows.
    if (hypothesis == Hypothesis.CONFIRMATION_REGRESSION) {
      for (String integration : List.of("alpha", "beta")) {
        if (facts.getOrDefault("deploys/" + integration + "/v2/600/deployed", 0) != 1
            || facts.getOrDefault("metrics/" + integration + "/v1/0/orders", 0) <= 0) return false;
        for (int start : List.of(600, 1200)) {
          String cohort = integration + "/v2/" + start;
          if (facts.getOrDefault("metrics/" + cohort + "/orders", -1) != 0
              || facts.getOrDefault("health/" + cohort + "/confirmationErrors", 0) <= 0)
            return false;
        }
      }
      return true;
    }
    if (facts.getOrDefault("metrics/alpha/v1/0/orders", 0) <= 0) return false;
    for (int start : List.of(600, 1200)) {
      if (facts.getOrDefault("metrics/alpha/v1/" + start + "/orders", -1) != 0
          || facts.getOrDefault("metrics/beta/v1/" + start + "/orders", 0) <= 0
          || facts.getOrDefault("health/alpha/v1/" + start + "/confirmationErrors", -1) != 0)
        return false;
    }
    return true;
  }

  static Assessment assess(Answer answer, Map<String, Integer> facts, long start) {
    var hypothesis = Objects.requireNonNull(answer.hypothesis());
    var evidence = answer.evidence();
    int verified =
        (int)
            evidence.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue().equals(facts.get(e.getKey())))
                .count();
    var correct = new HashMap<String, Integer>();
    evidence.forEach(
        (key, n) -> {
          if (n != null && n.equals(facts.get(key))) correct.put(key, n);
        });
    boolean supported = supports(hypothesis, correct);
    return new Assessment(
        hypothesis.main,
        evidence,
        hypothesis.alternative,
        hypothesis.missing,
        hypothesis.next,
        verified,
        evidence.size(),
        evidence.size() - verified + (supported ? 0 : 1),
        System.nanoTime() - start);
  }
}
