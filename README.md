# Context-aware rate limiting

Two Spring Boot replicas reserve tokens from one Redis-backed Bucket4j budget per authenticated synthetic identity. A found lookup costs one token, a missing recipient costs four, and a timeout costs one. A confirmed payment order can return one token for its original found lookup. Capacity is ten, with one token replenished per complete minute.

[Português](README.pt-BR.md) · [C4 architecture and recovery](docs/architecture.md) · [Acceptance evidence](docs/evidence.md)

## Run the experiment

Use JDK 25 and Docker with Compose. The Gradle Wrapper runs unit, integration and component tests during `build`. By default the latter suites start isolated Redis containers. Set `REDIS_TEST_BINARY` to the absolute path of a compatible local redis-server executable to run them without Docker. The offline reporting and investigation commands run without Redis or AWS.

Run the commands below from the repository root. Compose binds the two replicas to loopback on ports 8081 and 8082. Wait for both servers to start (`docker compose -f config/compose.yaml logs replica-a replica-b`). The examples retry both operations, reusing their original keys. A new operation needs a fresh key. The Redis volume survives `docker compose -f config/compose.yaml down`.

### Windows PowerShell

```powershell
.\gradlew.bat build
.\gradlew.bat -q report
.\gradlew.bat -q investigate -Pmode=simulated
docker compose -f config/compose.yaml up -d --build
$headers = @{ Authorization = 'Bearer demo-a'; 'Idempotency-Key' = 'lookup-001' }
$lookup = @{ Uri = 'http://localhost:8081/lookups'; Method = 'Post'; ContentType = 'application/json'; Headers = $headers; Body = '{"recipientId":"found-a"}' }
$result = Invoke-RestMethod @lookup
$lookup.Uri = 'http://localhost:8082/lookups'
Invoke-RestMethod @lookup
$order = @{ Uri = 'http://localhost:8082/payment-orders'; Method = 'Post'; ContentType = 'application/json'; Headers = @{ Authorization = 'Bearer demo-a'; 'Idempotency-Key' = 'order-001' }; Body = (@{ lookupId = $result.lookupId } | ConvertTo-Json) }
Invoke-RestMethod @order
Invoke-RestMethod @order
docker compose -f config/compose.yaml down
```

### Linux and macOS

The HTTP example uses curl and jq.

```sh
sh gradlew build
sh gradlew -q report
sh gradlew -q investigate -Pmode=simulated
docker compose -f config/compose.yaml up -d --build
lookup() {
  curl -fsS "http://localhost:$1/lookups" -H 'Authorization: Bearer demo-a' \
    -H 'Content-Type: application/json' -H 'Idempotency-Key: lookup-001' \
    -d '{"recipientId":"found-a"}'
}
id=$(lookup 8081 | jq -er .lookupId)
lookup 8082
order() {
  curl -fsS http://localhost:8082/payment-orders -H 'Authorization: Bearer demo-a' \
    -H 'Content-Type: application/json' -H 'Idempotency-Key: order-001' \
    -d "$(jq -nc --arg id "$id" '{lookupId:$id}')"
}
order
order
docker compose -f config/compose.yaml down
```

## Inputs and analysis

`Bearer demo-a` and `Bearer demo-b` identify separate demo accounts. Use the synthetic recipients `missing` or `timeout` to exercise those branches. Other nonblank recipients up to 128 characters resolve as found. HTTP 400 indicates invalid input, 401 an unknown credential, 404 an ineligible order reference, 409 conflicting key content, 429 insufficient balance and 503 unavailable admission.

While Compose is running, export events with `docker compose -f config/compose.yaml exec -T redis redis-cli --raw LRANGE journey:events 0 -1 > events.jsonl` in a POSIX shell. In PowerShell use `[IO.File]::WriteAllLines("$PWD/events.jsonl", [string[]](docker compose -f config/compose.yaml exec -T redis redis-cli --raw LRANGE journey:events 0 -1), [Text.UTF8Encoding]::new($false))`. Supply the cutoff in epoch seconds, for example `sh gradlew -q report -Pevents=events.jsonl -Pcutoff="$(date +%s)"` or `.\gradlew.bat -q report -Pevents=events.jsonl "-Pcutoff=$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())"`. The defaults for `-Pwindow`, `-Pgrace` and `-Pminimum` are 600 seconds, 120 seconds and 12 admissions. Fixture time starts at zero.

`investigate` runs locally. `-Pbudget=2` exercises budget exhaustion. `-Pmode=simulated` adds a labeled Converse contract replay. For a real comparison install AWS CLI v2, configure its region and credentials, set `BEDROCK_MODEL_ID` to an enabled model supporting Converse, and explicitly select `-Pmode=bedrock`. Grant only `bedrock:InvokeModel` on that model ARN for direct in-region inference. The CLI investigator reads aggregate fixtures and needs no Redis connection. [AWS Converse command reference](https://docs.aws.amazon.com/cli/latest/reference/bedrock-runtime/converse.html)
