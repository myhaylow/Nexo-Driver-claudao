# Codex Orchestration - Driver Inteligente

## Objetivo

Esta configuracao transforma os agentes do Codex em um catalogo de especialistas para o projeto Driver Inteligente. A escolha de agente, modelo, esforco e validacao deve ser feita automaticamente pela classificacao da tarefa, com prioridade para velocidade, qualidade, baixo desperdicio de tokens e seguranca operacional.

## Fontes confirmadas

Verificacao feita nesta instalacao:

| Item | Resultado confirmado |
| --- | --- |
| Config local de projeto | `.codex/config.toml` e suportado em projetos confiaveis. |
| Arquivos de agentes | `.codex/agents/*.toml` e suportado para agentes customizados de projeto. |
| Campos obrigatorios de agente | `name`, `description`, `developer_instructions`. |
| Campos opcionais usados | `nickname_candidates`, `model`, `model_reasoning_effort`, `sandbox_mode`. |
| Config global de subagentes | `[agents]` com `max_threads`, `max_depth`, `job_max_runtime_seconds`, `interrupt_message`. |
| Modelo por agente | Suportado via `model` no arquivo do agente. |
| Esforco por agente | Suportado via `model_reasoning_effort` no arquivo do agente. |
| CLI direta `codex --help` | O alias do WindowsApps retornou acesso negado, mas o binario em `CODEX_CLI_PATH` funcionou como `codex-cli 0.144.2`. |

Modelos confirmados em `C:\Users\mdesp\.codex\models_cache.json`:

| Familia | Slug | Esforcos suportados |
| --- | --- | --- |
| Sol | `gpt-5.6-sol` | `low`, `medium`, `high`, `xhigh`, `max`, `ultra` |
| Terra | `gpt-5.6-terra` | `low`, `medium`, `high`, `xhigh`, `max`, `ultra` |
| Luna | `gpt-5.6-luna` | `low`, `medium`, `high`, `xhigh`, `max` |
| GPT-5.5 | `gpt-5.5` | `low`, `medium`, `high`, `xhigh` |
| GPT-5.4 | `gpt-5.4` | `low`, `medium`, `high`, `xhigh` |

## Arquitetura implementada

Arquivos criados:

```text
AGENTS.md
.codex/config.toml
.codex/agents/explorer.toml
.codex/agents/android_worker.toml
.codex/agents/ui_compose.toml
.codex/agents/ocr_parser.toml
.codex/agents/architect.toml
.codex/agents/debugger.toml
.codex/agents/tester.toml
.codex/agents/privacy_security.toml
.codex/agents/reviewer.toml
.codex/agents/release_gate.toml
.codex/hooks.json
.codex/hooks/driver_policy_guard.ps1
.codex/scripts/simulate-routing.ps1
.codex/scripts/validate-orchestration.ps1
docs/CODEX_ORCHESTRATION.md
```

`AGENTS.md` contem a politica duravel do repositorio: classes A-C, limites de subagentes, garantias obrigatorias, autonomia permitida e validacao padrao.

`.codex/config.toml` define o default de projeto como `gpt-5.6-terra` com `medium`, habilita metas e multiagentes, limita concorrencia normal a quatro threads, limita profundidade a uma camada e usa `workspace-write` com rede desabilitada no sandbox de escrita.

`.codex/agents/*.toml` define os dez especialistas principais. Os agentes antigos `metric_tests`, `privacy_lifecycle_audit` e `rating_regression` foram consolidados como responsabilidades internas de `tester`, `privacy_security` e `ocr_parser`.

`.codex/hooks.json` registra hooks locais em `PostToolUse` e `Stop`. O comando chamado e `.codex/hooks/driver_policy_guard.ps1`, que checa mecanicamente as garantias centrais do app depois de comandos/patches e ao finalizar o turno. Por serem hooks locais, o Codex deve pedir revisao/confianca na proxima sessao antes de executa-los automaticamente.

`.codex/scripts/validate-orchestration.ps1` valida a configuracao, os dez agentes, a politica A-C, o guard de seguranca e a simulacao de roteamento. Use `-RunGradle` para incluir a bateria Gradle e `-CheckAdb` para consultar dispositivo autorizado.

## Politica de roteamento

| Classe | Uso | Modelo/esforco | Agentes provaveis |
| --- | --- | --- | --- |
| A - trivial | busca, texto, import, doc simples | Luna baixo ou Terra medio | `explorer`, `android_worker` |
| B - normal | feature delimitada, teste, UI, parser bem definido | Terra medio | `android_worker`, `ui_compose`, `ocr_parser`, `tester` |
| C - complexa | multiplos modulos, contratos, lifecycle, OCR estrutural, privacidade, seguranca, MediaProjection, release ou bug sem causa conhecida | Sol max/high para decisao; Terra para execucao delimitada | `architect`, `debugger`, `privacy_security`, `release_gate`, implementador, `tester`, `reviewer` |

## Catalogo de agentes

