package com.camargos.ratelimit.lookup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.camargos.ratelimit.policy.api.Ledger;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LookupControllerTest {
  @Mock Ledger ledger;
  @Mock Directory directory;

  @Test
  void dadoTimeout_quandoConsultar_deveRegistrarResultadoInconclusivo() throws Exception {
    // Given
    var controller = new LookupController(ledger, directory);
    var reserved = new Ledger.Lookup("lookup", null);
    var completed = new Ledger.Lookup("lookup", "inconclusive");
    when(ledger.reserve("alpha", "request", "slow")).thenReturn(reserved);
    when(directory.read("lookup", "slow")).thenThrow(new TimeoutException());
    when(ledger.result("alpha", "lookup", "inconclusive")).thenReturn(completed);

    // When
    var response = controller.lookup("Bearer demo-a", "request", new LookupController.Body("slow"));

    // Then
    assertThat(response).isEqualTo(completed);
    var sequence = inOrder(ledger, directory);
    sequence.verify(ledger).reserve("alpha", "request", "slow");
    sequence.verify(directory).read("lookup", "slow");
    sequence.verify(ledger).result("alpha", "lookup", "inconclusive");
  }
}
