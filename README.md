# Bonamassa Entregador

App Android do motoboy conectado à [APIBonamassa](https://github.com/nvpetri/APIBonamassa), com a identidade Bonamassa em preto, vermelho e dourado. Projeto independente do app do cliente; os dois podem ser instalados juntos.

## Testar com a pizzaria

1. Atualize e inicie a API na porta 3001. O painel deve apontar para essa mesma API e loja.
2. No painel, cadastre uma conta de funcionário com perfil **Entregador**, e-mail e senha. Contas de cliente/gerência não entram neste app.
3. Abra este projeto no Android Studio. Selecione um **JDK completo 17 ou 21**, SDK Android 35 e execute **Sync Project with Gradle Files**.
4. Execute `app` em aparelho Android 8.0 ou superior. Na tela de acesso, toque em **Configurar API** e informe `http://IP-DO-PC:3001` e o identificador da loja, normalmente `bonamassa`. No emulador Android Studio, use `http://10.0.2.2:3001`.
5. Entre com a conta do entregador e ative **Disponível para coletas**.
6. Faça um pedido de entrega pelo app cliente. No painel, aceite, prepare, marque como pronto e atribua ao entregador.
7. No app de entregas: **Confirmar retirada → Retirei o pedido → Iniciar entrega → Sair para entrega**. Abra Maps/Waze se precisar de navegação.
8. Em **Confirmar entrega**, informe quem recebeu e confirme o recebimento do dinheiro/cartão quando solicitado. A conclusão aparecerá no painel e no app cliente.

O celular e o PC precisam estar na mesma rede. A API deve escutar em `0.0.0.0` e a porta 3001 deve estar liberada no firewall da rede privada. `localhost` no celular aponta para o próprio celular.

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

HTTP e configuração de servidor pela tela estão disponíveis apenas em debug, para testes na rede local. Release exige HTTPS e recebe o servidor na compilação:

```bash
bash gradlew :app:assembleRelease -PbonamassaApiUrl=https://api.seudominio.com.br -PbonamassaStoreSlug=bonamassa
```

A assinatura de produção deve ser configurada pelo proprietário. Esta versão não implementa notificações push, rastreamento GPS em segundo plano ou prestação de contas/repasse. Maps e Waze são abertos por toque do usuário; o app não solicita localização nem permissão para fazer chamadas.

Detalhes: [integração](docs/INTEGRACAO.md) e [verificação](docs/VERIFICACAO.md).
