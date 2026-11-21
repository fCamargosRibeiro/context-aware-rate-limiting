package com.camargos.ratelimit.lookup;

import com.camargos.ratelimit.lookup.api.SyntheticRequest;
import com.camargos.ratelimit.policy.api.Ledger;
import org.springframework.web.bind.annotation.*;

@RestController
class LookupController {
  record Body(String recipientId) {}

  private final Ledger ledger;
  private final Directory directory;

  LookupController(Ledger ledger, Directory directory) {
    this.ledger = ledger;
    this.directory = directory;
  }

  @PostMapping("/lookups")
  Ledger.Lookup lookup(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody Body body) {
    String identity = SyntheticRequest.identity(auth);
    String recipient = SyntheticRequest.required(body.recipientId());
    var reserved = ledger.reserve(identity, SyntheticRequest.required(key), recipient);
    if (reserved.outcome() != null)
      return ledger.result(identity, reserved.lookupId(), reserved.outcome());
    String outcome;
    try {
      outcome = directory.read(reserved.lookupId(), recipient);
    } catch (java.util.concurrent.TimeoutException timeout) {
      outcome = "inconclusive";
    }
    return ledger.result(identity, reserved.lookupId(), outcome);
  }
}
