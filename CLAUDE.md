# Driver Inteligente (NexoDriver)

App Android para motoristas Uber/99: lê ofertas de corrida via OCR local (MediaProjection + ML Kit
on-device), calcula R$/km, R$/h, nota, tempo e distância, e mostra um overlay de análise sobre a
tela. `namespace`/`applicationId`: `br.com.nexo.driver`.



## Arquitetura (módulo único `:app`)

- `capture/` — `OfferCaptureService` (foreground service, `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION`;
  o manifest também declara `shortService`, usado só para satisfazer o contrato de
  `startForegroundService` em starts rejeitados — sem ele o processo crasha com
  `ForegroundServiceDidNotStartInTimeException`, verificado em S23/Android 16) e `capture/service/`
  (orquestração de frames, throttle de 100ms + backpressure de 1 frame, guard de sessão contra
  callbacks tardios do MediaProjection).
- `ocr/` — contrato `LocalOcrEngine` + implementação `ocr/mlkit/MlKitBitmapOcrEngine` (ML Kit Text
  Recognition on-device, timeout de 750ms, chamado sempre fora da main thread).
- `parser/` — `OfferTextParser`: regex/strings hardcoded pt-BR para telas Uber/99. Frágil a mudanças
  de layout dos apps-alvo. `OfferParserRegistry.parseAttempt()` distingue "nenhum card visível" de
  "card reconhecido (ex.: 'UberX') mas campos não extraídos" — o segundo caso é logado como aviso
  (`Log.w`) em `OfferCaptureService` e contado em `OfferOcrMetrics.unrecognizedLayoutCount`, servindo
  de alerta de possível drift de layout em vez de falhar silenciosamente.
- `evaluation/` — `OfferEvaluator`: cálculo de R$/km, R$/h e regras de filtro; protegido contra
  divisão por zero; dado ausente vira `MetricStatus.UNKNOWN` → decisão `ANALYZE` (nunca decide
  sozinho).
- `overlay/` — `WindowManagerOfferOverlay` (não interativo) e `OfferOverlayPresenter`.
- `destination/` — destino do motorista e resolução offline de endereços via pacote TSV local (ver
  [docs/OFFLINE_ADDRESS_PACK.md](docs/OFFLINE_ADDRESS_PACK.md)) — não usa o recurso de destino da
  Uber, não envia endereços para a internet.
- `profile/`, `permission/`, `offline/`, `ui/` (Compose: home, filtros, settings, tema).

Modelo de concorrência: `Handler`/`HandlerThread`/`Executor` clássicos (sem coroutines ainda) — ver
[android-skills:kotlin-coroutines](https://github.com/rcosteira79/android-skills) antes de decidir
migrar.

## Orçamento de latência (<1s captura→overlay)

- `capture/performance/OfferResponseLatencyTracker` mede frame-capturado→overlay-exibido (meta
  1000ms, p50/p95/max, janela de 100 amostras).
- OCR tem timeout próprio de 750ms (`ocr/mlkit/DEFAULT_RECOGNITION_TIMEOUT_MILLIS`).
- O restante do pipeline (parse+evaluate+enrich+render) tem seu próprio orçamento de 250ms via um
  segundo tracker (`postOcrLatency` em `OfferCaptureService`), que loga um aviso (`Log.w`) quando
  excedido — use isso para diferenciar "OCR lento" de "pipeline pós-OCR lento" ao investigar
  violações de latência.
- Números reais medidos em Galaxy S23/Android 16 (`MlKitOcrLatencyBenchmark`, androidTest):
  OCR cold-start 281ms; OCR quente p50 189ms @ minor edge 1080. Reduzir para 720 economizou só
  ~30ms — decidiu-se manter 1080 para preservar o texto pequeno dos cards da 99. O throttle de
  captura é 100ms (`DEFAULT_MIN_FRAME_INTERVAL_MS`); o backpressure de 1 frame já protege o OCR,
  então não aumente o throttle para "aliviar" o OCR — isso só atrasa o primeiro frame do card.

## Build/release

- `compileSdk`/`targetSdk` = 36, `minSdk` = 29, Java 17, AGP 8.13.2, Kotlin 2.2.10.
- Dependências no version catalog (`gradle/libs.versions.toml`); evite `implementation("...")` com
  string literal direto no `app/build.gradle.kts`.
- Build de release: `isMinifyEnabled = true` com regras R8 em `app/proguard-rules.pro` (inclui keep
  rules do ML Kit). Assinatura via `keystore.properties` (gitignored, nunca commitar) — copie
  `keystore.properties.example` e gere sua própria keystore local. Sem esse arquivo, `assembleRelease`
  ainda funciona mas produz um artefato **não assinado**.

## Testes

- `app/src/test` — testes unitários de lógica pura (parser, evaluator, latência, throttle, etc.).
- `app/src/androidTest` — testes instrumentados (ciclo de vida real de `Service`/`WindowManager`).
  Requer dispositivo/emulador conectado; ainda cobre pouco além do lifecycle básico do
  `OfferCaptureService` — expandir para overlay real e ML Kit real é um gap conhecido.

## Documentação relacionada

- [docs/OFFLINE_ADDRESS_PACK.md](docs/OFFLINE_ADDRESS_PACK.md) — formato do pacote offline de
  endereços usado pelo filtro "em direção ao destino".
