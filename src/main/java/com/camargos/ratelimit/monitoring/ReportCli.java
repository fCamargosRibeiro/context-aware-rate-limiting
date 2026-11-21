package com.camargos.ratelimit.monitoring;

import com.camargos.ratelimit.monitoring.api.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;

public final class ReportCli {
  public static void main(String[] args) throws Exception {
    run(args, System.out);
  }

  static void run(String[] args, java.io.PrintStream output) throws Exception {
    var json = new ObjectMapper();
    var p =
        new JourneyReport.Parameters(
            Long.getLong("cutoff", 1920),
            Integer.getInteger("window", 600),
            Integer.getInteger("grace", 120),
            Integer.getInteger("minimum", 12));
    output.println(json.writeValueAsString(p));
    if (args.length > 0) {
      var events = new ArrayList<JourneyReport.Event>();
      try (var reader = Files.newBufferedReader(Path.of(args[0]))) {
        for (String line; (line = reader.readLine()) != null; )
          if (!line.isBlank()) events.add(json.readValue(line, JourneyReport.Event.class));
      }
      for (var row : JourneyReport.aggregate(events, p))
        output.println(json.writeValueAsString(row));
      return;
    }
    for (String name : Fixture.NAMES) {
      var fixture = Fixture.create(name);
      output.println("Synthetic fixture: " + name);
      for (var row : JourneyReport.aggregate(fixture.events(), p))
        output.println(json.writeValueAsString(row));
      for (String policy : List.of("fixed", "weighted", "weighted-triage"))
        output.println(json.writeValueAsString(PolicyReplay.run(fixture, policy, p)));
    }
  }
}
