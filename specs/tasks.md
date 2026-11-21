# Roteiro para iniciar a POC

Resultados da implementação, evidências por tarefa e verificações externas pendentes estão em [docs/evidence.md](../docs/evidence.md). Os critérios abaixo apontam para a especificação que define a evidência de conclusão.

| Ordem | Tarefa | Critérios | Verificação a registrar |
|---|---|---|---|
| 1 | Preparar as duas instâncias, Redis compartilhado e a primeira fatia de consulta autenticada. Escrever primeiro a disputa pela última ficha. | [ADM-03](01-admission.md) | JUnit de componente com duas requisições simultâneas, credencial comum, chaves distintas, status por instância, saldo e leitura por destinatário. |
| 2 | Implementar a reserva, o resultado, a dívida, as reposições, o crédito e as rejeições. | [ADM-01, ADM-02, ADM-04, ADM-05, ADM-06](01-admission.md) | Testes unitários com relógio controlado e testes de integração com Redis. Isolar o saldo inicial em cada cenário. |
| 3 | Persistir operações, definir a fronteira atômica e deduplicar os efeitos HTTP e de eventos. | [REC-01, REC-02, REC-03](02-recovery.md) | Repetições seriais e concorrentes, conflito de conteúdo, contagem de efeitos e saldo final. |
| 4 | Exercitar falha entre escritas, resultado tardio e reinício. | [REC-04, REC-05](02-recovery.md) | Interromper no ponto definido, preservar armazenamento, conciliar, verificar refil, leituras e retomada. |
| 5 | Produzir eventos, agregar por coorte e gerar relatório local. | [MON-01, MON-02, MON-05](03-monitoring.md) | Fixture numérica, reentrega, corte temporal, ausência de ordens, amostra insuficiente e inspeção dos campos emitidos. |
| 6 | Executar as quatro fixtures e comparar as três políticas experimentais. | [MON-03, MON-04](03-monitoring.md) | Relatório com parâmetros, janelas comparáveis, indicadores observados e hipóteses identificadas. |
| 7 | Implementar a investigação local e restringir as três fontes ao caso. | [INV-01, INV-02, INV-03](04-investigation.md) | Rodar sem AWS, verificar referências e rejeitar solicitações fora de escopo. |
| 8 | Validar contrato, orçamento e comparação opcional com Bedrock. | [INV-04, INV-05](04-investigation.md) | Injetar indisponibilidade, medir tempo, conferir evidências e distinguir execução real de simulação. |
| 9 | Revisar a correspondência entre especificações, implementação e testes. | [Índice das quatro etapas](README.md) | Executar o Gradle Wrapper e a demonstração com Compose; registrar contagens por suíte, lacunas e limitações. Publicar início rápido bilíngue, visão C4 e orientações de implantação opcional. |

Executar os testes de cada etapa antes de avançar. Usar os diretórios de suíte e as convenções do AGENTS.md. Guardar a medição real junto ao comando que a reproduz.