| Agente | Modelo | Esforco | Sandbox | Funcao |
| --- | --- | --- | --- | --- |
| `explorer` | `gpt-5.6-luna` | `low` | `read-only` | Busca, inventario e impacto. |
| `android_worker` | `gpt-5.6-terra` | `medium` | `workspace-write` | Implementacao Android/Kotlin delimitada. |
| `ui_compose` | `gpt-5.6-terra` | `medium` | `workspace-write` | UI Compose e overlay. |
| `ocr_parser` | `gpt-5.6-terra` | `medium` | `workspace-write` | OCR, parser, layouts Uber/99. |
| `architect` | `gpt-5.6-sol` | `max` | `workspace-write` | Arquitetura e contratos transversais. |
| `debugger` | `gpt-5.6-terra` | `high` | `workspace-write` | Causa raiz e regressao. |
| `tester` | `gpt-5.6-terra` | `medium` | `workspace-write` | Testes, fixtures e benchmarks. |
| `privacy_security` | `gpt-5.6-sol` | `high` | `workspace-write` | Privacidade e seguranca independente. |
| `reviewer` | `gpt-5.6-terra` | `high` | `read-only` | Revisao independente. |
| `release_gate` | `gpt-5.6-sol` | `high` | `workspace-write` | Build, lint, R8, assinatura e gate final. |

## Fluxos

Tarefa normal:

```text
root -> explorer quando necessario -> implementador -> tester ou reviewer -> root consolida
```

Tarefa critica:

```text
root -> architect -> implementador -> tester -> privacy_security -> release_gate -> root consolida
```

## Simulacoes de roteamento

| Tarefa simulada | Classe | Agentes escolhidos | Modelo esperado | Evidencia de politica |
| --- | --- | --- | --- | --- |
| Localizar onde o overlay define flags | A | `explorer` | Luna baixo | Nao usa Sol e e somente leitura. |
| Ajustar texto em tela de settings | B | `ui_compose`, opcional `tester` | Terra medio | Mudanca delimitada de UI. |
| Corrigir parser de 99 Negocia com fixture | B/C | `ocr_parser`, `tester` | Terra medio; Sol somente se estrutural | Parser + regressao, sem todos os agentes. |
| Investigar falha intermitente de MediaProjection | C | `debugger`, `architect`, `privacy_security` | Terra alto + Sol alto/max | Lifecycle e permissao sensivel. |
| Preparar release | C | `release_gate`, `privacy_security`, opcional `reviewer` | Sol alto + Terra alto | Gate independente do implementador. |

## Permissoes

Permitido dentro do workspace:

- ler e editar arquivos do projeto;
- criar/remover arquivos do projeto quando justificado;
- executar Gradle, testes, lint e builds;
- usar ADB se houver dispositivo autorizado;
- coletar logs necessarios;
- criar fixtures sanitizadas;
- atualizar documentacao;
- corrigir problemas encontrados durante a implementacao.

Exige aprovacao explicita:

- apagar arquivos fora do workspace;
- modificar configuracoes globais do sistema;
- acessar credenciais externas;
- publicar releases;
- fazer push ou abrir pull request;
- alterar contas;
- comandos destrutivos fora do projeto;
- automatizar aceite/recusa na Uber/99;
- adicionar `INTERNET` ao app.

## Validacao recomendada

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .codex\scripts\validate-orchestration.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .codex\hooks\driver_policy_guard.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .codex\scripts\simulate-routing.ps1 -Validate
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
adb devices
```

`adb devices` deve ser apenas uma consulta. Nao instalar nem alterar dispositivo sem autorizacao especifica.

## Validacao executada nesta configuracao

| Checagem | Resultado |
| --- | --- |
| Sintaxe estrutural dos TOML | OK para `.codex/config.toml` e os 10 arquivos em `.codex/agents/`. |
| Hooks locais | `.codex/hooks.json` parseou como JSON valido. |
| Guard mecanico | `.codex/hooks/driver_policy_guard.ps1` passou. |
| Simulacao de roteamento | `.codex/scripts/simulate-routing.ps1 -Validate` passou. |
| Validacao da orquestracao | `.codex/scripts/validate-orchestration.ps1` passou. |
| `codex doctor --summary` com `--strict-config` | OK: config carregada, auth configurado, MCP OK, sandbox restrito e rede restrita. |
| `codex debug prompt-input` | Confirmou que `AGENTS.md` do projeto entra no contexto do Codex. |
| Modelos locais | Confirmados em `models_cache.json` e via `codex debug models`. |
| Roteamento simulado | Registrado na tabela de simulacoes; Classe A nao usa Sol e a politica impede disparar todos os agentes. |
| Garantias do app | `AccessibilityService` permitido somente para leitura; ausencia de `INTERNET`, `performAction`, `dispatchGesture`; `FLAG_NOT_TOUCHABLE` e `FLAG_SECURE` presentes. |
| Gradle | `testDebugUnitTest`, `lintDebug`, `assembleDebug` e `assembleRelease` passaram. |
| ADB | `adb devices` encontrou `RQCW2070A5P device`; nenhuma instalacao foi feita. |

## Limitacoes conhecidas

- O alias `codex` resolvido pelo WindowsApps retornou "Acesso negado"; use o binario de `CODEX_CLI_PATH` quando precisar validar pela CLI local.
- A CLI 0.144.2 nao mostrou um subcomando dedicado para listar agentes customizados. A evidencia local disponivel e: esquema oficial do manual, arquivos `.codex/agents/*.toml`, `doctor` carregando a config, e `debug prompt-input` carregando o `AGENTS.md`.
- A configuracao de projeto so e carregada quando o projeto esta confiavel. Este workspace aparece confiavel no `C:\Users\mdesp\.codex\config.toml` global.
- Alteracoes em `AGENTS.md` e `.codex/config.toml` normalmente sao lidas no inicio de uma nova sessao ou nova tarefa.
- Subagentes herdam tambem overrides runtime do turno pai; portanto permissoes escolhidas no compositor podem prevalecer sobre o arquivo do agente.
