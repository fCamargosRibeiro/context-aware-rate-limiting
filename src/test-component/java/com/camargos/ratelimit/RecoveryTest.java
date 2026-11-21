package com.camargos.ratelimit;

import static org.assertj.core.api.Assertions.*;

import com.camargos.ratelimit.policy.api.Ledger;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.PayloadApplicationEvent;

@ExtendWith(MockitoExtension.class)
class RecoveryTest extends ReplicaFixture {
  @Test
  void dadasDuplicatas_quandoConfirmar_deveCreditarUmaVez() throws Exception {
    lookup(first, "warm", "other");
    var gate = new CyclicBarrier(2);
    try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
      var one =
          threads.submit(
              () -> {
                gate.await();
                return lookup(first, "request", "known");
              });
      var two =
          threads.submit(
              () -> {
                gate.await();
                return lookup(second, "request", "known");
              });
      var original = one.get();
      assertThat(original.body()).isEqualTo(two.get().body());
      assertThat(lookup(second, "request", "known").body()).isEqualTo(original.body());
      assertThat(lookup(second, "request", "different").statusCode()).isEqualTo(409);
      String id = id(original);
      assertThat(balance()).isEqualTo(8);
      var a =
          threads.submit(
              () -> {
                gate.await();
                return order(first, "payment", id);
              });
      var b =
          threads.submit(
              () -> {
                gate.await();
                return order(second, "delivery-2", id);
              });
      var confirmed = a.get();
      assertThat(confirmed.statusCode()).isEqualTo(200);
      assertThat(confirmed.body()).isEqualTo(b.get().body());
      first.close();
      first = start();
      assertThat(order(first, "payment", id).body()).isEqualTo(confirmed.body());
      assertThat(order(first, "delivery-3", id).body()).isEqualTo(confirmed.body());
      assertThat(order(first, "payment", "different").statusCode()).isEqualTo(409);
      assertThat(balance()).isEqualTo(9);
      try (var db = observer.getResource()) {
        assertThat(db.keys("directory:*")).hasSize(2);
        assertThat(db.lrange("journey:events", 0, -1))
            .filteredOn(s -> s.contains("\"order\""))
            .hasSize(1);
      }
    }
  }

  @Test
  void dadoResultadoAtrasado_quandoReentregar_deveManterReservaEDebito() throws Exception {
    var ledger = first.getBean(Ledger.class);
    var reserved = ledger.reserve("alpha", "lost", "missing");
    assertThat(balance()).isEqualTo(9);
    var response = lookup(second, "lost", "missing");
    assertThat(id(response)).isEqualTo(reserved.lookupId());
    assertThat(balance()).isEqualTo(6);
    try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
      var gate = new CyclicBarrier(2);
      var a =
          threads.submit(
              () -> {
                gate.await();
                return ledger.result("alpha", id(response), "missing");
              });
      var b =
          threads.submit(
              () -> {
                gate.await();
                return second.getBean(Ledger.class).result("alpha", id(response), "missing");
              });
      assertThat(a.get()).isEqualTo(b.get());
    }
    assertThat(lookup(first, "lost", "missing").body()).isEqualTo(response.body());
    assertThatThrownBy(() -> ledger.result("alpha", reserved.lookupId(), "found"))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    assertThat(balance()).isEqualTo(6);
    assertThat(reads(second)).containsExactly("missing");
    try (var db = observer.getResource()) {
      assertThat(db.lrange("journey:events", 0, -1)).hasSize(2);
    }
  }

  @Test
  void dadaQuedaAposDebito_quandoReiniciar_deveConciliarPeloRecibo() throws Exception {
    first.addApplicationListener(
        event -> {
          if (event instanceof PayloadApplicationEvent<?> payload
              && payload.getPayload().getClass().getSimpleName().equals("DebitApplied"))
            throw new IllegalStateException("Injected consumer failure");
        });
    assertThat(lookup(first, "lost", "missing").statusCode()).isEqualTo(500);
    assertThat(balance()).isEqualTo(6);
    assertThat(lookup(second, "blocked", "other").statusCode()).isEqualTo(503);
    assertThat(reads(second)).isEmpty();
    assertThat(
            post(second, "/lookups", "Bearer demo-b", "independent", "recipientId", "known")
                .statusCode())
        .isEqualTo(200);
    first.close();
    milliseconds.addAndGet(120000);
    first = start();
    var recovered = lookup(first, "lost", "missing");
    assertThat(recovered.statusCode()).isEqualTo(200);
    assertThat(balance()).isEqualTo(8);
    try (var db = observer.getResource()) {
      assertThat(db.hget("account:alpha", "completed:" + id(recovered))).isEqualTo("true");
      assertThat(db.keys("directory:*")).hasSize(2);
      assertThat(db.lrange("journey:events", 0, -1))
          .filteredOn(s -> s.contains("\"missing\""))
          .hasSize(1);
    }
    assertThat(lookup(first, "resume", "known").statusCode()).isEqualTo(200);
    assertThat(balance()).isEqualTo(7);
  }
}
