# Base OSM de supermercados — Curitiba

Esta pasta contém uma coleta piloto para revisão humana. Ela ainda não é consumida pelo APK e
nenhuma entrada deve bloquear ofertas automaticamente antes da revisão.

Fonte: OpenStreetMap contributors, consultado pelo Nominatim. OSM é distribuído sob ODbL; manter a
atribuição ao distribuir ou transformar os dados: https://www.openstreetmap.org/copyright.

Consulta reproduzível:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .codex\scripts\collect-osm-blocklist.ps1
```

O script consulta cada rede separadamente, respeita um intervalo de um segundo, limita a caixa
geográfica de Curitiba e deduplica pelo par `osm_type/osm_id`. O resultado é uma lista candidata,
com `decisao=ANALISAR`, raio inicial de 180 metros e fonte identificada. A decisão deve ser alterada
para `BLOQUEAR` somente após confirmação de que o estabelecimento é grande e costuma causar o
comportamento que o motorista deseja evitar.

A coleta de 15/07/2026 gerou 39 pontos candidatos. Ela inclui resultados ranqueados pelo Nominatim
e sete IDs verificados mantidos como sementes para Atacadão e Assaí quando o ranqueamento omitir
uma filial. Esses pontos ainda exigem revisão visual e não são carregados automaticamente pelo APK.
