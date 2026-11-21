# Investigação somente leitura

## Objetivo

Comparar uma triagem determinística local e uma triagem assistida por Amazon Bedrock usando os mesmos alertas, evidências e limites.

## Contexto

O analista precisa decidir qual verificação fazer após um alerta de jornada. Os dois casos de MON-03 têm o mesmo volume, mas exigem investigações diferentes. A explicação deve permitir que outra pessoa confira o dado citado.

## Regras de Negócio

- O servidor fixa o escopo do caso, incluindo integrações, versões e período. As únicas fontes são métricas agregadas, saúde da jornada e histórico de deploys. Ambas as modalidades recebem o mesmo alerta e acesso às mesmas fontes.
- Validar operação e argumentos de cada consulta no servidor. Impor orçamento configurável de chamadas por caso. O investigador não possui capacidade de alterar saldo ou política nem de bloquear identidades.
- O modo padrão é determinístico e local. Bedrock exige ativação explícita. Identificar o modo e a origem sintética dos dados na saída.

## Requisitos Funcionais

Entregar, em cada caso, hipótese principal, referências às evidências, explicação alternativa, informação faltante e próxima verificação. Uma referência deve permitir localizar a fonte e o registro agregado que sustenta a afirmação. Na avaliação, conferir valores citados, contar afirmações sem suporte e medir tempo decorrido. Registrar custo e uso quando disponíveis.

## Requisitos Não Funcionais

Separar a investigação do caminho síncrono de admissão. Compartilhar apenas agregados com o modelo. A demonstração deve funcionar sem AWS. Para a extensão remota, documentar a permissão mínima para o modelo escolhido e dispensar acesso do investigador ao Redis de saldo.

## Critérios de Aceitação

| ID | Experimento | Evidência exigida |
|---|---|---|
| INV-01 | Executar a demonstração padrão sem credenciais de nuvem. | As fixtures e a triagem determinística completam localmente; a saída identifica o modo e os dados simulados. |
| INV-02 | Consultar as três fontes permitidas e tentar outra operação, outra integração ou outro período fora do caso. | Somente leituras autorizadas retornam dados. Registrar a recusa, sem revelar informações externas ao caso nem alterar estado. |
| INV-03 | Investigar os dois alertas de MON-03. | Cada relatório contém os cinco elementos exigidos. Um coteja deploy, versão e saúde da confirmação; o outro coteja persistência entre janelas e a integração de comparação. |
| INV-04 | Esgotar o orçamento de chamadas ou simular indisponibilidade do modelo. | Encerrar ou degradar explicitamente a investigação, indicando o motivo. Nenhuma chamada excedente e nenhum efeito sobre admissões e saldos. |
| INV-05 | Comparar as duas modalidades no mesmo par de casos. | Registrar acertos e total de referências verificadas, afirmações sem suporte, tempo e custo disponível. Sem credenciais, executar o contrato do adaptador com resposta simulada e identificar a comparação com o serviço real como pendente. |

## Casos de Erro

Referência inexistente ou valor incompatível conta como evidência incorreta. Uma resposta simulada não serve como medida de latência, custo ou qualidade do serviço remoto. Se uma fonte estiver indisponível, o relatório identifica a informação faltante.

## Fora de Escopo

Agente autônomo de bloqueio, ações de cobrança, decisões sobre pessoas reais e acesso do modelo a eventos individuais ou credenciais.
