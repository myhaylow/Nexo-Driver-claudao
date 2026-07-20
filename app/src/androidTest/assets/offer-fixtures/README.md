# Fixtures de oferta (não versionadas)

`OfferScreenshotFixtureTest` roda o reconhecedor ML Kit e o parser de produção contra capturas
reais de cards de oferta. As imagens **não entram no git** e o teste é pulado quando elas não
estão presentes.

## Por quê

Um card de oferta real mostra o endereço de embarque do passageiro **com número**, junto da nota
e da contagem de corridas dele. Isso é dado pessoal de alguém que não participou dessa decisão, e
a combinação endereço + nota + nº de corridas é estreita o bastante para identificar a pessoa.

O app inteiro é construído sobre a regra de que nada sai do dispositivo — sem permissão de
`INTERNET`, sem SDK de rede. Versionar essas capturas faria do repositório o único ponto por onde
o projeto vaza exatamente aquilo que o código foi feito para nunca vazar.

## Como rodar o teste localmente

Coloque os arquivos abaixo nesta pasta. Os valores esperados estão em `FIXTURES`, dentro de
`OfferScreenshotFixtureTest`; se usar capturas próprias, ajuste-os para o que a sua tela mostra.

| Arquivo | Plataforma |
|---|---|
| `uber_dark_1358.jpg` | Uber |
| `uber_light_1407.jpg` | Uber |
| `ninety_nine_dark_850.jpg` | 99 |
| `ninety_nine_dark_990.jpg` | 99 |
| `ninety_nine_negocia_2092.jpg` | 99 Negocia |

O teste só precisa do **layout** do card, não dos endereços verdadeiros. Capturas com os endereços
borrados, ou sintéticas com "Rua Exemplo, 100", validam o parser igualmente bem — e são o formato
preferível se um dia essas fixtures forem compartilhadas.
