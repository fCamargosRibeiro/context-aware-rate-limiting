# Limitação de consultas por contexto

Duas réplicas Spring Boot compartilham no Redis um bucket Bucket4j por identidade autenticada. O saldo inicial e o teto são dez fichas. A cada minuto completo entra uma ficha. Uma consulta encontrada custa uma, uma inexistente custa quatro e um timeout custa uma. A confirmação de uma ordem devolve uma ficha quando a consulta encontrada pertence à identidade da ordem.

[English](README.md) · [Arquitetura C4 e recuperação](docs/architecture.md) · [Resultados por critério](docs/evidence.md)

## Executar a demonstração

Use JDK 25 e Docker com Compose. O Wrapper executa as suítes unitária, de integração e de componente durante o `build`. As duas últimas usam Testcontainers por padrão. Com um executável Redis local, defina `REDIS_TEST_BINARY` com seu caminho absoluto para executar os mesmos testes sem Docker. Os relatórios e a investigação local dispensam Redis e AWS.

Execute os comandos abaixo a partir da raiz do repositório. O Compose publica as réplicas em localhost nas portas 8081 e 8082. Após a subida, execute `curl -i http://127.0.0.1:8081/lookups` e `curl -i http://127.0.0.1:8082/lookups` (use `curl.exe` no Windows PowerShell). Aguarde até ambas responderem HTTP 405 ao GET na rota que aceita apenas POST. Os exemplos repetem a consulta na outra réplica e repetem a ordem com a chave original de cada operação. Para iniciar outra jornada, troque as duas chaves. O volume do Redis persiste após `docker compose -f config/compose.yaml down`.

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

### Linux e macOS

O exemplo HTTP usa curl e jq.

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

## Entradas e análise

Use `Bearer demo-a` e `Bearer demo-b` para duas identidades distintas. O diretório sintético aceita `missing` para inexistente e `timeout` para inconclusivo. Outros destinatários não vazios de até 128 caracteres são encontrados. Os códigos HTTP são 400 para entrada inválida, 401 para credencial inválida, 404 para referência inelegível, 409 para chave em conflito, 429 para saldo insuficiente e 503 para indisponibilidade na admissão.

Com o Compose ativo, exporte os eventos em um shell POSIX com `docker compose -f config/compose.yaml exec -T redis redis-cli --raw LRANGE journey:events 0 -1 > events.jsonl`. No PowerShell use `[IO.File]::WriteAllLines("$PWD/events.jsonl", [string[]](docker compose -f config/compose.yaml exec -T redis redis-cli --raw LRANGE journey:events 0 -1), [Text.UTF8Encoding]::new($false))`. Execute `sh gradlew -q report -Pevents=events.jsonl -Pcutoff="$(date +%s)"` ou `.\gradlew.bat -q report -Pevents=events.jsonl "-Pcutoff=$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())"`. O corte usa segundos desde a época Unix. As fixtures começam no instante zero. Os padrões de `-Pwindow`, `-Pgrace` e `-Pminimum` são 600 segundos, 120 segundos e 12 admissões.

`investigate` executa o investigador local. Use `-Pbudget=2` para observar o esgotamento do orçamento e `-Pmode=simulated` para incluir a resposta simulada do contrato Converse. Para a comparação real, configure as credenciais e a região na AWS CLI v2, defina `BEDROCK_MODEL_ID` com um modelo habilitado para Converse e selecione `-Pmode=bedrock`. Para inferência direta na região, conceda `bedrock:InvokeModel` apenas no ARN do modelo. O investigador recebe agregados sintéticos e dispensa acesso ao Redis. [Referência AWS Converse](https://docs.aws.amazon.com/cli/latest/reference/bedrock-runtime/converse.html)
