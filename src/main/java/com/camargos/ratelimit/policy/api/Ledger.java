package com.camargos.ratelimit.policy.api;

public interface Ledger {
  record Lookup(String lookupId, String outcome) {}

  record Order(String paymentOrderId, String lookupId) {}

  Lookup reserve(String identity, String key, String recipient);

  Lookup result(String identity, String lookupId, String outcome);

  Order confirm(String identity, String key, String lookupId);

  long balance(String identity);
}
