# Ideias para o SpiritBox

## Mini mapa de movimento (oculto)
- Um mini mapa/heatmap que capta **movimento**: detecta atividade variável no sinal (não só pico) e marca onde houve movimento ao longo do tempo.
- **Fica oculto por padrão** — só aparece na UI quando detectar movimento real (alguma função interna/experimental, longe da tela principal).
- Se implementar, mantê-lo ativo/oculto: só é exibido quando há movimento (toggle interno, sem publicar na loja nas primeiras versões).

## Próximas (não implementadas)
- **Gravar clipes**: ao detectar captura, salvar os últimos N segundos de áudio para replay.
- **Filtro de ruído**: usar os parâmetros do WebSDR (`noisered`, `autonotch`) por banda.
- **Inserir nível por frequência no waterfall** — marcar capturas no waterfall.
- **Bookmark / favoritos**: salvar frequências de interesse e retornar a elas.
- **Histórico persistente buscável**: leitura do CSV na UI (já feito: capturas recarregam ao abrir o app).
- **Exportar PNG do waterfall atual**.
- **Widget de notificação com play/pause**.
- **Tema dark / light automático**.
- **Remover modo FM stub** ou marcá-lo experimental.

## Privacidade / segurança
- **Senha do keystore**: trocar a senha atual (`keystore.properties` já tem a senha antiga em texto plano; está fora do git, mas pode estar em backups). Usar sempre `SPIRIT_KS_PASS` no CI.
- **Opcional**: assinatura de release via secrets do GitHub Actions.