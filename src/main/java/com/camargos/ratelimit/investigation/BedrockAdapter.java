package com.camargos.ratelimit.investigation;

import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

final class BedrockAdapter {
  interface Converse {
    JsonNode call(String prompt) throws Exception;
  }

  record Reply(Triage.Answer answer, JsonNode usage) {}

  private final ObjectMapper json = new ObjectMapper();
  private final Converse converse;

  BedrockAdapter(Converse converse) {
    this.converse = converse;
  }

  Reply investigate(String alert, Map<String, Integer> facts) throws Exception {
    String prompt =
        "Assess this synthetic alert: "
            + alert
            + ". Sources: "
            + json.writeValueAsString(facts)
            + ". Return one JSON object with hypothesis (CONFIRMATION_REGRESSION or"
            + " INTEGRATION_GAP) and evidence (map of exact source references to integer values)."
            + " Cite comparative windows and integrations.";
    var response = converse.call(prompt);
    var content = response.requiredAt("/output/message/content/0/text").asText();
    return new Reply(json.readValue(content, Triage.Answer.class), response.path("usage"));
  }

  static Converse aws(String model) {
    return prompt -> {
      var json = new ObjectMapper();
      Path request = Files.createTempFile("converse-", ".json");
      Path response = Files.createTempFile("converse-response-", ".json");
      try {
        json.writeValue(
            request.toFile(),
            Map.of(
                "modelId",
                model,
                "messages",
                List.of(Map.of("role", "user", "content", List.of(Map.of("text", prompt))))));
        var process =
            new ProcessBuilder(
                    "aws",
                    "bedrock-runtime",
                    "converse",
                    "--cli-input-json",
                    "file://" + request,
                    "--output",
                    "json",
                    "--no-cli-pager")
                .redirectOutput(response.toFile())
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        try {
          if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0)
            throw new IOException("Model unavailable");
          return json.readTree(response.toFile());
        } finally {
          if (process.isAlive()) process.destroyForcibly().waitFor();
        }
      } finally {
        Files.deleteIfExists(request);
        Files.deleteIfExists(response);
      }
    };
  }
}
