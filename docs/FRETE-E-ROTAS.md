# Frete por distância e rota de entregas

Compatível com API 0.13.1 (`ad0093049271d925aa49d899b86e58238abdb139`), usada nos testes integrados deste repositório.

Aplicar as migrations da API e configurar a chave OpenRouteService somente no servidor. No painel do gerente, Configurações → Editar operação: salvar endereço da pizzaria e exatamente cinco faixas crescentes de km/valor, escolhendo cobrança por distância. O repasse ao motoboy continua separado.

O orçamento da API calcula pelo menor trajeto viário ORS driving-car desde a pizzaria. Limites incluídos na faixa; acima do quinto limite, a entrega é bloqueada. Retirada não tem frete nem consulta a mapas. O valor é revisado antes da confirmação. Pedidos guardam distância e origem; alterações futuras não recalculam pedidos confirmados.

O aplicativo do entregador agrupa endereços, mantém apartamentos/clientes separados e ordena da menor distância da pizzaria para a maior. A rota no Maps usa a origem salva e os destinos localizados; trechos seguintes começam na última parada anterior. Pedidos antigos sem distância ficam depois, com conferência manual.

Lojas em modo fixo e contratos antigos continuam operando até a ativação. A cobertura de geocodificação depende do provedor; homologar com endereços reais e a chave antes de usar em produção. Não há chave de mapas nos APKs ou no navegador.

[Guia central com limites, contrato e ativação](https://github.com/nvpetri/APIBonamassa/blob/main/docs/FRETE-E-ROTAS.md).
