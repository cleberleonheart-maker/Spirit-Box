# Ideias para o SpiritBox

## Implementado recentemente
- **Bookmarks / favoritos**: botão ★ marca/desmarca a frequência atual (persistida
  em Prefs como lista de kHz); spinner "★ Favoritos" sintoniza direto num favorito
  via `ACTION_TUNE` + `SweepEngine.stayOn()`.
- **Exportar PNG do waterfall**: long-press no waterfall compartilha um snapshot
  (bitmap atual + marcadores) via FileProvider.
- **Gravar clipes**: buffer circular com os últimos 8 s de áudio decodificado
  (PCM A-law 11025 Hz); a cada captura grava um WAV em
  `Download/SpiritBox/spiritbox_clip_*.wav` (somente modo SDR, API ≥ 29).
- **Auto-check de atualização** (UpdateChecker + VersionComparator): consulta o
  GitHub (`cleberleonheart-maker/Spirit-Box`) uma vez por dia e oferece download
  via diálogo. Silencioso se falhar / não houver release.
- **Tema claro/escuro/sistema** (DayNight) + **filtro vermelho noturno** com
  toggle de brilho (overlay + `screenBrightness`).
- **Marcadores de captura no waterfall** (linha vermelha na faixa capturada).
- **Notificação com ações**: Salvar, Hold, Mudo e Parar.
- **Filtro de ruído** (v1.3): toggle ao vivo com `noisered`/`autonotch` do WebSDR.
- **Mini mapa de movimento (oculto)** (v1.5): heatmap que marca atividade variável
  no sinal; fica oculto por padrão e só aparece quando detecta movimento real
  (modo investigação, toggle interno `Prefs.motionUnlocked`; sem publicar na loja).
- **EMF com magnetômetro real** (v1.6): leitura de `TYPE_MAGNETIC_FIELD`, mostra a
  variação do campo magnético em mG (desvio da linha de base). Sem sensor, cai no
  modo simulado com aviso.
- **Traduções completas** (pt-BR, en, de, es, it, ja) e lint sem erros.

## Próximas (não implementadas)
- **Histórico persistente buscável**: leitura do CSV na UI (capturas já recarregam
  ao abrir o app; falta busca/filtro).
- **Widget de notificação com play/pause**.
- **Remover modo FM stub** ou marcá-lo experimental.
- **Bookmarks exportados/importados**: salvar lista de favoritos como arquivo.

## Privacidade / segurança
- **Senha do keystore**: trocar a senha atual (`keystore.properties` ainda tem a
  senha antiga em texto plano; está fora do git, mas pode estar em backups). Usar
  sempre `SPIRIT_KS_PASS` no CI.
- **CI de release**: o workflow `Release` depende de `SPIRIT_KS_PASS` e
  `SPIRIT_KS_B64`; conferir se estão configurados no repo para publicar por tag.