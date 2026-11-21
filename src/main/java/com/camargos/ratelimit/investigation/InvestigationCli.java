package com.camargos.ratelimit.investigation;

import com.camargos.ratelimit.monitoring.api.CaseSnapshot;
import com.fasterxml.jackson.databind.*;
import java.util.*;

public final class InvestigationCli {
  private static final ObjectMapper JSON = new ObjectMapper();

  static Map<String, Integer> gather(CaseReader reader) {
    var facts = new TreeMap<String, Integer>();
    for (var source : List.of("metrics", "health", "deploys"))
      facts.putAll(reader.read(source, reader.allowed(1), reader.allowed(2), 0, 1800));
    return Collections.unmodifiableMap(facts);
  }

  public static void main(String[] args) throws Exception {
    for (String scenario : List.of("deployment", "concentrated")) {
      var snapshot = CaseSnapshot.synthetic(scenario);
      var modes = new ArrayList<>(List.of("local"));
      if (args.length > 0) modes.add(args[0]);
      for (String mode : modes) {
        long start = System.nanoTime();
        var output = new LinkedHashMap<String, Object>();
        output.put("mode", mode);
        output.put("syntheticCase", scenario);
        output.put("alert", snapshot.alert());
        try {
          var facts = gather(new CaseReader(snapshot, Integer.getInteger("budget", 3)));
          Triage.Answer answer;
          if (mode.equals("local")) answer = Triage.local(facts);
          else {
            BedrockAdapter.Converse transport =
                switch (mode) {
                  case "simulated" -> prompt -> simulated(scenario);
                  case "bedrock" ->
                      BedrockAdapter.aws(
                          Objects.requireNonNull(
                              System.getenv("BEDROCK_MODEL_ID"), "Missing model ID"));
                  default -> throw new IllegalArgumentException("Unknown mode");
                };
            var reply = new BedrockAdapter(transport).investigate(snapshot.alert(), facts);
            answer = reply.answer();
            output.put("usage", reply.usage());
            output.put("cost", null);
          }
          output.put("assessment", Triage.assess(answer, facts, start));
        } catch (Exception failure) {
          output.put("status", "degraded");
          output.put(
              "reason",
              failure instanceof IllegalStateException
                  ? failure.getMessage()
                  : "Model unavailable or invalid response");
        }
        System.out.println(JSON.writeValueAsString(output));
      }
    }
  }

  static JsonNode simulated(String scenario) throws Exception {
    boolean deploy = scenario.equals("deployment");
    var refs = new TreeMap<String, Integer>();
    refs.put("metrics/alpha/v1/0/orders", 16);
    for (int start : List.of(600, 1200)) {
      if (deploy) {
        for (String integration : List.of("alpha", "beta")) {
          refs.put("deploys/" + integration + "/v2/600/deployed", 1);
          refs.put("metrics/" + integration + "/v1/0/orders", 16);
          refs.put("health/" + integration + "/v2/" + start + "/confirmationErrors", 16);
          refs.put("metrics/" + integration + "/v2/" + start + "/orders", 0);
        }
      } else {
        refs.put("metrics/alpha/v1/" + start + "/orders", 0);
        refs.put("metrics/beta/v1/" + start + "/orders", 16);
        refs.put("health/alpha/v1/" + start + "/confirmationErrors", 0);
      }
    }
    var reply =
        new Triage.Answer(
            deploy ? Triage.Hypothesis.CONFIRMATION_REGRESSION : Triage.Hypothesis.INTEGRATION_GAP,
            refs);
    return JSON.valueToTree(
        Map.of(
            "output",
            Map.of(
                "message",
                Map.of("content", List.of(Map.of("text", JSON.writeValueAsString(reply)))))));
  }
}
