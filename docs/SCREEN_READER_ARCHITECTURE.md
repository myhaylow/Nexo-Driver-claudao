# Motor de leitura de tela

O Driver Inteligente é estritamente um leitor e analisador local: não toca, não clica e não toma
decisões nos apps monitorados. Os únicos pacotes aceitos pelo leitor são `com.ubercab.driver` e
`com.app99.driver` (mais as famílias 99 legadas já reconhecidas durante a migração).

## Fluxo de produção e migração

```text
AccessibilityEvent
  -> gate/debounce (100 ms no serviço)
  -> AccessibilityService.windows
  -> cópia de dados de árvore (nós são reciclados)
  -> parser Uber ou 99
  -> OCR local recortado, somente se dados críticos não forem encontrados
  -> NormalizedOffer / RideOffer
  -> deduplicação em memória por janela
  -> OfferAnalysisProcessor / OfferEvaluator
  -> WindowManagerOfferOverlay
```

`WindowSnapshot`/`NodeSnapshot` e `RideOffer` são contratos neutros já disponíveis no módulo
`screenreader`. O pipeline em produção ainda usa `NormalizedOffer` como ponte para preservar
regras, preferências e apresentação existentes. A próxima migração deve fazer o orquestrador
consumir diretamente esses snapshots; ela não deve duplicar parser, OCR ou overlay.

## Janelas e concorrência

Cada janela é identificada por `packageName + windowId + displayId`. Os snapshots são copiados
imediatamente e vivem apenas na memória do processo. O serviço já consulta `windows`, em vez de
depender somente de `rootInActiveWindow`; isto permite que o leitor encontre os dois provedores em
split-screen. A janela de deduplicação é de 10 segundos no novo estado por janela e a expiração
tem atraso de 650 ms para evitar remoções durante animações.

## OCR e privacidade

ML Kit Latin é incorporado ao APK e opera no dispositivo. A captura por acessibilidade é apenas
fallback de árvore incompleta; o bitmap é recortado, processado e reciclado no mesmo fluxo, sem
persistência. Não há envio de dados, armazenamento de frames, OCR bruto ou ofertas. Logs de
produção não devem incluir a árvore ou texto do passageiro.

O overlay é somente de apresentação. Ele continua com `FLAG_NOT_TOUCHABLE` e `FLAG_SECURE` e não
faz parsing, OCR ou consultas ao serviço de acessibilidade.

## Validação de release

Em 18/07/2026, `lintDebug`, `assembleRelease`, testes unitários, build debug e o guard de política
foram executados sequencialmente com sucesso. O release passou por R8 e `lintVitalRelease`; o
artefato produzido é não assinado, conforme a configuração segura padrão do repositório. A
assinatura permanece uma etapa local de distribuição e não altera o motor de leitura.
