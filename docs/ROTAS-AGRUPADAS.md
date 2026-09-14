# Rotas agrupadas — Entregador 0.4.0

Atualize primeiro a API da [PR #5](https://github.com/nvpetri/APIBonamassa/pull/5). A nova ação é `POST /v1/driver/routes/start`. Os endpoints individuais continuam disponíveis. A URL padrão do aplicativo continua a mesma; nenhuma configuração manual de API foi reintroduzida.

A lista agrupa pedidos por rua, número, bairro, cidade, UF e CEP. Espaços, maiúsculas e acentos são normalizados, sem adivinhar abreviações ou ruas próximas. Pedidos de apartamentos diferentes no mesmo prédio aparecem na mesma parada; cada pedido mostra seu complemento e sua cobrança. Endereços ausentes ficam separados para conferência.

“Iniciar rota com N pedidos” reúne os pedidos prontos atribuídos ao entregador, a retirar ou já retirados. Confira volumes, pagamentos e troco e marque que retirou todos. A API só inicia se todos continuarem válidos e atribuídos a essa conta. Uma falha de versão/atribuição recusa o grupo inteiro. Novas atribuições depois de abrir a conferência não são incluídas silenciosamente. Até 100 pedidos por saída.

O comando é salvo cifrado antes do envio. Se a resposta se perder, “Verificar envio” usa a mesma chave e o mesmo corpo. O app valida que a resposta contém todos os IDs e recarrega o estado atual antes de novas ações. Finalização, cobrança, recebedor e devolução continuam individuais.

Depois da saída, use “Abrir rota no Google Maps”. A sequência segue o pedido mais antigo de cada endereço, sem otimização por trânsito/distância. Links grandes ou com mais de quatro destinos aparecem em trechos explícitos, respeitando até três waypoints no navegador móvel e 2.048 caracteres. Nenhum destino é ocultado. Verifique a sequência antes de dirigir. O Waze individual permanece no detalhe. [Limites do Maps](https://developers.google.com/maps/documentation/urls/get-started).

Validação automatizada: testes JVM para agrupamento sem mistura de números/cidades, apartamentos preservados, URLs/trechos, persistência e repetição do lote; teste instrumentado cria três pedidos em dois endereços, exige conferência, inicia todos, confere painel, conclui só um e recupera um lote após perda da resposta. API/PostgreSQL são descartáveis, separados da loja pública.

`versionCode=4`. Instale com a mesma assinatura para preservar a sessão e os envios pendentes. Leia também [PRODUCAO.md](PRODUCAO.md) e o [contrato e roteiro completo](https://github.com/nvpetri/APIBonamassa/blob/codex/cep-sacola-rotas/docs/CEP-SACOLA-ROTAS.md).
