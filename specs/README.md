# Especificações da POC de limitação de consultas

A POC investiga duas perguntas. Duas réplicas conseguem respeitar o mesmo orçamento por identidade? Os resultados das consultas e das ordens ajudam a explicar um aumento de volume? A política é fictícia. Todas as identidades, destinatários, ordens e implantações do experimento são sintéticos.

| Ordem | Especificação | Primeira evidência a produzir |
|---|---|---|
| 1 | [Admissão e cobrança](01-admission.md) | Duas requisições sobre a última ficha geram uma única leitura. |
| 2 | [Idempotência e recuperação](02-recovery.md) | Uma falha entre ajuste e conclusão preserva o efeito após reinício. |
| 3 | [Monitoramento](03-monitoring.md) | Volumes iguais produzem evidências diferentes sobre a jornada. |
| 4 | [Investigação](04-investigation.md) | Duas formas de triagem explicam o mesmo alerta usando fontes restritas. |

## Base comum

Usar Java 25, Spring Boot e o Gradle Wrapper do repositório, com Groovy DSL. A demonstração local usa duas instâncias da mesma aplicação em portas diferentes e um Redis compartilhado via Docker Compose. A execução padrão dispensa credenciais AWS. Usar inglês nos contratos HTTP, campos, eventos e mensagens da aplicação.

O componente de consulta coordena a autorização, a admissão e a leitura do diretório. A política é dona do saldo. O componente de ordens valida o vínculo com a consulta. Monitoramento consome eventos e investigação lê agregados. Conforme o AGENTS.md, contratos entre componentes ficam em `api/`; o armazenamento, os consumidores e o cliente Redis são internos aos componentes responsáveis.

## Como registrar evidência

Começar por ADM-03. Usar JUnit 5, relógio controlável e efeitos observáveis. Testes de componente que dependem de atomicidade devem usar Redis real com Testcontainers e clientes independentes. Conectar as suítes de integração e componente à tarefa Gradle `check`. Registrar comando, versões, contagem por suíte, resultado observado e limitações em cada etapa. Documentar a reprodução em inglês e português e as fronteiras do sistema em uma visão C4. Os números nas specs são expectativas de aceite para a nova implementação.

[Sequência de trabalho e cobertura dos critérios](tasks.md)
