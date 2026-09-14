# Bonamassa Entregador

App Android do motoboy conectado à [APIBonamassa](https://github.com/nvpetri/APIBonamassa), com a identidade Bonamassa em preto, vermelho e dourado. Projeto independente do app do cliente; os dois podem ser instalados juntos.

## Testar com a pizzaria

1. Confirme que a API hospedada e a loja estão abertas no painel.
2. No painel, cadastre uma conta de funcionário com perfil **Entregador**, e-mail e senha. Contas de cliente/gerência não entram neste app.
3. Abra este projeto no Android Studio. Selecione um **JDK completo 17 ou 21**, SDK Android 35 e execute **Sync Project with Gradle Files**.
4. Execute `app` em aparelho Android 8.0 ou superior. O aplicativo já abre conectado a `https://bonamassa-api.onrender.com`, na loja `bonamassa`; não há configuração de servidor na interface.
5. Entre com a conta do entregador e ative **Disponível para coletas**.
6. Faça um pedido de entrega pelo app cliente. No painel, aceite, prepare, marque como pronto e atribua ao entregador.
7. No app de entregas: **Confirmar retirada → Retirei o pedido → Iniciar entrega → Sair para entrega**. Abra Maps/Waze se precisar de navegação.
8. Em **Confirmar entrega**, informe quem recebeu e confirme o recebimento do dinheiro/cartão quando solicitado. A conclusão aparecerá no painel e no app cliente.

Se a primeira conexão demorar, abra `https://bonamassa-api.onrender.com/v1/health` no navegador. A instância gratuita pode levar alguns segundos para despertar após um período sem uso.

## Operação conectada

- Login exclusivo de entregador, sessão protegida no Android Keystore e saída da conta.
- Disponibilidade compartilhada com o painel. Pausar novas coletas mantém as operações de pedidos já retirados.
- Fila, filtros, detalhes, itens de combos, observações, telefone, endereço e referência vindos da API.
- Retirada, início, entrega, tentativa sem sucesso com motivo e devolução à pizzaria.
- Total, forma de pagamento, troco, taxa prevista e taxa registrada informados pelo servidor. Recebimento é um registro operacional, sem cobrança por gateway.
- Histórico paginado. A soma de taxas considera somente o histórico carregado e não representa confirmação de repasse.
- Atualização a cada 5 segundos com o app visível, com intervalo maior após falha. Reatribuições e cancelamentos removem pedidos que deixaram de pertencer à conta.
- Envio persistido antes da requisição: **Verificar envio** repete a mesma chave, corpo, método e versão. Fechar o app não descarta uma confirmação pendente.
- Sessão expirada exige novo login na conta original quando há envio pendente. Trocar de servidor ou sair fica bloqueado até resolver esse envio.

Não há avanço automático de status nem fallback para pedidos fictícios quando a conexão falha. Endereços e histórico ficam em memória; sessão e comando pendente ficam criptografados e excluídos de backups.

## Compilar

Windows:

```powershell
.\gradlew.bat :core:test :client:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Linux/macOS:

```bash
bash gradlew :core:test :client:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. O workflow do GitHub Actions também disponibiliza o APK de teste e relatórios.

Somente regras/contratos JVM, sem configurar Android:

```bash
bash gradlew -PcoreOnly :core:test :client:test
```

A demonstração antiga continua disponível explicitamente com `-PbonamassaDemo=true` em build debug; seus dados são separados dos dados conectados. O build padrão usa a API.

## Distribuição

O servidor padrão fica em `gradle.properties` e não pode ser alterado pela interface. Para substituir o destino em uma compilação isolada de desenvolvimento ou teste:

```bash
bash gradlew :app:assembleDebug -PbonamassaApiUrl=http://10.0.2.2:3001 -PbonamassaStoreSlug=bonamassa
```

Release continua exigindo HTTPS. Ao atualizar uma instalação antiga, o aplicativo migra para o servidor configurado no build quando não há comando pendente. A assinatura de produção deve ser configurada pelo proprietário. Esta versão não implementa notificações push, rastreamento GPS em segundo plano ou prestação de contas/repasse. Maps e Waze são abertos por toque do usuário; o app não solicita localização nem permissão para fazer chamadas.

Detalhes: [integração](docs/INTEGRACAO.md) e [verificação](docs/VERIFICACAO.md).

## Preparação para produção

Leia [docs/PRODUCAO.md](docs/PRODUCAO.md) antes de distribuir o aplicativo. O release agora valida HTTPS e recusa flags demo/integração. Compilar não assina nem publica: o guia explica servidor definitivo, assinatura, atualização segura e testes em aparelho real.
