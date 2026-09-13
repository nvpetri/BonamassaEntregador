# Integração do entregador

Este repositório contém apenas o Android do motoboy e seu módulo de regras JVM. O aplicativo cliente está em outro repositório. Nenhum arquivo, banco local ou processo é compartilhado entre os dois apps.

## Fronteira desta demonstração

`LocalDriverRepository` grava o estado em DataStore com um codec JSON de schema 1. As três entregas vêm de `DriverDemo`; a disponibilidade e os eventos não são enviados ao restaurante. `DriverRepository` define a entrada de persistência, mas a interface atual de transformação local deve ser substituída por comandos autenticados ao introduzir uma API.

O app não solicita permissões de Internet, chamada telefônica ou localização. Os mapas abrem por ação do usuário em outro aplicativo ou navegador. O discador usa `ACTION_DIAL`, que permite ao usuário decidir se inicia a chamada; os fixtures não contêm telefones reais.

## Fluxo implementado

| Etapa | Próxima ação permitida |
|---|---|
| A retirar | Confirmar retirada estando disponível para coletas |
| Retirado | Iniciar entrega |
| Em rota | Confirmar entrega ou registrar tentativa sem sucesso |
| Retornar à pizzaria | Confirmar devolução |
| Entregue / Devolvido | Consultar o histórico |

Ao pausar novas coletas, pedidos já retirados podem ser iniciados e concluídos. Vários pedidos podem ficar em rota simultaneamente. Conclusões duplicadas e saltos de etapa são rejeitados. Uma devolução não conta como entrega concluída nem soma taxa nesta demonstração; essa regra comercial precisa ser aprovada.

Para dinheiro e cartão, o entregador deve confirmar o recebimento e informar o destinatário. `paymentCollected` é um registro operacional local, não uma comprovação de transação financeira. No pagamento antecipado, `PREPAID` representa uma informação que deverá vir do servidor.

## Contrato sugerido para o backend

| Operação | Proposta |
|---|---|
| Autenticação do entregador | Sessão criada pelo servidor e associada ao estabelecimento |
| Listar atribuídas | `GET /driver/deliveries` |
| Detalhar uma entrega autorizada | `GET /driver/deliveries/{id}` |
| Atualizar disponibilidade | `PATCH /driver/availability` |
| Registrar retirada | `POST /driver/deliveries/{id}/collect` |
| Iniciar percurso | `POST /driver/deliveries/{id}/start` |
| Registrar entrega | `POST /driver/deliveries/{id}/complete` |
| Registrar tentativa sem sucesso | `POST /driver/deliveries/{id}/issue` |
| Confirmar devolução | `POST /driver/deliveries/{id}/return` |

Cada comando deverá carregar um identificador de idempotência e a versão conhecida da entrega. O servidor precisa conferir estabelecimento, entregador atribuído, versão e transição permitida antes de gravar o evento. Valores, pagamento antecipado e permissões são validados no servidor. Após o aceite, a resposta canônica atualiza o app e o painel da pizzaria; alterações simultâneas precisam de reconciliação, inclusive cancelamentos e reatribuições.

Para uso com conexão instável, implementar uma fila de comandos pendentes e indicar quais ações ainda não foram confirmadas pelo servidor. O DataStore atual não é uma fila de sincronização. Tokens, dados reais de clientes e políticas de retenção ainda precisam de implementação antes de substituir os exemplos.

## A desenvolver para operação real

- Autenticação, atribuição dos pedidos pelo painel e revogação de acesso.
- API, envio e recebimento de eventos, reconexão e atualização do histórico entre aparelhos.
- Notificações de novas entregas.
- Endereço real da pizzaria e contato com a equipe.
- Confirmação de pagamento pelo sistema responsável e política de prestação de contas.
- Rastreamento, se contratado: consentimento, permissões, serviço em primeiro plano, limites de atualização e controle de acesso à localização.
- Tratamento de cancelamentos/reentregas e aprovação das regras comerciais.
- Testes em aparelhos, distribuição do APK e assinatura de produção sob controle do proprietário.

Mapas usam links externos; não há mapa embutido, cálculo de distância ou ETA no aplicativo. O endereço é codificado como parâmetro e não vira uma URL arbitrária. Referências: [Maps URLs](https://developers.google.com/maps/documentation/urls/get-started), [Waze Deep Links](https://developers.google.com/waze/deeplinks) e [intents Android](https://developer.android.com/training/basics/intents/sending).
