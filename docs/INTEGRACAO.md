# Integração com APIBonamassa

Cliente compatível com o contrato da API em `e2e3491741091b522272ebe3e43272a232fc701a`, a mesma revisão usada pelo painel e Android cliente nos testes integrados.

| Operação | Endpoint |
|---|---|
| Entrar / sair | `POST /v1/sessions`, `DELETE /v1/sessions/current` |
| Confirmar e-mail / recuperar senha | `POST /v1/auth/email-verification/request`, `.../confirm`, `POST /v1/auth/password-reset/request`, `.../confirm` |
| Perfil e disponibilidade atual | `GET /v1/me` |
| Alterar disponibilidade | `PATCH /v1/driver/availability` |
| Lista paginada / detalhe | `GET /v1/driver/deliveries`, `GET /v1/driver/deliveries/{id}` |
| Retirar / iniciar | `POST /v1/driver/deliveries/{id}/collect`, `.../start` |
| Concluir / tentativa sem sucesso / devolver | `POST .../complete`, `.../issue`, `.../return` |

Toda alteração operacional carrega `expectedVersion` e `Idempotency-Key`. O cliente não envia total, taxa, identidade de outro entregador ou alterações arbitrárias de status. A API valida papel, loja, atribuição e transição. Disponibilidade retorna apenas `id`, `available` e `version`; o perfil completo vem de `/v1/me`.

## Etapas

| Estado recebido | Ação |
|---|---|
| `READY` + `ASSIGNED` | Retirar, estando disponível |
| `READY` + `COLLECTED` | Iniciar entrega |
| `OUT_FOR_DELIVERY` + `ON_ROUTE` | Concluir ou registrar tentativa sem sucesso |
| `RETURNING` + `RETURNING` | Confirmar devolução física à pizzaria |
| `DELIVERED` / `RETURNED` / `CANCELLED` | Consulta |

A conclusão exige nome do recebedor. Quando `paymentRecorded=false` e `total>0`, exige confirmação explícita de pagamento. Um pagamento já registrado pela pizzaria não é cobrado novamente. Devoluções não somam taxa de entrega concluída, conforme a regra atual do backend.

## Falhas e reconciliação

No primeiro acesso, **Confirmar meu e-mail** solicita o código e abre a confirmação. A sessão renova a cada acesso autenticado e expira após cinco dias sem uso. Recuperar a senha invalida todas as sessões anteriores; o aplicativo usa o mesmo contrato que o painel e o cliente.

`client/` contém contrato, modelos, validações e codec sem dependências Android. `app/connected/` gerencia a sessão e as telas. Os arquivos do modo demo permanecem separados.

`SecureStore` grava uma transação AES-256-GCM em `noBackupFilesDir` usando chave Android Keystore e `AtomicFile`. O comando pendente é vinculado à origem, loja e usuário; método PATCH/POST, corpo e chave permanecem idênticos em todas as tentativas. A senha não é persistida.

A aplicação não confirma a ação localmente antes da resposta. Falhas de transporte, 408, 429, 5xx, resposta inválida e conflito de chave preservam o envio. Uma rejeição definitiva de negócio libera o comando e exige atualização antes de outra ação. Uma sessão expirada preserva o envio para retomar após login na mesma conta. Dados locais ilegíveis apresentam erro, sem reset silencioso.

Uma resposta de idempotência pode ser antiga. A tela combina versões monotonicamente e busca novamente o estado canônico antes de habilitar novos comandos. O refresh percorre todas as páginas de estados ativos; a primeira página do histórico não pode esconder uma atribuição antiga. Pedidos antes conhecidos são verificados individualmente para detectar conclusão, reatribuição ou cancelamento. Um 404 remove o pedido e fecha seus detalhes.

Polling funciona com a tela visível (5s; 15s após erro), sem simular GPS/ETA. O histórico é paginado; as taxas exibidas são a soma dos registros carregados. Rotas usam o endereço textual da API e abrem aplicações externas, sem chave de mapas. Ao retornar, a tela orienta entregar o pedido à equipe; a API atual não fornece endereço da loja para navegação de retorno.

## Próximas integrações

Push, rastreamento em segundo plano, endereço/contato da loja, fechamento de turno e repasses dependem de novos contratos e requisitos. A conta de entregador é criada pela gerência no painel; não existe cadastro público que promova um cliente a funcionário.
