# Relatório de engenharia reversa estática — cards de corrida

Data da análise: 20/07/2026  
Escopo: como os APKs distribuídos estruturam e apresentam cards de oferta/corrida ao motorista. Esta é uma análise estática de manifesto, recursos e DEX; não houve login, interceptação de rede, alteração dos APKs ou automação de interação.

## Amostras analisadas

| Aplicativo | Pacote | Versão | SDK alvo | SHA-256 |
|---|---|---:|---:|---|
| 99 Motorista | `com.app99.driver` | 7.10.38 (1207103823) | 35 | `4523CA5BCA128E79341875ADF20235E08C967F85994430D2F1371BB1FBDDBF27` |
| Uber Driver | `com.ubercab.driver` | 4.588.10000 (294712) | 36 | `1ECF25AE41E9F07E057D3B35D93071194F7717177F01FB38C1B0AD311524E5F1` |

## Resumo executivo

Os dois aplicativos têm uma camada nativa para o ciclo de ofertas, porém com padrões de apresentação distintos:

| Aspecto | 99 Motorista | Uber Driver |
|---|---|---|
| Unidade principal | Pedido amplo (`BroadOrder`) | Oferta antecipada (`UpfrontOffer`) |
| Ponto de entrada | Sobre o mapa/home e também seletor de viagens | Job board/bandeja de cards sobre a home |
| Composição | View nativa de pedido com rotas V2/V3; parte do seletor em Flutter | Containers e componentes nativos/Compose, com layout parcialmente orientado por dados (SDUI) |
| Interação | Aceitar/"disputar" pedido, recusar, aguardar aceite do passageiro | Aceitar/rejeitar, expandir detalhes, oferta expira, seleção de card/lance |
| Estado visual inferido | Exibição, carregamento de rota/ETA, tentativa, sucesso/falha, cancelamento, recusas | Novo pedido, foco/sobreposição, seleção, expiração, aceito/rejeitado/removido, detalhes em tela cheia |

## 99 Motorista

### Arquitetura identificada

O fluxo principal está no namespace `com.didiglobal.biz.broadorder`. As rotas internas encontradas incluem:

- `/main/broadorder` — grupo do fluxo de pedidos na tela principal;
- `/broadorder/ordershowv2` e `/broadorder/ordershowv3` — duas versões de apresentação do pedido;
- `/broadorder/refused`, `/broadorder/taxirefused` e `/broadorder/refusedwithoutduty` — variações de recusa;
- `/broadorder/waitpaxaccept` — estado após a ação do motorista, enquanto aguarda o passageiro;
- `/trippicker` e `/trippickerdetail` — seletor/lista e detalhe de viagens.

As classes `OrderShowViewV2` e `OrderShowViewV3` são views Android nativas. Ambas recebem `BroadOrder`; a V2 expõe `bindData()` e `setupBargain(...)`, e a V3 contém listeners de clique, handler, callback de mudança de layout e temporizador. Isso evidencia que o card/painel é ligado a um modelo de pedido, responde a clique e atualiza seu ciclo de vida visualmente.

O fluxo é dirigido por push: foram localizadas mensagens de tentativa/resultado (`DriverOrderStrived`, `OspreyOrderStriveSucc`, `OspreyOrderStriveFail`) e os tipos de resultado `OrderStrived` e `OrderCancelled`. O app também contém `BroadOrderViewModel` com `LiveData`, confirmando atualização reativa do conteúdo apresentado.

### Como o card é mostrado

1. Com o motorista disponível, uma oferta ampla pode abrir o fluxo `BroadOrder` dentro da home/mapa.
2. O pedido é renderizado por `OrderShowViewV2` ou `OrderShowViewV3`, acoplado ao `BroadOrderContainerType`; há rotas específicas de fragmento, portanto não é apenas uma notificação solta.
3. A view vincula dados do pedido e carrega a rota no mapa. As chaves `eta_value_pickup`, `eta_value_sendoff` e `eta_value_broad_order` indicam que o tempo até embarque/destino é parte do conteúdo calculado/exibido.
4. A ação primária é uma disputa/aceite: há métodos `setGrabOrderBtnEnabled`, `setGrabOrderBtnText`, `setForbiddenGrabCountBtnText`, `setGrabOrderBtnOnclickListener` e `setupBargain`. O botão pode, portanto, mudar de texto/estado e ser bloqueado conforme regras do pedido.
5. Depois da tentativa, o fluxo recebe êxito, falha ou cancelamento; se necessário vai para a tela de espera do aceite do passageiro. Há também telas de recusa dedicadas.

### Lista/seletor de viagens

