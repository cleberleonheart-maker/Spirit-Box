# SpiritBox

Aplicativo Android para varredura de frequências via WebSDR, com waterfall ao vivo, favoritos, capturas (CSV), exportação de imagem do waterfall e gravação de clipes de áudio (WAV).

## Build

```bash
./gradlew assembleDebug
```

Requires Android SDK (see `local.properties`). Release build precisa do keystore (`SPIRIT_KS_PASS` ou `keystore.properties`).

## CI

O workflow `.github/workflows/android.yml` roda testes, lint e `assembleDebug` a cada push/PR.

## Release

Para publicar uma versão final assinada:

1. Ajuste `versionCode`/`versionName` no `app/build.gradle` (padrões: 2 / 1.1).
2. Crie uma tag `v*` e empurre para `main`:

   ```bash
   git tag v1.2
   git push origin v1.2
   ```

3. O workflow `.github/workflows/release.yml` roda no push da tag: restaura o keystore do secret `SPIRIT_KS_B64`, builda o `assembleRelease` assinado (nome da versão vem da tag) e anexa o APK na GitHub Release.

O keystore de release fica nos secrets do repositório (`SPIRIT_KS_B64` = base64 do `.keystore`, `SPIRIT_KS_PASS` = senha).

> Instalações/atualizações só funcionam por cima de APK assinado com a **mesma chave**. A release da CI e os builds locais com o mesmo keystore atualizam sem desinstalar; uma chave diferente exige desinstalar antes.