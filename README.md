# BonamassaEntregador

App Android **Bonamassa Entregas**, em Kotlin e Jetpack Compose, com a identidade da pizzaria em preto, vermelho e dourado.

Projeto independente de `BonamassaAndroid`. Tem seu próprio APK, armazenamento e identificador `br.com.bonamassa.driver`; os dois apps podem ficar instalados no mesmo aparelho.

**Versão 0.1.0-demo: pedidos fictícios e registros locais. Não recebe despachos da pizzaria, não realiza cobranças e não rastreia localização.**

## O que já funciona

- Fila de entregas com filtros de coletas e pedidos em rota.
- Disponibilidade para novas coletas; pausar não impede concluir pedidos já retirados.
- Detalhes do pedido, itens, observações, destino e referência.
- Confirmação de retirada e início de entrega; é possível sair com mais de um pedido.
- Abertura de destino no Google Maps ou Waze e cópia do endereço.
- Abertura do discador se houver telefone válido. Os exemplos não contêm números de clientes.
- Valor a cobrar, pagamento antecipado, dinheiro, cartão e troco.
- Conclusão com nome do recebedor e confirmação obrigatória para dinheiro/cartão.
- Tentativa sem sucesso, motivo e observação, com etapa própria de devolução à pizzaria.
- Histórico com etapas e horários, taxas de entregas concluídas e dinheiro recebido separados.
- Nome do entregador e reinício da demonstração com confirmação.
- Persistência em DataStore; uma leitura inválida mostra erro e preserva os dados para tentar novamente.

Os destinos são exemplos em locais públicos de São Paulo. Antes de abrir a navegação, o app identifica o destino como demonstrativo. Nenhum pedido se refere a uma pessoa real. O campo de disponibilidade também é local.

## Abrir no Android Studio

1. Clone `https://github.com/nvpetri/BonamassaEntregador.git` e abra a raiz do projeto em **File → Open**.
2. Em **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK**, escolha um **JDK completo 17 ou 21**. O projeto mantém bytecode Java 17, inclusive usando JDK 21.
3. No SDK Manager, instale **Android SDK Platform 35**, **Build Tools 35.0.0** e Platform Tools.
4. Clique em **Sync Project with Gradle Files**. A primeira execução precisa baixar dependências.
5. Selecione a configuração `app` e um aparelho/emulador Android 8.0 (API 26) ou superior, depois clique em **Run**.

O aplicativo instalado se chama **Bonamassa Entregas**. O arquivo `local.properties`, caso gerado pelo Android Studio, deve ficar somente no seu computador.

### Compilar e testar

No Windows, na raiz do projeto:

```powershell
.\gradlew.bat :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

No Linux/macOS:

```bash
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Se a compilação terminar, o APK estará em `app/build/outputs/apk/debug/app-debug.apk`.

Somente as regras, sem SDK Android:

```powershell
.\gradlew.bat -PcoreOnly :core:test
```

O teste de interface precisa de aparelho ou emulador e **reinicia os dados da demonstração**:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Use o mesmo JDK em `JAVA_HOME` e no Gradle JDK do Android Studio. Se trocar de JDK no terminal, execute `.\gradlew.bat --stop` antes de compilar novamente.

## Roteiro de apresentação

1. Abra o pedido **#1043**, pago em dinheiro. Confira os R$ 90,00 a cobrar e R$ 10,00 de troco para R$ 100,00.
2. Toque em **Confirmar retirada → Retirei o pedido → Iniciar entrega**.
3. Abra **Abrir rota** para escolher Google Maps ou Waze. O destino é um exemplo.
4. Feche e reabra o aplicativo: a etapa continua salva.
5. Em **Confirmar entrega**, informe quem recebeu. Sem marcar o recebimento do pagamento, a confirmação permanece bloqueada.
6. Registre a entrega e confira o histórico: taxa de R$ 8,00 e dinheiro recebido de R$ 90,00 são mostrados separadamente.
7. Use o pedido **#1042** para demonstrar uma tentativa sem sucesso: retire, inicie, toque em **Não consegui entregar**, escolha um motivo e depois confirme a devolução.
8. Em **Perfil**, reinicie a demonstração para restaurar os três pedidos.

## Organização

| Parte | Local |
|---|---|
| Modelos e regras de entrega | `core/src/main/kotlin/br/com/bonamassa/core/delivery/` |
| Pedidos de exemplo | `core/src/main/kotlin/br/com/bonamassa/core/delivery/DriverDemo.kt` |
| Telas e tema Bonamassa | `app/src/main/java/br/com/bonamassa/driver/ui/` |
| Estado, ações e tratamento de erros | `app/src/main/java/br/com/bonamassa/driver/DriverViewModel.kt` |
| DataStore e codec JSON versionado | `app/src/main/java/br/com/bonamassa/driver/data/` |
| Abertura de mapas e discador | `app/src/main/java/br/com/bonamassa/driver/ExternalActions.kt` |

As regras de valores, taxas e operação são exemplos para aprovação da pizzaria. A logo foi fornecida pelo usuário; os direitos da marca permanecem com seus titulares.

Consulte [o plano de integração](docs/INTEGRACAO.md) e [o registro de verificação](docs/VERIFICACAO.md) para os limites da versão.
