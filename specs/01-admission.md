# Admissão e cobrança por resultado

## Objetivo

Garantir que cada consulta autorizada reserve uma ficha antes de ler o diretório, inclusive quando duas instâncias disputam o mesmo saldo.

## Contexto

Um cliente consulta um destinatário e pode confirmar uma ordem fictícia. A política distingue uma consulta útil, uma busca sem resultado e uma falha técnica. O orçamento pertence à identidade obtida da credencial pelo servidor.

## Regras de Negócio

- Capacidade inicial e máxima de dez fichas. Repor uma ficha a cada minuto completo. As réplicas compartilham o mesmo bucket da identidade.
- Exigir uma ficha para admitir. Resultado encontrado mantém a reserva. Resultado inexistente custa quatro no total, com ajuste adicional de três após a leitura. Timeout consome somente a reserva e produz resultado inconclusivo.
- O ajuste pode gerar dívida. Com saldo inferior a um, recusar antes de ler. Uma ordem confirmada referente a uma consulta encontrada da mesma identidade devolve uma ficha até o teto.

## Requisitos Funcionais

| Operação | Entrada | Resultado observável |
|---|---|---|
| `POST /lookups` | Credencial, `Idempotency-Key`, corpo com `recipientId` sintético | Na admissão bem-sucedida, HTTP 200 com `lookupId` opaco e `outcome` igual a `found`, `missing` ou `inconclusive`. Persistir o resultado correlacionado. |
| `POST /payment-orders` | Credencial, chave de idempotência da ordem e `lookupId` | HTTP 200 com `paymentOrderId` e `lookupId` para uma confirmação válida. As regras de repetição estão em REC-01 a REC-03. |

## Requisitos Não Funcionais

Usar Bucket4j com integração Redis distribuída. Verificar o módulo, cliente e assinaturas compatíveis ao fixar versões. No teste de disputa, contar leituras na fronteira do diretório por instância e consultar o saldo compartilhado ao final. Congelar o relógio nos cenários de custo.

## Critérios de Aceitação

| ID | Dado / Quando | Então |
|---|---|---|
| ADM-01 | Em cenários independentes, partir de dez e consultar uma chave encontrada ou provocar timeout. | Uma leitura em cada caso, saldo nove em ambos. Persistir respectivamente `found` e `inconclusive`; contar o timeout como erro técnico. |
| ADM-02 | Partir de dez e consultar um destinatário inexistente. | Persistir `missing`, fazer uma leitura e terminar em seis. |
| ADM-03 | Com exatamente uma ficha e relógio parado, liberar simultaneamente duas consultas distintas, uma por instância, para a mesma identidade, com chaves de idempotência diferentes. | Uma resposta 200 e outra 429, em qualquer ordem. O diretório vencedor recebe somente o destinatário admitido; o outro registra zero leituras. Saldo final zero. |
| ADM-04 | Partir de uma ficha e admitir uma consulta inexistente. Avançar o relógio em minutos completos. | Saldo −3 após o resultado. Tentativas com chaves novas recebem 429 nos saldos −3, −2, −1 e 0. No quarto minuto uma consulta encontrada é admitida e deixa zero. |
| ADM-05 | Indisponibilizar o Redis na admissão. Em cenário separado, enviar credencial ausente ou inválida. | Respectivamente 503 e 401. Zero leituras em ambos. Na indisponibilidade anterior à reserva, verificar saldo anterior após restabelecer o Redis. |
| ADM-06 | Confirmar ordem para consulta própria encontrada, com espaço no bucket; repetir o cenário com bucket cheio. Tentar também consultar ordem por referência alheia ou inelegível. | O primeiro crédito aumenta o saldo em um; o segundo cenário preserva dez. Referências alheias, inexistentes ou inconclusivas recebem 404 sem criar ordem nem conceder crédito. |

## Casos de Erro

Campo obrigatório ou chave de idempotência ausente gera 400. Rejeições de validação e autorização precedem efeitos de negócio. Responder 429 para falta de saldo e 503 para indisponibilidade de admissão. Quando a reserva pode ter sido efetivada antes de perder a resposta, aplicar REC-05.

## Fora de Escopo

Pagamentos reais, rede financeira, chaves pessoais e política regulatória. Um orçamento agregado por organização é extensão futura e exigiria admissão conjunta entre orçamentos.
