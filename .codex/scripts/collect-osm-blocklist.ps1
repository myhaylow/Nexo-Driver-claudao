param(
    [string]$OutputPath = (Join-Path $PSScriptRoot '..\..\docs\osm\curitiba-supermarket-blocklist.tsv'),
    [string]$Version = '2026-07-15'
)

$ErrorActionPreference = 'Stop'
$headers = @{ 'User-Agent' = 'DriverInteligente-OSM-collector/0.1 (local preparation)' }
$bbox = @(-25.60, -49.40, -25.30, -49.10)
$invariant = [Globalization.CultureInfo]::InvariantCulture
$brands = @(
    @{ Name = 'Condor'; Aliases = 'condor|supermercado condor' },
    @{ Name = 'Jacomar'; Aliases = 'jacomar|supermercado jacomar' },
    @{ Name = 'Muffato'; Aliases = 'muffato|super muffato' },
    @{ Name = 'Max Atacadista'; Aliases = 'max atacadista|max' },
    @{ Name = 'Atacadão'; Aliases = 'atacadao|atacadão' },
    @{ Name = 'Assaí'; Aliases = 'assai|assaí|assai atacadista|assaí atacadista' },
    @{ Name = 'Carrefour'; Aliases = 'carrefour|carrefour hipermercado' },
    @{ Name = 'Festval'; Aliases = 'festval' },
    @{ Name = 'Angeloni'; Aliases = 'angeloni' },
    @{ Name = 'Nacional'; Aliases = 'nacional supermercado' }
)

$rows = @{}
foreach ($brand in $brands) {
    $query = "$($brand.Name) Curitiba Paraná Brasil"
    $viewbox = "$($bbox[3]),$($bbox[2]),$($bbox[1]),$($bbox[0])"
    $uri = 'https://nominatim.openstreetmap.org/search?format=jsonv2&limit=50&bounded=1' +
        "&viewbox=$viewbox&q=$([Uri]::EscapeDataString($query))"
    try {
        $results = Invoke-RestMethod -Uri $uri -Headers $headers -TimeoutSec 30
        foreach ($result in $results) {
            $lat = [double]$result.lat
            $lon = [double]$result.lon
            if ($lat -lt $bbox[0] -or $lat -gt $bbox[2] -or $lon -lt $bbox[1] -or $lon -gt $bbox[3]) { continue }
            $isRetailType = $result.type -in @('supermarket', 'hypermarket', 'department_store', 'retail', '商店')
            $hasBrandName = $result.display_name -match [Regex]::Escape($brand.Name)
            if (-not $isRetailType -and -not $hasBrandName) { continue }
            if ([string]::IsNullOrWhiteSpace($result.osm_type) -or [string]::IsNullOrWhiteSpace($result.osm_id)) { continue }

            $key = "$($result.osm_type)-$($result.osm_id)"
            if ($rows.ContainsKey($key)) { continue }
            $displayName = ($result.display_name -split ',')[0].Trim()
            $rows[$key] = [ordered]@{
                id = "osm-$key"
                nome = $displayName
                aliases = $brand.Aliases
                rede = $brand.Name
                cidade = 'Curitiba'
                uf = 'PR'
                latitude = [string]::Format($invariant, '{0:0.##########}', $lat)
                longitude = [string]::Format($invariant, '{0:0.##########}', $lon)
                raio_m = '180'
                decisao = 'ANALISAR'
                fonte = "OpenStreetMap Nominatim ($($result.osm_type)/$($result.osm_id))"
                versao = $Version
            }
        }
    } catch {
        Write-Warning "Falha ao consultar '$query': $($_.Exception.Message)"
    }
    Start-Sleep -Seconds 1
}

# Verified OSM results retained as a deterministic seed when Nominatim ranking temporarily omits
# a known branch from a brand search. Re-run the normal query later to refresh these IDs/details.
$verifiedFallbacks = @(
    @{ Brand = 'Atacadão'; Aliases = 'atacadao|atacadão'; OsmType = 'way'; OsmId = '131490822'; Name = 'Atacadão'; Lat = '-25.4509688'; Lon = '-49.2432104' },
    @{ Brand = 'Atacadão'; Aliases = 'atacadao|atacadão'; OsmType = 'way'; OsmId = '136389833'; Name = 'Atacadão'; Lat = '-25.3896427'; Lon = '-49.2362982' },
    @{ Brand = 'Atacadão'; Aliases = 'atacadao|atacadão'; OsmType = 'way'; OsmId = '461679070'; Name = 'Atacadão'; Lat = '-25.4643834'; Lon = '-49.3015140' },
    @{ Brand = 'Atacadão'; Aliases = 'atacadao|atacadão'; OsmType = 'way'; OsmId = '447998571'; Name = 'Atacadão'; Lat = '-25.4832851'; Lon = '-49.3183556' },
    @{ Brand = 'Atacadão'; Aliases = 'atacadao|atacadão'; OsmType = 'way'; OsmId = '178412979'; Name = 'Atacadão'; Lat = '-25.4146766'; Lon = '-49.3520968' },
    @{ Brand = 'Atacadão'; Aliases = 'atacadao|atacadão'; OsmType = 'way'; OsmId = '161312734'; Name = 'Atacadão'; Lat = '-25.5188558'; Lon = '-49.2298415' },
    @{ Brand = 'Assaí'; Aliases = 'assai|assaí|assai atacadista|assaí atacadista'; OsmType = 'node'; OsmId = '6358867643'; Name = 'Assaí Atacadista'; Lat = '-25.5147977'; Lon = '-49.2909560' }
)
foreach ($fallback in $verifiedFallbacks) {
    $key = "$($fallback.OsmType)-$($fallback.OsmId)"
    if ($rows.ContainsKey($key)) { continue }
    $rows[$key] = [ordered]@{
        id = "osm-$key"
        nome = $fallback.Name
        aliases = $fallback.Aliases
        rede = $fallback.Brand
        cidade = 'Curitiba'
        uf = 'PR'
        latitude = $fallback.Lat
        longitude = $fallback.Lon
        raio_m = '180'
        decisao = 'ANALISAR'
        fonte = "OpenStreetMap Nominatim ($key; verificação manual)"
        versao = $Version
    }
}

$directory = Split-Path -Parent $OutputPath
New-Item -ItemType Directory -Force -Path $directory | Out-Null
$lines = @(
    '# Fonte: OpenStreetMap contributors (ODbL) via Nominatim; revisão manual obrigatória'
    '# Área: Curitiba, Paraná; caixa: -25.60,-49.40,-25.30,-49.10'
    "# Coletado em: $Version; decisões ainda não ativadas no APK"
    "id`tnome`taliases`trede`tcidade`tuf`tlatitude`tlongitude`traio_m`tdecisao`tfonte`tversao"
)
foreach ($row in ($rows.Values | Sort-Object rede, nome, id)) {
    $lines += (($row.Values | ForEach-Object { ([string]$_).Replace("`t", ' ').Replace("`r", ' ').Replace("`n", ' ') }) -join "`t")
}
[System.IO.File]::WriteAllLines($OutputPath, $lines, [System.Text.UTF8Encoding]::new($false))
Write-Output "Geradas $($rows.Count) entradas em $OutputPath"
