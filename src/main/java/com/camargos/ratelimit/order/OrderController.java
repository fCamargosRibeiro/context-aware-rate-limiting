package com.camargos.ratelimit.order;

import com.camargos.ratelimit.lookup.api.SyntheticRequest;
import com.camargos.ratelimit.policy.api.Ledger;
import org.springframework.web.bind.annotation.*;

@RestController
class OrderController {
  record Body(String lookupId) {}

  private final Ledger ledger;

  OrderController(Ledger ledger) {
    this.ledger = ledger;
  }

  @PostMapping("/payment-orders")
  Ledger.Order confirm(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody Body body) {
    return ledger.confirm(
        SyntheticRequest.identity(auth),
        SyntheticRequest.required(key),
        SyntheticRequest.required(body.lookupId()));
  }
}