O `TripPicker` é um segundo formato de card/listagem. Há atividades de lista e detalhe, uma implementação Flutter e APIs que reportam `currentOrderListCount`, pedem detalhe e sincronizam IDs de ofertas em correspondência. Assim, o 99 não depende exclusivamente de um único card modal: ele combina o pedido imediato do mapa com uma superfície de seleção de viagens quando habilitada por configuração.

## Uber Driver

### Arquitetura identificada

O APK expõe uma arquitetura explícita de quadro de ofertas:

- `driveroffersjobboard` — board de ofertas;
- `CardsTrayV2View` — bandeja de cards;
- `OfferStateStream` — fluxo de estado de oferta;
- `UpfrontOfferInfo` e `DriverOffer` — modelos da oferta;
- `SelectedBidIndexStore` — mantém o card/lance selecionado;
- `DriverOffersJobBoard...Focus`, `...Browse`, `...Overlay` e `...Invalidation` — módulos de foco, navegação, sobreposição e invalidação;
- `OfferExperienceClient` e `EarnerTrip...SduiPartialUpdate` — conteúdo/atualizações guiadas pelo backend.

Os recursos confirmam as superfícies: `driver_offers_job_board_recycler_view`, `driver_offers_job_board_content_container`, barra lateral, toolbar, badge/pill de novas solicitações e um overlay. Isto caracteriza uma lista/bandeja navegável, não só um card isolado sobre o mapa.

### Como o card é mostrado

1. O job board é apresentado como container com `RecyclerView`; a bandeja pode receber uma nova solicitação, atualizando o texto/contador de novos pedidos e o pill/badge.
2. Há modos de navegação e foco. Os módulos `Browse` e `Focus`, junto dos recursos de toolbar e sidebar, indicam seleção de um item sem necessariamente sair da lista.
3. Ao selecionar uma oferta, o card de oferta antecipada pode ser exibido compacto ou expandido: `ub__upfront_offer_bundle_collapsed_container`, `...expanded_container` e `...static_container`.
4. O conteúdo detalhado tem cabeçalho, título, subtítulo, tags, itinerário, mapa, etiqueta do mapa, itens de informação e política/link. Os recursos `upfront_offer_pickup_label` e `upfront_offer_dropoff_label` indicam pontos de coleta e desembarque; `upfront_offer_map_card_view` confirma o mapa dentro do card.
5. A ação é explícita e auditável: existem botão de aceite, layout do botão, métricas de impressão do botão de aceitar/cancelar e eventos `UPFRONT_OFFER_CREATED`, `...ACCEPTED`, `...REJECTED`, `...REMOVED` e `...REMOVED_WITH_FINALIZATION_CUTOFF_TIME_REACHED`.
6. Se o card expirar, o board mostra mensagem de expiração. Há ainda variantes de corridas agendadas, oferta agrupada (`bundle`) e despacho direcionado com entrada de PIN.

### Conteúdo variável do servidor

A presença de componentes SDUI e chaves como `UPFRONT_OFFER_HEADER_TITLE_RICHTEXT`, `...SUBTITLE_RICHTEXT`, itens genéricos de informação e atualização parcial revela que texto, tags e blocos de detalhe podem ser compostos/configurados remotamente. Portanto, a hierarquia visual é estável, mas quais blocos aparecem pode variar por produto, cidade, experimento e tipo de corrida.

## Comparação prática para reprodução de UX

Para uma implementação própria inspirada nesses padrões, a combinação mais fiel é:

1. Um painel de oferta priorizado sobre o mapa, com rota/ETA e CTA em estado bem visível (padrão 99).
2. Uma bandeja com várias ofertas, contador de novas solicitações e seleção persistente (padrão Uber).
3. Card expansível para detalhes: cabeçalho, tags, coleta/desembarque, mapa, métricas da corrida e regras/política.
4. Máquina de estados explícita: `nova -> visível -> selecionada -> aceitando -> aceita | rejeitada | expirada | removida`; o CTA deve desabilitar durante a transição e o card deve desaparecer/atualizar após o resultado.
5. Atualização reativa e idempotente: cada oferta precisa de identificador, timestamp/expiração e proteção contra resultado tardio de push/rede.

## Limitações e grau de confiança

Alta confiança: nomes de rotas, classes, recursos, máquinas de estado e componentes citados foram obtidos diretamente dos APKs. Média confiança: disposição exata, cores, tipografia e ordem final de cada campo, pois podem ser alteradas remotamente e não foram observadas em uma sessão autenticada. Não se conclui deste relatório quais dados comerciais específicos são sempre exibidos, nem se tenta reproduzir mecanismos proprietários de despacho.

## Método

Foram lidos manifesto, tabela de recursos e cadeias/classes DEX dos APKs localmente. A análise não extraiu segredos, não contornou autenticação, não modificou as amostras e não realizou captura de tráfego.
