# Revisão e evidências de execução

**Revisão local aprovada.** Em 21/11/2026, o Wrapper concluiu `clean build report investigate -Pmode=simulated --offline` em 1 min 3 s. O `check` executou as três suítes. O ambiente usou Windows, OpenJDK 25.0.2, Gradle 9.9.2, Spring Boot 3.5.7, JUnit 5, Bucket4j Jedis 8.15.0, Jedis 5.2.0 e Redis 5.0.14.1. A versão do Spring foi escolhida para manter uma linha com JUnit 5, e os módulos do Bucket4j foram alinhados na mesma versão. O build inicial havia parado na resolução offline do núcleo 8.23.0.

| Tarefa Gradle | Testes descobertos | Falhas / erros / ignorados |
|---|---:|---:|
| `test` | 8, incluindo o smoke test Spring original | 0 / 0 / 0 |
| `integrationTest` | 3 | 0 / 0 / 0 |
| `componentTest` | 4 | 0 / 0 / 0 |

Os XMLs por classe ficam em `build/test-results/<tarefa>/`. Para reproduzir sem Docker, aponte `REDIS_TEST_BINARY` para um executável Redis local. Nesta execução foi utilizado o binário Windows distribuído no artefato Maven `com.github.codemonstur:embedded-redis:1.4.3`. Cada caso abre um Redis isolado com diretório temporário. O build com o cache inicialmente vazio requer rede.

## Etapa 1 — Admissão

| Critério | Teste executado | Comportamento observado | Alcance |
|---|---|---|---|
| ADM-03 | LastTokenTest | Uma ficha inicial, relógio parado. Réplica A retornou 200 e leu apenas seu destinatário. B retornou 429 com zero leituras. Saldo final zero. | Duas portas e clientes independentes em dois contextos Spring na mesma JVM de teste. |
| ADM-01 | AdmissionTest, LookupControllerTest | Encontrado e timeout custaram uma leitura e deixaram nove em identidades inicialmente cheias. O timeout foi registrado como inconclusivo. | Timeout sintético na fronteira do diretório. |
| ADM-02 | AdmissionTest, RecoveryTest | O inexistente reservou uma ficha e recebeu o débito adicional de três. Saldo seis e um único resultado. | Relógio controlado. |
| ADM-04 | AdmissionTest | Quatro recusas sucessivas com saldos −3, −2, −1 e 0. No quarto minuto a admissão deixou zero. | Reposição por minuto completo. |
| ADM-05 | AdmissionTest | Redis pausado durante a chamada retornou 503. Credenciais ausente e inválida retornaram 401. Zero leituras adicionais, saldo nove após retorno do Redis. | Indisponibilidade anterior à reserva. |
| ADM-06 | AdmissionTest, RecoveryTest | Apenas consulta própria encontrada permitiu a ordem. Crédito de oito para nove e teto dez. Referências alheia, desconhecida, inexistente e inconclusiva receberam 404. | Ordem fictícia. |

O JAR gerado também foi executado em **duas JVMs** nas portas 58518 e 58521 com Redis compartilhado. A segunda réplica devolveu o mesmo ID de consulta e o retry da ordem na primeira preservou o ID da ordem. O teste automatizado com a barreira de simultaneidade fornece a evidência específica da última ficha.

## Etapa 2 — Recuperação

| Critério | Teste executado | Comportamento observado | Alcance |
|---|---|---|---|
| REC-01 | RecoveryTest | Consultas duplicadas nas duas réplicas devolveram o mesmo resultado. Retries de consulta e ordem preservaram IDs. Conteúdo divergente recebeu 409. | Chave por identidade e operação. |
| REC-02 | RecoveryTest | Reentregas seriais e paralelas do inexistente mantiveram seis. Uma leitura e dois eventos no histórico, admissão e resultado. | Um efeito de resultado por lookup ID. |
| REC-03 | RecoveryTest | Duas confirmações concorrentes com chaves distintas partiram de oito. Após retry e reinício, nove fichas, um ID de ordem e um evento de ordem. | Havia espaço para expor um segundo crédito. |
| REC-04 | RecoveryTest | Falha após o commit do débito deixou seis. Nova consulta recebeu 503 sem leitura e a outra identidade operou. Após reinício, duas reposições e redelivery, saldo oito, recibo concluído e um único evento de débito. A próxima consulta deixou sete. | A falha é uma exceção injetada, seguida do fechamento e recriação do contexto. O Redis permaneceu ativo. |
| REC-05 | RecoveryTest | A reserva foi efetivada antes da retomada HTTP. O retry encontrou a correlação original e o resultado tardio preservou uma leitura e o saldo seis. | O teste descarta o retorno da reserva no chamador. |

A fronteira comprovada é o EXEC que publica a imagem do bucket, o recibo e o evento. A conclusão posterior do consumidor pode falhar sem reaplicar o débito na reentrega. A cobertura de recuperação pressupõe Redis preservado e leitura idempotente no diretório. [Sequência e limites](architecture.md)

## Etapa 3 — Monitoramento

