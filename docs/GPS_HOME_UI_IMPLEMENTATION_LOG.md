# GPS, Destino Casa e revisão de interface — registro de implementação

Data da revisão: 17 de julho de 2026.

## Resultado

O Driver Inteligente passou a ter localização de sessão independente da captura de ofertas, regra objetiva de destino próximo de Casa e uma interface revisada para leitura rápida. O GPS não substitui o destino final informado pela Uber/99 e não participa da decisão Casa. O primeiro overlay continua imediato; uma resolução tardia do destino só pode atualizar a oferta ainda ativa, uma única vez, sem reiniciar os oito segundos e sem repetir a fala.

## Comportamento anterior e novo

| Área | Antes | Agora |
|---|---|---|
| Localização | Sem rastreamento de sessão independente | Foreground service explícito, GPS principal, network fallback, estado/precisão/provedor e distância apenas em memória |
| Casa | Indício genérico “em direção ao destino” | Destino final dentro do raio da Casa, com coordenadas, TSV offline, texto conservador e Geocoder assíncrono |
| Filtro | `IS_TOWARD_DESTINATION` podia decidir a regra | `ENDS_NEAR_HOME`; regra antiga migrada e removida do seletor de novos filtros |
| Overlay | Posição inferior próxima ao rodapé e métricas menos responsivas | Card seguro, elevado acima da área comum de ações, título curto, pagamento sem duplicação e cores independentes por métrica |
| Resumo | Valores diários sem fonte ativa | Ofertas e distância da sessão ligadas a repositórios somente em memória |
| Importação TSV | Validação no fluxo principal | Leitura/validação limitada a 16 MB em `Dispatchers.IO`, com erros específicos |
| Privacidade | Havia diagnóstico legado mais detalhado | Sem INTERNET, rota, frames, OCR bruto ou ofertas persistidas; somente métricas agregadas locais |

## Subsistemas e arquivos principais

- `location/LocationContracts.kt`: `GeoPoint`, fixes, estados, seleção de provedor e acumulador de movimento.
- `location/CurrentLocationTracker.kt`: permissões, GPS/network, last-known e listeners.
- `location/CurrentLocationService.kt`: foreground service `location`, notificação persistente e ação Parar.
- `location/CurrentLocationStateRepository.kt`: estado e distância da sessão somente em memória.
- `destination/HomeMatcher.kt`: Haversine com raio terrestre de 6371,0088 km e fallback textual conservador.
- `destination/DriverDestinationStore.kt` e codecs: modelo Casa v3 e migrações.
- `destination/offline/DestinationOfferEnricher.kt`: resolução offline sem usar pickup ou GPS atual.
- `analysis/OfferAnalysisProcessor.kt`: overlay inicial não bloqueante e enriquecimento tardio protegido por geração.
- `analysis/ActiveOfferUpdateGate.kt`: validade de oito segundos e descarte de ofertas antigas.
- `evaluation/OfferEvaluator.kt`: métrica `ENDS_NEAR_HOME`; desconhecido resulta em análise.
- `ui/home/HomeScreen.kt`: card GPS e resumo da sessão.
- `ui/destination/HomeDestinationScreen.kt`: endereço, resolução, raio, coordenadas avançadas e pacote TSV.
- `ui/filters/FilterPickerSheet.kt`: seletor real sem permitir recriar a regra legada.
- `ui/settings/SettingsScreen.kt`: temas, fonte, posição, campos do overlay, acessibilidade, fala e galeria.
- `overlay/OfferOverlayCard.kt` e `WindowManagerOfferOverlay.kt`: card responsivo, sem toque, seguro e com auto-dismiss.
- `AndroidManifest.xml`: permissões de localização e serviço não exportado, sem localização em background e sem INTERNET.

## Regras de localização implementadas

- atualização solicitada a cada 2 segundos e deslocamento mínimo de 5 metros;
- rejeição de fixes com precisão pior que 60 metros;
- jitter ignorado abaixo de `max(5, min(25, (precisão anterior + atual) × 0,35))`;
- velocidade reportada ou calculada acima de 170 km/h rejeitada;
- `elapsedRealtimeNanos` usado para ordenação/cálculo;
- last-known apenas inicializa estado e nunca soma distância;
- diferença entre provedores acima de 10 segundos favorece o ponto mais novo; caso contrário, a melhor precisão;
- distância zerada ao parar o serviço e nunca persistida;
- sem `ACCESS_BACKGROUND_LOCATION` e sem inicialização automática.

