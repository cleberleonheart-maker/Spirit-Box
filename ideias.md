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
- **Gráfico de tendência do campo no EMF** (v1.7): série dos últimos ~60 s da
  variação em mG desenhada no diálogo, colorida por intensidade.
- **Mapa de hotspots magnéticos** (v1.7): grava trajetória com GPS enquanto o
  campo varia, desenha o caminho e marca picos por intensidade; persistido em
  `emf_hotspots.json` (oculto no modo investigação).
- **Traduções completas** (pt-BR, en, de, es, it, ja) e lint sem erros.

## Em andamento → entregue (v1.9)
- **Isenção de bateria (Doze) guiada** (v1.9): `batteryExempt()` + oferta da tela de
  isenção no primeiro uso; em MIUI/HyperOS abre antes a tela de autostart (que não
  concede isenção) e retoma a tela padrão no `onResume` (`pendingBatteryPrompt`).
  Toast `battery_not_exempt` (6 idiomas) ao abrir o mapa EMF se a energia estiver
  restringida — era o motivo de a varredura "travar" sem explicação.
- **Leitura velha do magnetômetro** (v1.9): `EmfMeter` passa a medir idade da amostra
  com `SystemClock.elapsedRealtime()` (immune a mudança de relógio) e zera
  `fieldUv`/`lastSampleElapsed` no `start()`, para não gravar hotspot com leitura velha.

## Em andamento → entregue (v1.10)
- **Relógio do `SweepEngine`** (v1.10): as fases (settle/hold/gap) passam a usar
  `SystemClock.elapsedRealtime()`. Com `currentTimeMillis`, mudar o fuso ou o NTP
  ajustar a hora no meio da varredura pulava o dwell ou prendia a fase — mesma
  classe de bug que a v1.9 corrigiu no `EmfMeter`.
- **`configureRange` volta ao início da faixa** (v1.10): trocar de banda no meio do
  dwell continuava da frequência da banda antiga, então a varredura da nova começava
  no meio dela. Também zera o `peak` e a fase.
- **Janela para as passagens** (v1.10): `captureStreak` não decayia, então com
  `requiredPasses=3` um pico isolado de 30 min atrás já contava como 3. Agora a
  passagem só vale por `STREAK_WINDOW_MS` (60 s), e a decisão de capturar virou
  `shouldCapture(...)` para poder ser testada.
- **PNG do waterfall com tamanho de leitura** (v1.10): `snapshotBitmap()` agora
  amplia 6× nearest-neighbor (240×48 → 1440×288); antes a imagem compartilhada
  abria como uma tira fininha.

## Próximas (não implementadas)
- **Histórico persistente buscável**: leitura do CSV na UI (capturas já recarregam
  ao abrir o app; falta busca/filtro).
- **Widget de notificação com play/pause**.
- **Remover modo FM stub** ou marcá-lo experimental.
- **Bookmarks exportados/importados**: salvar lista de favoritos como arquivo.

## Novas ideias (26/09)
### Waterfall / leitura
- **Eixo de frequência no waterfall** (hoje não mostra kHz em lugar nenhum) + toque para
  sintonizar e pinça para zoom numa faixa.
- **Textura do sinal**: `addSample` guarda só o máximo por coluna, então o dwell inteiro
  vira 1 pixel. Guardar min/max por coluna mostra a modulação.

### Varredura
- **Baseline por faixa**: o limiar é absoluto (0.12) em bandas com pisos de ruído muito
  diferentes (AM vs militar 225–400 MHz). Normalizar por percentil do ruído de fundo.
- **Fila de faixas**: varrer AM+SW+CB+METEO em sequência automática, com log de ciclo.

### Dados / investigação
- **Estatísticas por frequência**: nº de capturas, primeiro/último avistamento, nível
  máximo, e um painel de "top frequências".
- **Exportar/importar favoritos e presets** em JSON, junto do trajeto EMF em **GPX**
  (o `emf_hotspots.jsonl` já tem lat/lng).
- **"Limpar dados de investigação"**: hotspots + histórico num botão só.

### Android
- **Notificação com frequência atual e contador de capturas**; widget com play/pause.

## Ideias futuras (EMF / investigação)
- **Mapa de hotspots sobre mapa de fundo / imagem**: hoje é projeção local (lat/lng
  apenas); futuro: camada de mapa real e exportação do trajeto como imagem/GPX.

## Privacidade / segurança
- **Senha do keystore**: trocar a senha atual (`keystore.properties` ainda tem a
  senha antiga em texto plano; está fora do git, mas pode estar em backups). Usar
  sempre `SPIRIT_KS_PASS` no CI.
- **CI de release**: o workflow `Release` depende de `SPIRIT_KS_PASS` e
  `SPIRIT_KS_B64`; conferir se estão configurados no repo para publicar por tag.