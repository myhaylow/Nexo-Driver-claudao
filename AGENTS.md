# AGENTS.md - Driver Inteligente

## Principio central

Este projeto usa agentes Codex como um catalogo de especialistas, nao como um pipeline obrigatorio. Nunca dispare todos os agentes por padrao. Para cada tarefa, classifique o risco, escolha poucos agentes e mantenha o contexto enviado a cada um no menor conjunto util de arquivos, contratos e requisitos.

Os agentes atuais `metric_tests`, `privacy_lifecycle_audit` e `rating_regression` foram consolidados como responsabilidades internas dos agentes `tester`, `privacy_security` e `ocr_parser`/`tester`.

## Classificacao obrigatoria

Antes de trabalhar, classifique internamente a tarefa:

- Classe A - trivial: localizar arquivo, ajustar texto, corrigir import, pequena configuracao ou documentacao simples. Use Luna ou Terra. Nao use Sol.
- Classe B - normal: funcionalidade delimitada, correcao com causa provavel, alteracao em um modulo, testes ou UI Compose localizada. Use Terra por padrao.
- Classe C - complexa: multiplos modulos, mudanca de contratos, persistencia, concorrencia, pipeline OCR, lifecycle, refatoracao estrutural ou bug sem causa conhecida. Use `architect` ou `debugger` com Sol somente nas partes que exigem raciocinio profundo; implementacoes delimitadas podem voltar para Terra.

## Politica de subagentes

- Padrao por tarefa: zero a dois subagentes.
- Maximo simultaneo normal: quatro.
- Profundidade maxima: uma camada.
- Nao forme cadeias profundas de delegacao.
- Apenas um agente pode editar determinado arquivo na mesma etapa.
- Agentes de exploracao devem trabalhar em modo somente leitura.
- Revisao, seguranca e release devem ser independentes do agente que implementou a mudanca.
- Use subagentes quando houver trabalho independente real: exploracao, testes, logs, revisao, seguranca ou release.
- Nao envie o contexto completo do repositorio para todos os agentes.

## Catalogo de agentes

- `explorer`: leitura, inventario, mapa de arquivos, simbolos, dependencias e impacto. Use Luna baixo. Somente leitura.
- `android_worker`: implementacao Kotlin/Android delimitada. Use Terra medio.
- `ui_compose`: telas Compose, estado, responsividade, acessibilidade visual e overlay. Use Terra medio.
- `ocr_parser`: captura, OCR, ordenacao de blocos, deduplicacao, Uber, 99 e 99 Negocia. Use Terra; Sol apenas para mudanca estrutural ou falha dificil.
- `architect`: arquitetura, contratos, migracoes e decisoes transversais. Use Sol no maior esforco suportado.
- `debugger`: reproducao, hipoteses, causa raiz e teste de regressao. Use Terra para falhas normais e Sol para concorrencia, lifecycle ou falha sem causa clara.
- `tester`: testes unitarios, instrumentados, fixtures, benchmark e regressao. Use Terra medio.
- `reviewer`: revisao independente de bugs, regressoes, contratos quebrados e codigo morto. Use Terra alto.
- `release_gate`: build, testes, lint, R8, assinatura, artefato e checklist final. Use Sol alto.

## Fluxos recomendados

Para tarefas normais:

```text
root -> explorer, quando necessario -> agente implementador -> tester ou reviewer -> root consolida
```

Para tarefas criticas:

```text
root -> architect -> implementador -> tester -> release_gate -> root consolida
```

## Autonomia permitida

Codex pode ler e editar arquivos dentro do workspace, criar e remover arquivos do projeto quando justificado, executar Gradle, testes, lint, builds debug/release, ADB quando houver dispositivo autorizado, coletar logs necessarios, criar fixtures sanitizadas, atualizar documentacao e corrigir problemas encontrados durante a implementacao.


## Validacao padrao

Use validacao proporcional ao risco. Para mudancas relevantes, prefira:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .codex\scripts\validate-orchestration.ps1
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

Quando houver dispositivo Android autorizado, `adb devices` pode ser usado para confirmar disponibilidade. Nao instale nem altere o dispositivo se ele nao estiver autorizado.

## Guardrails mecanicos

- `.codex/hooks.json` registra hooks `PostToolUse` e `Stop` para executar `.codex/hooks/driver_policy_guard.ps1`.
- Hooks locais precisam ser revisados/confiados pelo Codex na proxima sessao antes de rodarem automaticamente.
- Para validar manualmente a orquestracao, rode `.codex\scripts\validate-orchestration.ps1`.
- Para auditar somente a politica do app, rode `.codex\hooks\driver_policy_guard.ps1`.
- Para simular roteamento A-D, rode `.codex\scripts\simulate-routing.ps1 -Validate`.
