package com.camargos.ratelimit.investigation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.camargos.ratelimit.monitoring.api.CaseSnapshot;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvestigationTest {
  @Test
  void dadoCaso_quandoExcederEscopoOuOrcamento_deveRecusar() {
    var snapshot = CaseSnapshot.synthetic("deployment");
    var reader = new CaseReader(snapshot, 4);
    var integrations = Set.of("alpha");
    var versions = Set.of("v1");
    assertThatIllegalStateException()
        .isThrownBy(() -> reader.read("block", integrations, versions, 0, 600));
    assertThatIllegalStateException()
        .isThrownBy(() -> reader.read("metrics", Set.of("outside"), versions, 0, 600));
    assertThatIllegalStateException()
        .isThrownBy(() -> reader.read("deploys", integrations, versions, 0, 2400));
    assertThatIllegalStateException()
        .isThrownBy(() -> reader.read("health", integrations, Set.of("v3"), 0, 600));
    assertThatIllegalStateException()
        .isThrownBy(() -> reader.read("health", integrations, versions, 0, 600))
        .withMessageContaining("budget");
    assertThat(reader.audit()).hasSize(5);
    assertThat(new CaseReader(snapshot, 1).read("health", integrations, versions, 0, 600))
        .containsExactlyEntriesOf(Map.of("health/alpha/v1/0/confirmationErrors", 0));
  }

  @Test
  void dadosDoisAlertas_quandoComparar_deveVerificarValoresEContrato() throws Exception {
    for (String scenario : List.of("deployment", "concentrated")) {
      var snapshot = CaseSnapshot.synthetic(scenario);
      var facts = InvestigationCli.gather(new CaseReader(snapshot, 3));
      var local = Triage.local(facts);
      var model =
          new BedrockAdapter(
              prompt -> {
                assertThat(prompt)
                    .contains(snapshot.alert(), "metrics/", "health/", "deploys/")
                    .doesNotContain("lookupId", "recipientId", "Bearer");
                return InvestigationCli.simulated(scenario);
              });
      var simulated = model.investigate(snapshot.alert(), facts);
      assertThat(simulated.answer().hypothesis()).isEqualTo(local.hypothesis());
      for (var answer : List.of(local, simulated.answer())) {
        var assessment = Triage.assess(answer, facts, System.nanoTime());
        assertThat(assessment.verified()).isEqualTo(assessment.total()).isGreaterThan(2);
        assertThat(assessment.unsupported()).isZero();
        assertThat(assessment.next()).isNotBlank();
      }
      var invalid =
          new Triage.Answer(
              local.hypothesis(), Map.of("metrics/absent", 9, "metrics/alpha/v1/0/admitted", 1));
      assertThat(Triage.assess(invalid, facts, System.nanoTime()).verified()).isZero();
    }
  }

  @Test
  void dadoModeloIndisponivel_quandoInvestigar_deveEncerrarSemRepeticao() throws Exception {
    var remote = mock(BedrockAdapter.Converse.class);
    when(remote.call(anyString())).thenThrow(new java.io.IOException("Model unavailable"));
    assertThatThrownBy(() -> new BedrockAdapter(remote).investigate("Alert", Map.of()))
        .hasMessage("Model unavailable");
    verify(remote).call(anyString());
  }
}
