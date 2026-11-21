package com.camargos.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.camargos.ratelimit.policy.api.Ledger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdmissionTest extends ReplicaFixture {
  @Test
  void dadoDiretorio_quandoConsultar_deveCobrarPorResultado() throws Exception {
    // Given independent identities at capacity
    var found = lookup(first, "found", "known");
    var timeout = post(second, "/lookups", "Bearer demo-b", "slow", "recipientId", "timeout");
    // Then
    assertThat(found.body()).contains("\"found\"");
    assertThat(timeout.body()).contains("\"inconclusive\"");
    assertThat(balance()).isEqualTo(9);
    assertThat(first.getBean(Ledger.class).balance("beta")).isEqualTo(9);
    assertThat(reads(first)).containsExactly("known");
    assertThat(reads(second)).containsExactly("timeout");
    assertThat(order(first, "foreign", id(timeout)).statusCode()).isEqualTo(404);
    assertThat(
            post(second, "/payment-orders", "Bearer demo-b", "bad", "lookupId", id(timeout))
                .statusCode())
        .isEqualTo(404);
    assertThat(
            post(second, "/payment-orders", "Bearer demo-b", "foreign", "lookupId", id(found))
                .statusCode())
        .isEqualTo(404);
    assertThat(order(first, "absent", "unknown").statusCode()).isEqualTo(404);
    milliseconds.addAndGet(60000);
    assertThat(order(first, "full", id(found)).statusCode()).isEqualTo(200);
    assertThat(balance()).isEqualTo(10);
  }

  @Test
  void dadaDivida_quandoRepor_deveAdmitirNoQuartoMinuto() throws Exception {
    for (int i = 0; i < 9; i++) lookup(first, "warm" + i, "known");
    var missing = lookup(first, "negative", "missing");
    assertThat(missing.body()).contains("\"missing\"");
    assertThat(order(first, "invalid", id(missing)).statusCode()).isEqualTo(404);
    for (int i = 0; i < 4; i++) {
      assertThat(balance()).isEqualTo(i - 3);
      assertThat(lookup(second, "attempt" + i, "other").statusCode()).isEqualTo(429);
      milliseconds.addAndGet(60000);
    }
    assertThat(lookup(second, "available", "other").statusCode()).isEqualTo(200);
    assertThat(balance()).isZero();
    assertThat(reads(second)).containsExactly("other");
  }

  @Test
  void dadaFalhaAntesDaReserva_quandoConsultar_devePreservarSaldo() throws Exception {
    lookup(first, "before", "known");
    for (String auth : new String[] {null, "Bearer invalid"})
      assertThat(post(second, "/lookups", auth, "x", "recipientId", "other").statusCode())
          .isEqualTo(401);
    assertThat(lookup(second, "", "other").statusCode()).isEqualTo(400);
    try (var client = observer.getResource()) {
      client.clientPause(3600);
    }
    assertThat(lookup(second, "offline", "other").statusCode()).isEqualTo(503);
    org.awaitility.Awaitility.await().ignoreExceptions().until(() -> balance() == 9);
    assertThat(balance()).isEqualTo(9);
    assertThat(reads(second)).isEmpty();
  }
}
