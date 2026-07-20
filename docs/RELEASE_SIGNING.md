# Assinatura local da versão estável

O APK principal `br.com.nexo.driver` usa a chave local `driver-inteligente-release.jks` e as
credenciais privadas de `keystore.properties`. Os dois arquivos são ignorados pelo Git.

O pacote de desenvolvimento usa `br.com.nexo.driver.debug`. Assim, testes instrumentados podem
instalar e remover o APK de teste sem desinstalar a versão estável do aparelho.

## Cuidados obrigatórios

- Faça backup seguro de `driver-inteligente-release.jks` e `keystore.properties`.
- Nunca envie esses arquivos ao GitHub, por e-mail ou em mensagens.
- Perder a chave impede publicar atualizações assinadas sobre a versão instalada.
- Para distribuição pública, mantenha ao menos duas cópias criptografadas em locais separados.

## Build e verificação

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleRelease
```

O APK assinado será criado em `app/build/outputs/apk/release/app-release.apk`.

Para repetir apenas as fixtures visuais sem reinstalar o aplicativo principal:

```powershell
powershell.exe -ExecutionPolicy Bypass -File .\scripts\run-visual-fixtures.ps1
```
