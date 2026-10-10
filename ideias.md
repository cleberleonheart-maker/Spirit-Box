# Ideias para o SpiritBox

## Implementado recentemente
- **Watch list com alarme** (v1.27): os **favoritos** viram uma lista de
  observação. Ligando "Alarme nas favoritas", quando uma captura casa (tolerância
  de 1 kHz) com uma frequência marcada o app vibra (padrão distinto), toca um som
  e posta uma **notificação** — útil com a tela apagada. A lógica de casamento é
  pura (`WatchList`, testada). O alerta comum continua valendo para as demais.
- **Ajuda nos diálogos EMF** (v1.26): bolinha "i" ao lado do título abre um
  diálogo explicativo. Na **correlação**, descreve cada elemento do gráfico
  (linha do campo, pico, traço de rádio, faixa de evento combinado) e que a
  janela cobre só os últimos 5 s. No **mapa de hotspots**, explica como ativar
  (modo investigação + permissão/GPS) e o que cada botão faz (GRAVAR/PARAR, LIMPAR,
  estatísticas, exigência de magnetômetro).
- **Correlação rádio+EMF** (v1.25): timeline no diálogo EMF com a linha do campo
  (mG), traços das capturas de rádio e destaque dos **eventos combinados** — um
  pico do magnetômetro dentro de 5 s de uma captura. A lógica é pura
  (`CorrelationTimeline`, testada): cada excursão do campo acima de 15 mG vira um
  pico no seu maior valor e pareia com a captura mais próxima no tempo.
- **Modo EVP** (v1.25): grava o microfone em paralelo à varredura, **no serviço**
  (continua com a tela apagada). Cada captura de rádio vira um marcador com offset
  em ms; ao parar, salva `spiritbox_evp_*.wav` + `.json` (marcadores) em
  `Downloads/SpiritBox`. No Android 14+ o serviço entra em foreground também com
  o tipo `microphone` (só quando a permissão está concedida). Toggle "Gravar EVP"
  pede `RECORD_AUDIO`. Lógica de marcadores pura em `EvpSession` (testada).
- **Update check sem API (v1.18)**: descobre a última release com `HEAD` em
  `github.com/.../releases/latest` lendo o header `Location` — contorna redes
  onde `api.github.com` responde 404/301 e o auto-check sempre falhava.
- **Download de update reforçado (v1.19)**: redirect manual (até 5 saltos,
  cada um logado), URL da tag + fallback `latest/download`, 2 tentativas cada,
  timeouts 30/60 s e validação do tamanho do arquivo. Se nada passa, diálogo
  **ABRIR NO NAVEGADOR** leva à página de releases (mesmo APK, contorna CDN
  bloqueado).
- **Diagnóstico do download (v1.20)**: o diálogo de falha mostra o motivo exato
  (HTTP ou exceção + host) para o usuário reportar em vez de um aviso genérico.
- **Histórico persistente buscável** (v1.24): campo de busca acima da lista.
  Carrega até 1000 linhas do CSV (antes 200) e filtra por frequência, nível,
  data, banda, modo ou servidor — sem busca mostra as 200 últimas. A partir de
  `CaptureFilter` (puro, testado): vários tokens precisam todos bater, acentos
  são ignorados ("nivel" acha "nível") e número sem separador acha a frequência
  formatada ("1760" acha "1.760 MHz").
- **Correção do download interno (v1.22)**: `onProgress` rodava na thread do
  executor e o Toast estourava `NullPointerException: Can't toast on a thread
  that has not called Looper.prepare()` antes da primeira conexão — desde a
  v1.4 nenhum download pelo app chegou à rede. O aviso agora vai para a main
  thread; o motivo só apareceu na v1.20, que passou a exibir o erro.
- **Play/Pause na notificação**: botão que pausa/retoma a varredura e o áudio
  (mantém a conexão WebSDR viva; a varredura congela e volta de onde parou).
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

## Novas ideias (06/10)
### Detecção / alertas
- **Watch list com alarme**: marcar frequências (ou faixas) e vibrar/notificar quando
  capturar — reusa favoritos + notificação. (rápido)
- **Nível em SNR** (dB acima do piso de ruído) em vez de %, no app e no CSV.
- **Dedup por frequência**: agrupar capturas iguais (nº de avistamentos, nível máx).
- **Classificador simples voz/CW/ruído** usando o min/max por coluna do waterfall.
- **Voltar ao pico**: botão que volta à frequência de maior nível desde o último pico.

### Sessões & dados
- **Sessões nomeadas**: iniciar/encerrar, com nº de capturas, tempo, top frequências;
  exportar tudo num zip.
- **Comparar sessões**: o que apareceu/sumiu entre duas sessões.
- **Cartão de captura** compartilhável: PNG com recorte do waterfall + frequência/hora/GPS.
- **GPS nas capturas** (hoje só o EMF tem) e mapa de capturas reusando o mapa de hotspots.

### EMF / investigação
- **Gradiente do campo** (variação/s) com alarme, além do desvio da linha de base.

### Áudio
- **Gravação contínua** da sessão (não só 8 s) com marcadores nas capturas.
- **Repetir clipe** / loop de um trecho para reescutar no local.
- **Media session**: play/pause/mudo pelo fone, tela de bloqueio e (futuro) Android Auto.

### WebSDR / servidores
- **Lista de servidores públicos** com ping/latência e auto-reconnect, em vez de digitar o host.
- **Presets de varredura nomeados** (todos os parâmetros) com export/import JSON.
- **Indicador de buffer/qualidade** da conexão.

### Android/UX
- **Widget** com nível/pico + iniciar/parar (também na lista antiga).
- **Quick Settings tile** e atalhos do app: "iniciar AM", "iniciar SW".
- **Perfil carro / tela sempre ligada**.

## Ideias futuras (EMF / investigação)
- **Mapa de hotspots sobre mapa de fundo / imagem**: hoje é projeção local (lat/lng
  apenas); futuro: camada de mapa real e exportação do trajeto como imagem/GPX.

## Privacidade / segurança
- **Senha do keystore**: trocar a senha atual (`keystore.properties` ainda tem a
  senha antiga em texto plano; está fora do git, mas pode estar em backups). Usar
  sempre `SPIRIT_KS_PASS` no CI.
- **CI de release**: o workflow `Release` depende de `SPIRIT_KS_PASS` e
  `SPIRIT_KS_B64`; conferir se estão configurados no repo para publicar por tag.