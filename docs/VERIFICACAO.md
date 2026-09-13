# Verificação do Bonamassa Entregador — 13/09/2026

Versão de demonstração 0.1.0. Resultados obtidos com Gradle 8.10.2, Kotlin 2.0.21, Android Gradle Plugin 8.8.2, JDK 21, SDK 35 e Build Tools 35.0.0. Java e Kotlin compilam com alvo 17.

## Resultado da compilação completa

Comando executado na raiz do projeto:

```text
:core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
BUILD SUCCESSFUL
```

| Verificação | Resultado |
|---|---|
| Testes de regras de entrega | 17 aprovados, sem falhas ou erros |
| Testes de persistência/codec | 7 aprovados, sem falhas ou erros |
| Compilação Android | Concluída |
| Android Lint | Sem erros; 4 avisos e 1 informação |
| APK debug | Gerado: 17.427.752 bytes, aproximadamente 17,43 MB neste build |
| Assinatura do APK debug | Verificada com apksigner, esquema v2 |
| APK do teste instrumentado | Compilado |
| Execução em aparelho/emulador | Pendente |
| Conferência visual em aparelho/emulador | Pendente |

Os avisos do lint tratam de versão mais nova de activity-compose, qualificador de recurso redundante para API 26, ícone legado não utilizado e ausência de ícone monocromático. Há uma informação sobre otimização de estado inteiro no Compose. Eles não impedem a compilação. As dependências foram mantidas alinhadas à base do app cliente.

O APK inspecionado tem identificador `br.com.bonamassa.driver`, nome **Bonamassa Entregas** e versão `0.1.0-demo`. O manifesto final não pede Internet, localização ou chamada telefônica; contém apenas a permissão interna de receiver adicionada pelo AndroidX.

## Regras de entrega

Gradle 8.10.2, Kotlin 2.0.21 e JDK 21, com alvo Java 17:

```text
-PcoreOnly :core:test
BUILD SUCCESSFUL
17 testes, 0 falhas, 0 erros
```

Cobertura: coleta antes de iniciar, início antes de concluir, nome do recebedor, confirmação de dinheiro/cartão, pagamento antecipado, troco, taxas separadas de dinheiro recebido, duplicidade de conclusão, pausa de novas coletas, múltiplas entregas em rota, tentativa sem sucesso, devolução obrigatória, motivo “outro”, relógio retrocedendo, valores inválidos, identificadores duplicados, URLs de mapas e números de telefone.

## Persistência

Os sete testes do codec cobrem os três pedidos e suas formas de pagamento, conclusão com recebimento, histórico de tentativa/devolução, schema e status desconhecidos, duplicidade, valores negativos, sequência temporal inválida, estouro de inteiro e valores fracionários em campos monetários.

## Verificações restantes

- Teste instrumentado: código e APK compilados; execução em aparelho/emulador pendente.
- Conferência visual: pendente em aparelho/emulador, incluindo 360 dp e fonte ampliada.
- Integração real: ausente por escopo desta versão de demonstração.

O teste instrumentado reinicia os dados locais do app, demonstra o fluxo em dinheiro, recria a Activity durante o percurso, exige confirmação de pagamento e confere os valores no histórico. Recriar a Activity não equivale a encerrar o processo pelo sistema.

## Conferir antes de apresentar

- Abrir todas as telas, rotação, Voltar, teclado e fonte ampliada.
- Reiniciar o processo entre retirada e conclusão para conferir a persistência.
- Testar Google Maps/Waze instalados, navegador como alternativa e aparelho sem aplicativo compatível.
- Conferir as três formas de pagamento e a separação entre troco, valor cobrado e taxa do entregador.
- Testar tentativa sem sucesso e posterior devolução.
- Confirmar que os avisos de demonstração estão visíveis e que o app não aparenta receber pedidos reais.
