# Apoio a mapas off-line e resolução de endereços

O Driver Inteligente não acessa arquivos privados, tiles ou bancos de dados do Google Maps.

## Fluxo do destino

1. O motorista informa um endereço.
2. `GeocoderDestinationResolver` valida o texto, acrescenta `Brasil` quando necessário e executa `Geocoder` fora da thread principal.
3. O primeiro resultado é convertido em endereço padronizado, latitude e longitude.
4. A resolução é salva no cache privado do aplicativo com status e timestamp.
5. `GoogleMapsOfflineIntent` cria uma URI `geo:` e tenta abrir `com.google.android.apps.maps`; se não estiver instalado, usa qualquer app compatível com `ACTION_VIEW`.
6. O motorista conclui manualmente o download em **Mapas off-line** no Google Maps.

O aplicativo nunca afirma que o mapa foi baixado, porque não recebe essa confirmação.

## Ofertas com destino textual

`OfferDestinationGeocoder` é um enriquecedor assíncrono que:

- preserva coordenadas já presentes;
- ignora textos genéricos;
- evita consultas concorrentes e repete falhas somente após intervalo;
- mantém o texto quando não há Geocoder ou conectividade;
- acrescenta as coordenadas apenas em memória na oferta, para uma nova análise do pipeline.

O cache próprio armazena somente endereço, coordenadas, status e horário. Não armazena tiles, imagens,
árvores de acessibilidade ou OCR bruto. A comparação geográfica continua funcionando com o pacote OSM
local e não depende do mapa off-line do Google.
