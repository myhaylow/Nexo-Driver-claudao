# Pacote offline de endereços

O filtro **Em direção ao destino** usa apenas o destino configurado pelo motorista e este arquivo local. Não usa o recurso de destino da Uber nem envia endereços para a internet.

Use UTF-8, quebras de linha `LF` e colunas separadas por tabulação:

```text
DRIVER_INTELIGENTE_OFFLINE_ADDRESS	1
META	Nome do pacote	1.0.0	Cidade
PLACE	id-unico	Endereço completo	-25.428400	-49.273300	Alias opcional
```

Cada `PLACE` pode incluir aliases adicionais em novas colunas. Endereços ambíguos, inválidos ou ausentes são tratados como desconhecidos: a oferta não recebe indicação falsa de que vai em direção à casa.

O arquivo de exemplo mínimo está em `offline-address-pack-example.tsv`.

## Pack base do Paraná (`osm/parana-places.tsv`)

Pack real para servir de base, com 970 lugares (cidades, vilas e povoados) de todo o Paraná em
granularidade municipal. Ele decodifica e valida pelo `OfflineAddressPackageTsvCodec`
(`ParanaAddressPackDecodeTest` garante isso no CI).

- **Fonte:** OpenStreetMap, via Overpass API — `node["place"~"^(city|town|village)$"]` na área
  `ISO3166-2=BR-PR`.
- **Licença/atribuição:** dados © colaboradores do OpenStreetMap, sob **ODbL**
  (https://www.openstreetmap.org/copyright). A atribuição vive aqui (o formato TSV não tem linha de
  comentário — toda linha após `META` é um `PLACE`).
- **Regenerar / expandir:** repetir a consulta Overpass e formatar como `PLACE\t<id>\t<nome>\t<lat>\t<lon>`
  com `id` único (`pr-osm-<node_id>`). Para incluir bairros de uma cidade específica, some
  `place~"^(suburb|neighbourhood)$"` restrito à área daquela cidade (o estado inteiro em bairro seria
  grande demais e ruidoso para o filtro de destino).
