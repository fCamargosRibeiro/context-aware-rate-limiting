package com.camargos.ratelimit.monitoring;

import static com.camargos.ratelimit.monitoring.api.JourneyReport.*;
import static org.assertj.core.api.Assertions.*;

import com.camargos.ratelimit.monitoring.api.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MonitoringTest {
  @Test
  void dadaCoorte_quandoAgregar_deveDeduplicarEAtribuirOrdensTardias() {
    var input = JourneyEventProvider.numericalCohort();
    var early = aggregate(input, new Parameters(600, 600, 120, 12)).getFirst();
    assertThat(early.orders()).isZero();
    assertThat(early.lookupsPerOrder()).isNull();
    assertThat(early.status()).isEqualTo("awaiting completion");
    var row = aggregate(input, new Parameters(720, 600, 120, 12)).getFirst();
    assertThat(row)
        .isEqualTo(
            new Row(
                new Cohort("alpha", "v1", 0),
                11,
                1,
                8,
                2,
                1,
                0,
                4,
                0.2,
                2.75,
                "insufficient sample"));
  }

  @Test
  void dadosEventosJsonLines_quandoGerarRelatorio_deveEmitirSomenteAgregados(
      @org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
    var json = new com.fasterxml.jackson.databind.ObjectMapper();
    var events = JourneyEventProvider.numericalCohort();
    var file = dir.resolve("events.jsonl");
    try (var writer = java.nio.file.Files.newBufferedWriter(file)) {
      for (var event : events) {
        writer.write(json.writeValueAsString(event));
        writer.newLine();
      }
    }
    var bytes = new java.io.ByteArrayOutputStream();
    ReportCli.run(new String[] {file.toString()}, new java.io.PrintStream(bytes));
    String report = bytes.toString(java.nio.charset.StandardCharsets.UTF_8);
    assertThat(report)
        .doesNotContain("recipientId", "lookupId", "Authorization", events.getFirst().lookupId());
    var row = json.readTree(report.lines().skip(1).findFirst().orElseThrow());
    assertThat(row.required("admitted").asInt()).isEqualTo(11);
    assertThat(row.required("orders").asInt()).isEqualTo(4);
  }

  @Test
  void dadasFixtures_quandoComparar_deveMedirPoliticasComEntradasIguais() {
    var params = new Parameters(1920, 600, 120, 12);
    for (String name : Fixture.NAMES) {
      var f = Fixture.create(name);
      var rows = aggregate(f.events(), params);
      assertThat(rows).hasSize(6).allMatch(r -> r.admitted() == 24);
      var weighted = PolicyReplay.run(f, "weighted", params);
      var triage = PolicyReplay.run(f, "weighted-triage", params);
      assertThat(triage.reads()).isEqualTo(weighted.reads());
      assertThat(triage.labeledFalseBlocks()).isEqualTo(weighted.labeledFalseBlocks());
      assertThat(PolicyReplay.run(f, "fixed", params).reads()).isPositive();
      assertThat(f.events()).allMatch(e -> e.lookupId().matches("[a-f0-9-]{36}"));
    }
  }
}
