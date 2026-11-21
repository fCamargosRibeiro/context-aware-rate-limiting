# Monitoramento da jornada

## Objetivo

Identificar por que o volume de consultas deixou de se converter em ordens e fornecer evidências reproduzíveis para investigação.

## Contexto

Uma regressão na confirmação pode afetar várias integrações após um deploy. Uma única integração também pode fazer consultas por várias janelas sem confirmar ordens. Volume isolado não distingue esses casos.

## Regras de Negócio

- Agrupar por integração, versão e janela de admissão. Atribuir resultados e ordens à consulta de origem. Deduplicar reentregas antes da agregação.
- Contar consultas admitidas, encontradas, inexistentes, timeouts, erros de confirmação, ordens e recusas por limite. A fração de inexistentes usa apenas encontradas mais inexistentes no denominador; a razão consulta/ordem usa consultas admitidas.
- Com denominador zero, exibir contagens e razão indisponível. Somente interpretar ausência de ordem após o prazo esperado de conclusão e com amostra mínima configurável. Comparar pelo menos duas janelas maduras.

## Requisitos Funcionais

Emitir eventos estruturados de decisão, resultado e ordem com IDs opacos, instante, integração, versão e categoria de resultado. Gerar em Java um relatório local a partir de fixtures determinísticas ou de exportação JSON Lines. Tornar explícitos o instante de corte, o prazo de conclusão e a amostra mínima usados. Como parâmetros de referência para reproduzir as fixtures, usar janelas de 600 segundos, prazo de 120 segundos e mínimo de 12 consultas.

## Requisitos Não Funcionais

Usar dados sintéticos e dimensões de métricas de cardinalidade limitada. Repetir a análise com as mesmas entradas e o mesmo corte deve produzir as mesmas contagens. Separar medidas observadas, expectativas calculadas e hipóteses ainda sem validação.

## Critérios de Aceitação

| ID | Experimento | Evidência exigida |
|---|---|---|
| MON-01 | Inserir 11 consultas admitidas, oito encontradas, duas inexistentes, um timeout, quatro ordens e uma recusa. Reentregar uma ordem. | Mostrar 11, 8, 2, 1, 4 e 1 nas respectivas contagens; fração 2/10 e razão 11/4. A recusa não conta como leitura. |
| MON-02 | Exercitar zero ordens, amostra abaixo do mínimo, janela imatura e ordem recebida na janela seguinte dentro do prazo. | Mostrar contagens e razão indisponível para zero ordens. Identificar insuficiência de amostra ou aguardar maturidade. Atribuir a ordem atrasada à coorte original uma única vez. |
| MON-03 | Gerar dois casos de 144 consultas cada, com duas integrações, três janelas e 24 consultas por integração por janela. No primeiro, a nova versão falha ao confirmar em ambas as integrações. No segundo, uma integração fica sem ordens nas duas últimas janelas enquanto a outra mantém o padrão anterior. | Volumes iguais. Mostrar queda de ordens e erros de confirmação por versão no primeiro caso e persistência concentrada no segundo. Uso indevido permanece hipótese a verificar. |
| MON-04 | Incluir pico legítimo e dados de diretório desatualizados. Reproduzir cada fixture sob regra fixa de volume, política ponderada e política ponderada acompanhada de triagem. | Usar a mesma entrada e registrar parâmetros. Comparar leituras admitidas, bloqueios indevidos segundo rótulos sintéticos, latência do limitador e tempo até alerta quando aplicável. A triagem é somente leitura. |
| MON-05 | Inspecionar eventos e relatórios das quatro fixtures. | Operações correlacionáveis por IDs opacos. Nenhuma credencial, chave de destinatário, nome ou telefone em logs ou relatórios. Nenhum ID individual como dimensão de métrica. |

## Casos de Erro

Timeout e erro de confirmação são categorias técnicas distintas. Uma consulta recusada não entra no denominador de resultados conclusivos. Dados insuficientes ou uma janela ainda aberta não autorizam classificar abandono.

## Fora de Escopo

Diagnóstico automático de fraude, ingestão de dados reais e bloqueio de identidades a partir do relatório.
