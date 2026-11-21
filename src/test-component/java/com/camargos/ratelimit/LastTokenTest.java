package com.camargos.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.camargos.ratelimit.policy.api.Ledger;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LastTokenTest extends ReplicaFixture {
  @Test
  void dadaUltimaFicha_quandoConsultarEmDuasReplicas_deveLerSomenteODestinatarioAdmitido()
      throws Exception {
    // Given
    for (int i = 0; i < 9; i++) first.getBean(Ledger.class).reserve("alpha", "warm-" + i, "warm");
    assertThat(balance()).isEqualTo(1);
    var gate = new CyclicBarrier(2);
    // When
    try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
      var a =
          workers.submit(
              () -> {
                gate.await(5, TimeUnit.SECONDS);
                return lookup(first, "race-a", "found-a");
              });
      var b =
          workers.submit(
              () -> {
                gate.await(5, TimeUnit.SECONDS);
                return lookup(second, "race-b", "found-b");
              });
      var codes = List.of(a.get().statusCode(), b.get().statusCode());
      // Then
      assertThat(codes).containsExactlyInAnyOrder(200, 429);
      assertThat(reads(first))
          .containsExactlyElementsOf(codes.getFirst() == 200 ? List.of("found-a") : List.of());
      assertThat(reads(second))
          .containsExactlyElementsOf(codes.getLast() == 200 ? List.of("found-b") : List.of());
      long remaining = balance();
      assertThat(remaining).isZero();
      System.out.printf("ADM-03 first=%d second=%d reads=%d,%d balance=%d%n",
          codes.getFirst(), codes.getLast(), reads(first).size(), reads(second).size(), remaining);
    }
  }
}
