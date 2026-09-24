# SpiritBox

Aplicativo Android para varredura de frequências via WebSDR, com waterfall ao vivo, favoritos, capturas (CSV), exportação de imagem do waterfall e gravação de clipes de áudio (WAV).

## Build

```bash
./gradlew assembleDebug
```

Requires Android SDK (see `local.properties`). Release build precisa do keystore (`SPIRIT_KS_PASS` ou `keystore.properties`).

## CI

O workflow `.github/workflows/android.yml` roda testes, lint e `assembleDebug` a cada push/PR.