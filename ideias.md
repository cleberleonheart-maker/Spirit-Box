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
- **Eixo de frequência no waterfall** (v1.10): faixa de 15 dp no rodapé com marcas em
  passo redondo (1/2/5 × 10ⁿ) e rótulo em kHz até 999, MHz acima disso. O eixo também
  entra no PNG exportado, desenhado depois da ampliação para não sair serrilhado.
- **Toque para sintonizar** (v1.10): tocar numa coluna trava a varredura naquela
  frequência (`ACTION_TUNE` → `stayOn`), arredondada para o passo da faixa — sem o
  arredondamento a varredura pararia num valor que ela nunca visita. O long-press
  (exportar) continua funcionando via `performLongClick()`.
- **Textura min/max por coluna** (v1.10): `addSample` guardava só o máximo, então o dwell
  inteiro virava 1 pixel e uma portadora CW ficava indistinguível de uma emissora de voz.
  Agora guarda máximo e mínimo, e `apparentLevel()` pondera o pico pela modulação
  (0,75× para sinal contínuo, 1,0× para modulado).
- **Piso de ruído por faixa** (v1.10): `NoiseFloor` guarda as últimas 64 passagens e tira
  o percentil 20. O limiar efetivo é `max(limiar do usuário, 3 × piso)` — nunca fica
  abaixo do que o usuário configurou. Zera ao trocar de faixa, e só vale depois de 24
  amostras (antes disso é o limiar do usuário).

## Próximas (não implementadas)
- **Histórico persistente buscável**: leitura do CSV na UI (capturas já recarregam
  ao abrir o app; falta busca/filtro).
- **Widget de notificação com play/pause**.
- **Remover modo FM stub** ou marcá-lo experimental.
- **Bookmarks exportados/importados**: salvar lista de favoritos como arquivo.

## Novas ideias (26/09)
### Waterfall / leitura
- **Pinça para zoom** no waterfall: navegar dentro de uma faixa larga (militar) sem
  precisar trocar de preset.

### Varredura
- **Fila de faixas**: varrer AM+SW+CB+METEO em sequência automática, com log de ciclo.
- **Faixa personalizada**: editar início/fim/passo em vez de usar só os 8 presets.

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