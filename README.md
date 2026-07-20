# Driver Inteligente 1.1

Aplicativo Android local para ajudar motoristas a analisar ofertas visíveis da Uber e da 99. O app lê o card por `AccessibilityService` em modo somente leitura e usa OCR/MediaProjection como fallback. Ele calcula pagamento, R$/km, R$/hora, tempos, distâncias e nota do passageiro, aplica filtros configuráveis e mostra um overlay temporário em verde, amarelo ou vermelho.

## Recursos

- leitura local por acessibilidade e ML Kit OCR;
- overlay não interativo com quatro métricas configuráveis;
- fala “aceitar”, “analisar” ou “recusar corrida”;
- GPS opcional de sessão, sem histórico de rota;
- Destino Casa por raio, TSV offline, texto conservador e Geocoder Android assíncrono;
- temas claro, escuro e sistema, com três escalas de fonte;
- fixtures visuais Uber, Uber Radar, 99 e 99 Negocia;
- diagnóstico agregado de latência com objetivo abaixo de 1 segundo.

## Garantias

- sem permissão `INTERNET`;
- sem `ACCESS_BACKGROUND_LOCATION`;
- acessibilidade somente para leitura;
- sem clique, gesto, aceite ou recusa automática;
- overlay com `FLAG_NOT_TOUCHABLE` e `FLAG_SECURE`;
- sem persistência de frames, OCR bruto, ofertas ou rota GPS;
- distância GPS somente em memória durante a sessão.

## Requisitos

- Android Studio compatível com AGP 8.13.2;
- JDK 17;
- Android SDK 36;
- dispositivo Android 10/API 29 ou superior.

## Build e testes

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat lintRelease
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

Os testes conectados exigem aparelho autorizado no ADB:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

O APK release é gerado em `app/build/outputs/apk/release/app-release.apk`. Consulte [RELEASE_SIGNING.md](docs/RELEASE_SIGNING.md) para configuração de assinatura.

## Documentação

- [Revisão de arquitetura](ARCHITECTURE_REVIEW.md)
- [Implementação GPS, Casa e UI](docs/GPS_HOME_UI_IMPLEMENTATION_LOG.md)
- [Orquestração Codex](docs/CODEX_ORCHESTRATION.md)
- [Pacote offline de endereços](docs/OFFLINE_ADDRESS_PACK.md)

## Aviso

O Driver Inteligente apenas apresenta uma análise visual e falada. A decisão e qualquer ação nos aplicativos de transporte continuam exclusivamente com o motorista.