## Destino Casa e migrações

- perfil v1 → v2: `IS_TOWARD_DESTINATION` é convertido em `ENDS_NEAR_HOME`, preservando ordem, ativação e comparador;
- Casa v1/v2 → v3: endereço original/padronizado, coordenada opcional, estado, ativação, atualização e raio;
- raio novo padrão de 2 km; mínimo de 0,2 km e máximo de 20 km;
- editar endereço invalida imediatamente coordenadas antigas;
- debounce de 400 ms e geração impedem que resolução antiga sobrescreva edição nova;
- coordenadas confiáveis têm precedência; cidade/estado isolados nunca aprovam a regra;
- destino desconhecido com regra ativa produz “Analisar”, nunca “Aceitar”.

## Correções da auditoria visual

- `BackHandler` agora retorna de Filtros, Casa e Ajustes para a tela inicial;
- navegação inferior usa vetores com descrições acessíveis;
- seletor de filtros exclui a métrica legada;
- escolhas de tema/fonte/posição possuem alvo mínimo de 48 dp;
- prévia de fonte deixou de aplicar o multiplicador duas vezes;
- importação TSV não bloqueia mais a thread principal;
- overlay inferior foi elevado para reduzir sobreposição com botões Uber/99;
- cabeçalho e métricas foram compactados para 360/390 dp e fonte grande;
- título permanece somente “Aceitar”, “Analisar” ou “Recusar”;
- valor total aparece uma vez, no cabeçalho;
- verde neon `#39FF88`, amarelo e vermelho são semânticos e independentes por campo.

## Validação executada

| Comando | Resultado | Duração observada |
|---|---|---:|
| `.codex/scripts/validate-orchestration.ps1` | aprovado | incluído no gate local |
| `.codex/hooks/driver_policy_guard.ps1` | aprovado | incluído no gate local |
| `gradlew testDebugUnitTest` | 162 testes, 0 falhas/erros | 1 min 24 s junto da compilação AndroidTest |
| `gradlew compileDebugAndroidTestKotlin` | aprovado | 1 min 24 s junto dos unitários |
| `gradlew lintDebug lintRelease assembleDebug assembleRelease` | aprovado antes do último refino visual; repetido no gate final | 8 min na rodada anterior |

Os testes cobrem seleção GPS/network, last-known, precisão, jitter, velocidade, distância, Haversine, borda do raio, precedência de coordenadas, fallback textual, migrações, resultado desconhecido, gate assíncrono, deduplicação de fala, parser/evaluator e auto-dismiss. O teste instrumentado do manifesto confirma serviço `exported=false`, FGS de localização, ausência de INTERNET e de localização em background.

## Latência

- o parser/avaliador/overlay não lê mais o TSV no caminho síncrono da oferta;
- o pacote offline é decodificado em executor dedicado e mantido somente em memória;
- o overlay inicial nunca aguarda Geocoder ou pacote offline;
- benchmarks instrumentados mantêm teto de 1 segundo para fixtures e card legível;
- esta rodada não gerou nova medição física de p95 porque nenhum dispositivo apareceu em `adb devices`.

Não se deve interpretar a compilação dos testes instrumentados como medição física. O p95 real captura→overlay precisa ser registrado novamente no Samsung quando o ADB estiver autorizado.

## Limitações e validação física pendente

Na última consulta, `adb devices -l` não listou aparelho. Permanecem pendentes no dispositivo:

- `connectedDebugAndroidTest`;
- permissão precisa, aproximada e negada;
- GPS desligado, fallback network e revogação durante a sessão;
- ação Parar da notificação, tela apagada e serviço já iniciado em background;
- matriz visual 360/390 dp, três fontes e três temas;
- janela real topo/rodapé contra botões Uber/99;
- medição p95 captura→overlay abaixo de 1 segundo;
- remoção do pacote debug e instalação do release assinado.

## Artefatos

- Debug: `app/build/outputs/apk/debug/app-debug.apk`.
- Release: `app/build/outputs/apk/release/app-release.apk`.

O caminho e o hash do release devem ser considerados definitivos somente após o último `assembleRelease` registrado no fechamento desta entrega.
