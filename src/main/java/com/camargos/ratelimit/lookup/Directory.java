package com.camargos.ratelimit.lookup;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisPool;

@Component
class Directory {
  private final JedisPool pool;
  private final String instance = UUID.randomUUID().toString();

  Directory(JedisPool pool) {
    this.pool = pool;
  }

  String read(String id, String recipient) throws java.util.concurrent.TimeoutException {
    // The synthetic directory owns its read receipt, including the replica that performed it.
    try (var db = pool.getResource()) {
      String outcome =
          (String)
              db.eval(
                  """
                  local prior = redis.call('HGET', KEYS[1], 'outcome')
                  if prior then return prior end
                  local outcome = 'found'
                  if ARGV[1] == 'missing' then outcome = 'missing' end
                  if ARGV[1] == 'timeout' then outcome = 'inconclusive' end
                  redis.call('HSET', KEYS[1], 'outcome', outcome, 'recipient', ARGV[1], 'instance', ARGV[2])
                  return outcome
                  """,
                  List.of("directory:" + id),
                  List.of(recipient, instance));
      if (outcome.equals("inconclusive"))
        throw new java.util.concurrent.TimeoutException("Directory timed out");
      return outcome;
    }
  }
}
