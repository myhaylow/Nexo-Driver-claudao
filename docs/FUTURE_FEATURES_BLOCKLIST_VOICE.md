# Features futuras — bloqueio de supermercados e vozes

Este documento define duas evoluções planejadas para o Driver Inteligente. Elas não entram no
pipeline de ofertas até serem implementadas e validadas.

## 1. Lista local de supermercados e hipermercados

### Objetivo

Permitir que o motorista marque ofertas cujo embarque ou destino esteja em um supermercado,
hipermercado, atacarejo ou estabelecimento específico como `BLOQUEAR` ou `ANALISAR`.

O app continuará sem `INTERNET`. A coleta de dados será uma etapa externa de preparação de um
pacote TSV/JSON; o APK receberá somente o pacote validado pelo motorista.

### Fonte recomendada

Usar OpenStreetMap como fonte de preparação, consultando estabelecimentos com `shop=supermarket`
e, quando disponível, `shop=hypermarket`. A classificação `hypermarket` ainda possui variações de
uso na comunidade, portanto o filtro também deve considerar nome e área/localidade. Os dados OSM
são ODbL e o pacote distribuído deve carregar atribuição a OpenStreetMap e seus contribuidores:
https://www.openstreetmap.org/copyright.

Não copiar endereços em massa do Google Maps para dentro do APK. A Places API exige faturamento,
chave e regras específicas de atribuição/cache; o `place_id` pode ser armazenado, mas os detalhes
retornados não devem ser tratados como uma base offline permanente:
https://developers.google.com/maps/documentation/places/web-service/policies.

### Consulta Overpass de preparação

Exemplo para Curitiba e região metropolitana; o arquivo baixado deve ser revisado antes de entrar
no pacote do app:

```overpass
[out:json][timeout:60];
area["name"="Curitiba"][boundary=administrative]->.curitiba;
nwr["shop"~"supermarket|hypermarket"](area.curitiba);
out center tags;
```

Depois da exportação:

1. normalizar nomes (minúsculas, sem acentos, pontuação e duplicidade);
2. remover estabelecimentos pequenos ou incompletos que não sejam relevantes;
3. revisar manualmente nomes de rede e endereços;
4. adicionar aliases como `Condor`, `Jacomar`, `Muffato`, `Max Atacadista`, `Atacadão`, `Assaí`,
   `Carrefour`, `Festval` e `Angeloni`;
5. validar cada ponto com coordenada, cidade e raio;
6. gerar checksum e versão do pacote;
7. importar pelo mesmo fluxo de pacote offline já usado para endereços.

### Contrato sugerido

```tsv
id\tnome\taliases\trede\tcidade\tuf\tlatitude\tlongitude\traio_m\tdecisao\tfonte\tversao
condor-agua-verde\tCondor Água Verde\tcondor|supermercado condor\tCondor\tCuritiba\tPR\t-25.44\t-49.28\t180\tBLOQUEAR\tOSM\t2026-01
```

Regras de precedência:

- endereço exato/raio configurado pelo motorista;
- alias de rede forte;
- categoria genérica, que deve produzir `ANALISAR` em vez de bloqueio automático;
- exceção explícita do motorista.

O histórico não deve guardar a árvore de acessibilidade nem o texto bruto; apenas a decisão
resumida e a regra que coincidiu.

### Testes planejados

- normalização de acentos, caixa e abreviações;
- correspondência por alias sem falsos positivos para `mercado` genérico;
- distância ao ponto e raio configurável;
- precedência entre bloqueio, análise e exceção;
- leitura de pacote inválido, checksum e versão;
- fixture local com pontos de Curitiba e São José dos Pinhais.

## 2. Voz configurável e voz local masculina

### Objetivo

Permitir selecionar a voz usada nas frases de decisão e testar a voz antes de ativá-la:

```text
aceitar corrida, R$ 13,58
analisar corrida, R$ 14,07
recusar corrida, R$ 8,50
```

### Limites técnicos

O Android expõe as vozes instaladas pelo mecanismo TTS com `getVoices()`, permite aplicar uma
voz com `setVoice(Voice)` e informa se ela exige rede com `isNetworkConnectionRequired()`:
https://developer.android.com/reference/android/speech/tts/TextToSpeech e
https://developer.android.com/reference/android/speech/tts/Voice.

O aplicativo deve filtrar vozes `pt-BR` que não exigem conexão. A API não garante um campo
padronizado de gênero; portanto a interface deve mostrar o nome da voz, permitir prévia e deixar
o motorista escolher a opção que soe masculina/natural no aparelho.

### Modelo de configuração

```text
voiceId: nome estável retornado por Voice.getName()
locale: pt-BR
offlineOnly: true
speechRate: 0.95
pitch: 0.90
enabled: true
```

Fallback: se a voz selecionada desaparecer, voltar para a voz padrão pt-BR e informar o motorista
sem interromper a análise da oferta. Vozes neurais que dependam de download ou rede não podem ser
selecionadas enquanto a garantia de ausência de internet estiver ativa.

### UI planejada

- lista de vozes locais pt-BR;
- indicador `offline`;
- botão `Ouvir exemplo`;
- controle de velocidade;
- controle de tom;
- toggle de fala por decisão;
- restauração da voz padrão;
- aviso quando nenhuma voz local pt-BR estiver instalada.

### Testes planejados

- seleção e persistência por `voiceId`;
- filtro de vozes que exigem rede;
- fallback quando a voz não existe mais;
- prévia sem alterar a deduplicação das ofertas;
- frases verde/amarelo/vermelho preservadas;
- `stop()` e `shutdown()` sem vazamento;
- teste instrumentado em Samsung/API 36 com o mecanismo TTS instalado.

## Ordem recomendada

1. Criar o contrato e validador do pacote de bloqueio.
2. Importar uma primeira base OSM revisada para Curitiba/RMC.
3. Integrar a decisão de bloqueio ao `OfferEvaluator`, sem automatizar toques.
4. Criar o catálogo de vozes e a tela de prévia.
5. Integrar a voz selecionada ao `OfferDecisionSpeaker`.
6. Executar unitários, lint, instrumentação e validação manual no aparelho.
