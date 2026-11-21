package com.camargos.ratelimit.monitoring.api;

import java.util.*;

public final class JourneyReport {
  @com.fasterxml.jackson.annotation.JsonInclude(
      com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
  public record Event(
      String lookupId,
      String type,
      long time,
      String integration,
      String version,
      String paymentOrderId) {
    public Event(String lookupId, String type, long time, String integration, String version) {
      this(lookupId, type, time, integration, version, null);
    }
  }

  public record Parameters(long cutoff, int window, int grace, int minimum) {
    public Parameters {
      if (window < 1 || grace < 0 || minimum < 1)
        throw new IllegalArgumentException("Invalid report parameters");
    }
  }

  public record Cohort(String integration, String version, long start)
      implements Comparable<Cohort> {
    public int compareTo(Cohort other) {
      int c = integration.compareTo(other.integration);
      if (c == 0) c = Long.compare(start, other.start);
      return c == 0 ? version.compareTo(other.version) : c;
    }

    public String reference() {
      return integration + "/" + version + "/" + start;
    }
  }

  public record Row(
      Cohort cohort,
      int admitted,
      int refused,
      int found,
      int missing,
      int timeouts,
      int confirmationErrors,
      int orders,
      Double missingFraction,
      Double lookupsPerOrder,
      String status) {}

  public static List<Row> aggregate(List<Event> input, Parameters p) {
    var origins = new HashMap<String, Cohort>();
    var totals = new TreeMap<Cohort, Map<String, Integer>>();
    var visible =
        input.stream()
            .filter(e -> e.time <= p.cutoff)
            .sorted(Comparator.comparingLong(Event::time))
            .toList();
    for (var e : visible) {
      if (e.type.equals("admitted") || e.type.equals("refused"))
        origins.putIfAbsent(
            e.lookupId,
            new Cohort(e.integration, e.version, Math.floorDiv(e.time, p.window) * p.window));
    }
    record Effect(String lookup, String type) {}
    var seen = new HashSet<Effect>();
    for (var e : visible) {
      var cohort = origins.get(e.lookupId);
      if (cohort != null && seen.add(new Effect(e.lookupId, e.type)))
        totals.computeIfAbsent(cohort, k -> new HashMap<>()).merge(e.type, 1, Integer::sum);
    }
    var rows = new ArrayList<Row>();
    totals.forEach(
        (cohort, c) -> {
          int admitted = c.getOrDefault("admitted", 0), found = c.getOrDefault("found", 0);
          int missing = c.getOrDefault("missing", 0), orders = c.getOrDefault("order", 0);
          String status = "mature";
          if (cohort.start + p.window + p.grace > p.cutoff) status = "awaiting completion";
          else if (admitted < p.minimum) status = "insufficient sample";
          rows.add(
              new Row(
                  cohort,
                  admitted,
                  c.getOrDefault("refused", 0),
                  found,
                  missing,
                  c.getOrDefault("inconclusive", 0),
                  c.getOrDefault("confirmation-error", 0),
                  orders,
                  ratio(missing, missing + found),
                  ratio(admitted, orders),
                  status));
        });
    return List.copyOf(rows);
  }

  private static Double ratio(int n, int d) {
    return d == 0 ? null : (double) n / d;
  }
}
