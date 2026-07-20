# Pacote offline de endereços

O filtro **Em direção ao destino** usa apenas o destino configurado pelo motorista e este arquivo local. Não usa o recurso de destino da Uber nem envia endereços para a internet.

Use UTF-8, quebras de linha `LF` e colunas separadas por tabulação:

```text
DRIVER_INTELIGENTE_OFFLINE_ADDRESS	1
META	Nome do pacote	1.0.0	Cidade
PLACE	id-unico	Endereço completo	-25.428400	-49.273300	Alias opcional
```

Cada `PLACE` pode incluir aliases adicionais em novas colunas. Endereços ambíguos, inválidos ou ausentes são tratados como desconhecidos: a oferta não recebe indicação falsa de que vai em direção à casa.

O arquivo de exemplo está em `offline-address-pack-example.tsv`.