| Critério | Teste ou comando | Comportamento observado | Alcance |
|---|---|---|---|
| MON-01 | MonitoringTest, relatório JSON Lines | 11 admitidas, oito encontradas, duas inexistentes, um timeout, quatro ordens, uma recusa. Fração 0,2 e razão 2,75 após duplicar uma ordem. | Contagens observadas sobre entrada conhecida. |
| MON-02 | MonitoringTest | No corte de 600 s havia zero ordens e razão indisponível. No corte maduro as ordens recebidas aos 610 s pertenciam à coorte inicial, inclusive com versão posterior. Onze admissões ficaram abaixo do mínimo 12. | Janela 600 s, prazo 120 s. |
| MON-03 | `report`, MonitoringTest, InvestigationTest | Nos dois casos houve 144 admissões. Após o deploy, ambas as integrações passaram de 16 ordens para zero e 16 erros por janela. No caso concentrado, alpha teve zero ordens por duas janelas e beta manteve 16, sem erros de confirmação. | Associação com deploy e concentração orientam investigação. O propósito das consultas requer verificação. |
| MON-04 | `report`, MonitoringTest | Quatro fixtures e três políticas com entradas idênticas por fixture. Resultados abaixo. | Replay Java local. |
| MON-05 | MonitoringTest e inspeção do esquema de eventos | Eventos das fixtures com IDs opacos. O relatório exportado contém agregados por integração, versão e janela. | Verificação dos eventos e relatórios sintéticos. |

**Entradas definidas nas fixtures.** Cada cenário contém duas integrações, três janelas e 24 consultas por integração por janela. O cenário concentrado deixa sem rótulo de legitimidade as tentativas de alpha nas duas últimas janelas. O pico acelera a chegada das consultas e o diretório desatualizado torna metade dos resultados inexistentes. Os rótulos servem de referência para contar bloqueios indevidos.

**Medidas de uma execução.** Cada célula contém leituras admitidas / bloqueios de tentativas rotuladas legítimas / tempo médio por chamada ao limitador em nanossegundos, arredondado. O relógio monotônico mede chamadas de Bucket4j dentro da JVM. Redis, HTTP e investigação ficam fora desse intervalo.

| Fixture | Fixa | Ponderada | Ponderada com triagem |
|---|---:|---:|---:|
| Falha após deploy | 78 / 66 / 2193 | 106 / 38 / 1602 | 106 / 38 / 930 |
| Ausência concentrada | 78 / 38 / 537 | 125 / 0 / 453 | 125 / 0 / 580 |
| Pico legítimo | 60 / 84 / 157 | 144 / 0 / 344 | 144 / 0 / 340 |
| Diretório desatualizado | 78 / 66 / 299 | 32 / 112 / 519 | 32 / 112 / 333 |

Com o mínimo de 12, nenhuma das sequências ponderadas gerou duas janelas consecutivas elegíveis para o alerta. Ao executar `report -Pminimum=8`, o alerta ocorreu no segundo 1920 nos casos de deploy e concentração. Pico e diretório desatualizado continuaram sem alerta. Esses tempos são relativos ao início da fixture. A triagem preservou a quantidade de admissões. As durações de uma execução local variam com a JVM e o host.

## Etapa 4 — Investigação

| Critério | Teste ou comando | Comportamento observado | Alcance |
|---|---|---|---|
| INV-01 | `investigate` | Os dois alertas receberam triagem local sobre dados identificados como sintéticos. | Sem dependência de nuvem. |
| INV-02 | InvestigationTest | Fonte de escrita, integração, versão e período fora do caso foram recusados. A leitura permitida devolveu apenas o registro solicitado. | Leitor em processo com três operações de leitura. |
| INV-03 | InvestigationTest, `investigate` | Os dois relatórios contêm hipótese, evidências localizáveis, alternativa, lacuna e próxima verificação. | Hipóteses delimitadas aos dois padrões experimentais. |
| INV-04 | InvestigationTest, `investigate -Pbudget=2` | Ambos os casos encerraram com `Read budget exhausted`. O transporte indisponível recebeu só uma chamada. | Falha de modelo injetada. Investigador sem cliente Redis. |
| INV-05 | `investigate -Pmode=simulated` | Mesmos alertas, fontes e orçamento. Valores incorretos e referências ausentes falharam no avaliador. | Contrato Converse com envelope roteirizado. Comparação real pendente. |

| Caso | Local, referências corretas / total | Tempo | Adaptador simulado, corretas / total | Tempo local do adaptador | Afirmações sem suporte |
|---|---:|---:|---:|---:|---:|
| Deploy | 16 / 16 | 7,46 ms | 12 / 12 | 43,12 ms | 0 em ambos |
| Concentração | 14 / 14 | 0,66 ms | 7 / 7 | 1,94 ms | 0 em ambos |

Para o deploy, a alternativa é uma falha simultânea de dependência. Faltam a saúde dessa dependência e o comportamento após rollback. A próxima verificação é comparar os traces de confirmação. Para a concentração, uma jornada legítima de consulta ou interrupção do cliente também explicaria a ausência de ordens. Faltam intenção e tentativas de confirmação no cliente. Conferir a telemetria desse cliente antecede qualquer hipótese de uso indevido.

## Verificações externas pendentes

- `docker compose up -d --build` e `docker compose config` dependem do Docker, ausente neste Windows. As imagens, a rede e o volume da demonstração, assim como a inicialização padrão pelo Testcontainers, permanecem pendentes. O caminho alternativo executou Redis real, as mesmas suítes e o JAR em duas JVMs.
- `investigate -Pmode=bedrock` requer AWS CLI v2, região, modelo e credenciais habilitadas. A CLI estava ausente. Latência, uso de tokens, custo e qualidade de um modelo real permanecem pendentes.
- O Wrapper reportou uso de APIs obsoletas no build. A compatibilidade com Gradle 10 está fora da execução verificada.
