# Idempotência e recuperação

## Objetivo

Permitir repetição segura de requisições, resultados e confirmações, inclusive quando um processo cai entre o efeito no saldo e o registro da conclusão.

## Contexto

Uma resposta HTTP pode se perder após a reserva. Um evento pode chegar atrasado ou ser entregue em paralelo. A chave da tentativa de entrega e a identidade do efeito de negócio têm papéis diferentes.

## Regras de Negócio

- Vincular a chave HTTP à identidade autenticada, à operação e ao conteúdo de negócio. Um retry conserva a chave e o corpo. Operações novas usam chaves próprias.
- Um resultado de consulta pode gerar um único ajuste. Uma consulta elegível pode originar uma única confirmação creditável. Usar os IDs de negócio para reconhecer redelivery mesmo com outro ID de entrega.
- Persistir IDs de operação e correlação, resultado e evidência dos ajustes em ordem. Ao encontrar um ajuste incerto, suspender novas admissões daquela identidade até conciliar. Identidades independentes continuam operando.
- A conciliação considera dívida, teto, reposições e ordem temporal. Um registro de intenção isolado é insuficiente para provar que o efeito ocorreu.

## Requisitos Funcionais

Devolver a resposta previamente concluída no retry válido. Disponibilizar processamento de eventos que possa ser reexecutado sem leitura duplicada do diretório, nova ordem ou segundo ajuste. Após reinício, distinguir efeito comprovadamente aplicado, comprovadamente ausente e incerto. Somente liberar a identidade quando a evidência permitir determinar seu saldo.

## Requisitos Não Funcionais

O estado publicado do bucket e o recibo de cada movimento devem ser confirmados juntos no Redis. A conclusão do consumidor é posterior. A retomada usa a correlação e o recibo persistidos. O diretório sintético suporta leitura idempotente pelo ID da consulta.

Documentar a fronteira atômica escolhida e o que permanece durável após o reinício do consumidor. Escritas em Redis e em outro banco não constituem uma transação conjunta por padrão. Injetar falhas em pontos identificáveis e executar testes com estado persistido entre processos ou contextos. Descrever o alcance da garantia demonstrada.

## Critérios de Aceitação

| ID | Experimento | Evidência exigida |
|---|---|---|
| REC-01 | Repetir consulta e ordem concluídas após descartar a resposta. Reutilizar também cada chave com conteúdo diferente. | Mesmo status, conteúdo e IDs no retry válido. HTTP 409 no conflito. Uma leitura, uma ordem e somente os movimentos previstos para a jornada. |
| REC-02 | Após uma consulta inexistente reduzir dez para seis, reentregar seu resultado em sequência e em paralelo com relógio parado. | Saldo seis, uma leitura e um único ajuste adicional de três no histórico. |
| REC-03 | Entregar duas confirmações da mesma ordem, inclusive concorrentes e após reinício. Começar com saldo oito e espaço para observar um crédito duplo. | Saldo nove, um ID de ordem e um crédito, mesmo que as entregas tenham IDs distintos. |
| REC-04 | Simular queda após aplicar o ajuste e antes de gravar sua conclusão. Reiniciar o consumidor mantendo o armazenamento. Avançar o relógio em dois minutos depois de um débito que deixou seis. | Demonstrar a evidência que permitiu concluir o ajuste uma vez; saldo oito após reposição. Enquanto houver incerteza, uma nova consulta não chega ao diretório. Após conciliar, a identidade volta a operar. |
| REC-05 | Atrasar um resultado, perder a resposta após a reserva e repetir a operação original. | Recuperar sua correlação, verificar o saldo real e contar as leituras. Uma reserva já efetuada não é refeita. Um resultado tardio compatível não altera novamente um efeito concluído. |

## Casos de Erro

Conteúdo divergente para o mesmo efeito deve ser rejeitado e registrado sem movimento financeiro fictício. Com evidência insuficiente para conciliar, manter o bloqueio conservador da identidade e registrar a pendência. Uma reentrega não pode conceder crédito para consulta alheia.

## Fora de Escopo

Garantia universal de processamento exatamente uma vez, recuperação após perda integral de todos os registros e coordenação entre regiões.
