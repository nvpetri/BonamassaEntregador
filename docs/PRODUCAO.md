# Android entregador — preparação para produção

Este guia prepara a distribuição; **não cria chave privada, não assina release nem publica na Google Play**. Para nuvem, segurança e operação, seguir o [manual central](https://github.com/nvpetri/APIBonamassa/blob/codex/preparacao-producao/docs/producao/README.md).

## 1. Definir o servidor definitivo

Em `gradle.properties`:

```properties
bonamassaApiUrl=https://api.seu-dominio.example
bonamassaStoreSlug=bonamassa
```

São configurações públicas do aplicativo, não segredos. Nunca colocar senha do banco, token de gerente ou SESSION_SECRET no APK.

A URL atual do repositório continua sendo a origem HTTPS já usada na demonstração. **Esta etapa não trocou a aplicação para um novo servidor.** Antes de distribuir release, confirmar que a origem aponta ao banco/loja de produção. O verificador exige HTTPS e formato seguro, mas não consegue provar que um servidor HTTPS é o ambiente correto.

Pode manter a origem Render estável, se essa for a decisão operacional. Para domínio próprio, preparar DNS/TLS antes de alterar o app. Apps já instalados não recebem novas configurações só porque você editou o Git; precisam de atualização.

## 2. Verificar o release

Na raiz do repositório, com JDK 17 e Android SDK configurados:

```powershell
.\gradlew.bat :app:verifyReleaseConfiguration
.\gradlew.bat :core:test :client:test :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:assembleRelease :app:lintRelease
```

No Git Bash/Linux, usar `bash gradlew` em vez de `.\gradlew.bat`.

O build release executa a verificação automaticamente. Recusa HTTP, origem com credencial/caminho/query/fragmento e flags `bonamassaDemo` ou `bonamassaIntegration`. Ambas precisam estar ausentes ou false. A CI também tenta uma configuração HTTP inválida e exige que ela seja recusada.

`assembleRelease` nesta configuração produz **release sem assinatura de produção**. Compilar release não é publicar, e um APK sem assinatura não é instalável normalmente. O APK debug da CI é para testes e não deve ser distribuído como versão final.

A CI preserva os testes reais com API/PostgreSQL descartáveis. Os testes instrumentados usam emulador Android 15/API 35; isso não cobre todos os fabricantes, versões ou comportamentos do APK release instalado.

## 3. Assinar sem expor a chave

No Android Studio, usar **Build → Generate Signed Bundle / APK**, escolher APK para distribuição direta ou AAB para o fluxo Google Play, selecionar módulo app e variante release.

Criar/selecionar um keystore privado fora da pasta do projeto, com senha forte guardada em cofre. Proteger a chave e sua cópia de recuperação; não usar o certificado debug. Para futuras atualizações, preservar a identidade de assinatura. Play App Signing separa chave de upload e assinatura conforme o fluxo escolhido. [Guia oficial de assinatura](https://developer.android.com/studio/publish/app-signing).

O .gitignore já exclui .jks, .keystore e arquivos usuais de senha de assinatura, mas isso não substitui revisão do git diff. Não enviar chave/senha em chat, PR ou artifact de CI. A automação de assinatura fica para uma etapa com secrets e aprovação configurados.

## 4. Identificar a versão

- applicationId: `br.com.bonamassa.driver`.
- Incrementar versionCode em cada atualização distribuída. versionName é o nome visível da versão.
- Registrar commit, versão, API/unidade, data, fingerprint do certificado e hash do arquivo.
- Guardar mapping.txt do release ofuscado em local restrito para analisar falhas.

Exemplo local para registrar integridade do APK já assinado:

```powershell
Get-FileHash "C:\releases\bonamassa-entregador-release.apk" -Algorithm SHA256
```

Conferir a assinatura com a ferramenta apksigner do Android SDK ou pelo fluxo da loja. Hash do arquivo não substitui verificação da assinatura.

## 5. Testar o arquivo que será entregue

Em aparelho real de teste, com ambiente de homologação e dados fictícios:

1. Login com perfil entregador; conta desativada não entra.
2. Ver somente entregas atribuídas e dados necessários.
3. Coletar/sair, abrir rota externa, concluir e tratar ocorrência/retorno.
4. Pedido de outro entregador não pode ser acessado.
5. Perda de rede após comando: reconciliar sem duplicar baixa.
6. Reiniciar o aplicativo e preservar operação pendente até a confirmação.

Testar Wi-Fi/rede móvel, aparelho mais antigo suportado (minSdk 26) e os aparelhos reais da equipe. Confirmar também atualização sobre uma versão release anterior.

**Debug e release podem ter assinaturas diferentes.** Não desinstalar o app de uso real para resolver conflito de assinatura enquanto houver comandos/pedidos pendentes: isso pode apagar dados locais. Usar um aparelho separado para o primeiro teste ou reconciliar tudo com a pizzaria antes de uma troca planejada.

## 6. Distribuição e atualização

Decidir o canal com a pizzaria. Para Google Play, conferir requisitos vigentes de target SDK, verificação da conta, política de privacidade, segurança de dados e testes da loja antes de enviar. O projeto compila com targetSdk 35; aprovação na loja não foi verificada nem está garantida. Para distribuição fora da loja, verificar as exigências atuais do Android para desenvolvedor/distribuição nos países atendidos e usar canal restrito/confiável.

Não mudar domínio enquanto existirem operações locais pendentes vinculadas ao servidor antigo. A reconciliação usa a mesma origem/unidade e chave de idempotência; trocar a origem não transfere esses comandos. Os clientes não seguem redirects de API: manter a origem antiga atendendo durante a transição controlada.

Em caso de falha já distribuída, publicar correção com versionCode maior, assinatura compatível e contrato de API compatível. Voltar simplesmente para um APK mais antigo pode ser bloqueado ou não compreender estados novos como reservas.

## Critério para liberar

Assinatura protegida, origem correta, testes do release no aparelho real, aprovação do gerente e checklist central concluído. CI verde sozinho não autoriza exposição pública de pedidos reais.
