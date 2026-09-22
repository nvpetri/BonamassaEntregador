# Verificação

O workflow `.github/workflows/android.yml` compila o aplicativo e os testes, executa regras JVM, contratos HTTP e Android lint. Em seguida inicia PostgreSQL 17, aplica migrações e seed da mesma revisão da API usada pelos outros consumidores (ver `INTEGRACAO.md`) e executa os testes de interface em Android 35.

Os testes conectados são opt-in e usam somente a loja isolada de CI. Credenciais de fixtures estão no APK de instrumentação, não no APK distribuído. Não execute o teste integrado contra dados reais.

Cobertura do fluxo integrado:

- Primeiro acesso solicita e confirma e-mail pela tela; recuperação de senha revoga a sessão anterior e aceita o novo login.
- Login pela tela; disponibilidade refletida no servidor.
- Pedido criado por cliente, preparado e atribuído pela gerência; retirada e saída pelo app.
- Pausa de coletas sem bloquear entregas já retiradas.
- Nome e confirmação explícita de dinheiro na conclusão; painel recebe o mesmo resultado.
- Recriação de Activity, histórico e revogação de sessão ao sair.
- Reatribuição retira acesso do entregador anterior.
- Perda de resposta e reinício: replay da mesma chave, sem repetir retirada e sem regredir estado mais recente.
- Rejeição de versão antiga; motivo de tentativa e devolução sem taxa de entrega concluída.
- PATCH pendente preservado após sessão expirada e recuperado com novo login.

Os testes JVM verificam transições, pagamento, isolamento da conta/origem, serialização exata, requisições PATCH, paginação, rejeição de perfis indevidos e bloqueio de redirecionamentos com credenciais. A demonstração mantém seus testes de regras, codec e interface.

O workflow publica APK debug, relatórios e capturas de tela. Consulte o resultado do run associado ao commit do PR para a evidência de execução. Teste final em aparelho físico na rede da pizzaria, assinatura de produção e homologação operacional continuam necessários antes da distribuição.
