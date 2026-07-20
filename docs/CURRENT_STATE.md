# Estado atual — leitura de ofertas

## Implementado

- Leitura principal pela árvore de acessibilidade e suporte a múltiplas janelas.
- Eventos limitados a mudanças de janela/conteúdo, com `notificationTimeout` de 80 ms e debounce
  de 100 ms.
- OCR local ML Kit como fallback, com recorte visual, limitação de uma operação e descarte de
  bitmap após o reconhecimento.
- Parsers normalizados para moeda, distância, duração e nota em formatos brasileiros.
- Contratos imutáveis de `WindowSnapshot`, `NodeSnapshot` e `RideOffer`.
- Contratos e testes de deduplicação/expiração efêmeras por janela no módulo `screenreader`.
- Overlay informativo não tocável e seguro; regras atuais de decisão seguem independentes da
  origem do dado.

## Compatibilidade preservada

O parser de produção continua utilizando `NormalizedOffer` e `OfferOcrPipeline`, que já contém
parsers Uber e 99 separados. A ponte evita mudança de fórmulas, preferências e formatos visuais.
Não foi alterada nenhuma regra de aceitação/rejeição manual.

## Limitações conhecidas

- A passagem do serviço para o contrato `RideOffer` ainda é incremental: `NormalizedOffer` é a
  ponte de produção até que todos os consumidores de análise sejam migrados juntos.
- O OCR por acessibilidade atual captura o display para manter compatibilidade com API 30; uma
  evolução futura pode preferir `takeScreenshotOfWindow` quando disponível, usando o `windowId`
  do snapshot e mantendo o mesmo limite de concorrência.
- As imagens externas fornecidas para referência não foram incorporadas ao projeto. Os testes usam
  somente fixtures sanitizadas já versionadas em `app/src/androidTest/assets/offer-fixtures`.

## Gate final de validação

- Data da validação: 18/07/2026.
- Ambiente: Windows, JDK 17, Gradle wrapper 8.13, execução sequencial com `--no-parallel`.
- `testDebugUnitTest --no-parallel`: PASS.
- `assembleDebug --no-parallel`: PASS.
- `lintDebug --no-parallel --stacktrace`: PASS em 2m03s, com 0 erros e 50 warnings preexistentes.
  Relatórios: `app/build/reports/lint-results-debug.html` e
  `app/build/reports/lint-results-debug.xml`.
- `assembleRelease --no-parallel --stacktrace`: PASS; incluiu `lintVitalRelease`, R8 e otimização
  de recursos.
- Guard de política: PASS.
- Artefato gerado: `app/build/outputs/apk/release/app-release-unsigned.apk`.

O APK de release é deliberadamente **não assinado** quando `keystore.properties` não existe. O
build está aprovado, mas a distribuição exige configurar a chave local prevista pelo repositório e
validar o APK assinado. Os 50 warnings de lint não bloqueiam o build; incluem recomendações de
KTX, APIs condicionais e atualizações de dependência, sem supressões adicionadas neste gate.
