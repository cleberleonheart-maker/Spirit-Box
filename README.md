# SpiritBox

Aplicativo Android para varredura de frequências via WebSDR, com waterfall ao vivo, favoritos (bookmarks), capturas (CSV), exportação de imagem do waterfall e gravação de clipes de áudio (WAV).

## Recursos

- Varredura de faixas (AM, SW, CB, VHF aeronáutica, FM, METEO, marítimo, militar) com dwell/settle/limiar/gap configuráveis.
- Waterfall ao vivo (gradiente vermelho) com marcadores de captura e exportação de imagem (toque longo).
- Modo noturno (filtro vermelho) e tema claro/escuro/sistema.
- Favoritos de frequência com sintonia direta.
- Histórico de capturas persistido em CSV e espelhado em `Download/SpiritBox/`.
- Clipes WAV dos últimos 8 s (modo SDR, API >= 29) salvos ao apertar **Salvar**.
- Notificação com ações (Salvar, Hold, Mudo, Parar) e auto-check de atualização via GitHub Releases.
- Modo FM experimental (rádio FM local) com aviso de indisponibilidade.

## Build

```bash
# Debug (EOF: assinatura Android Debug)
./gradlew assembleDebug

# Release (assinada com a keystore de produção)
SPIRIT_KS_PASS='sua-senha' ./gradlew assembleRelease
```

Requer Android SDK (ver `local.properties`).
A keystore de release vive em `keystore/spirit.keystore` (fora do git); a senha vem de `SPIRIT_KS_PASS` ou do fallback `keystore.properties`.

## CI

- `.github/workflows/android.yml` — testes, lint e `assembleDebug` a cada push/PR.
- `.github/workflows/release.yml` — ao publicar uma tag `v*`, builda a release **assinada com a sua keystore** (secrets `SPIRIT_KS_PASS` + `SPIRIT_KS_B64`) e anexa o APK à Release do GitHub:

```bash
git tag v1.2
git push origin v1.2
```

Para instalar/atualizar sem conflito, use sempre o APK da Release (assinatura `9105225d…`), nunca misturar com APK debug.