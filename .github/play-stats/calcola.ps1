# Conti delle statistiche di Google Play per la Dashboard, dai report mensili "overview" (stessa logica di play-stats.ps1 sul PC).
param([string]$Csv, [string]$Stati, [string]$Dest)
$ErrorActionPreference = 'Stop'
$apps = @((Get-Content $Stati -Raw -Encoding UTF8 | ConvertFrom-Json).app | Where-Object { $_.stato -eq 'PUBBLICATA' })
$out = @()
foreach ($a in $apps) {
    $rows = @()
    foreach ($f in Get-ChildItem $Csv -Filter "installs_$($a.pacchetto)_*_overview.csv") {
        $rows += Get-Content $f.FullName -Encoding Unicode | ConvertFrom-Csv
    }
    if (-not $rows) { continue }
    $rows = @($rows | Sort-Object Date)
    $dev  = ($rows | Measure-Object -Property 'Daily Device Installs' -Sum).Sum
    # per alcune app Play non mette nel bucket i primi mesi, e il primo giorno disponibile ha già installazioni attive:
    # i download sono almeno quelle più i download dei giorni dopo
    $base0 = [int]$rows[0].'Active Device Installs' + [int](($rows | Select-Object -Skip 1 | Measure-Object -Property 'Daily Device Installs' -Sum).Sum)
    if ($base0 -gt $dev) { $dev = $base0 }
    $last = $rows[-1]
    # l'ultimo giorno arriva con 'Active Device Installs' ancora a 0 (Play lo calcola dopo): si prende l'ultimo giorno che lo ha
    $conAtt = @($rows | Where-Object { [int]$_.'Active Device Installs' -gt 0 })
    $att  = if ($conAtt.Count -ge 1) { [int]$conAtt[-1].'Active Device Installs' } else { 0 }
    $uni  = [math]::Max(0, [int]$dev - $att)
    $da30 = ([datetime]$last.Date).AddDays(-29).ToString('yyyy-MM-dd')
    $d30  = ($rows | Where-Object { $_.Date -ge $da30 } | Measure-Object -Property 'Daily Device Installs' -Sum).Sum
    $primo = ($rows | Where-Object { [int]$_.'Daily Device Installs' -gt 0 } | Select-Object -First 1).Date
    $devP = [int]$dev - [int]$last.'Daily Device Installs'
    $attP = if ($conAtt.Count -ge 2) { [int]$conAtt[-2].'Active Device Installs' } else { $att }
    $uniP = [math]::Max(0, $devP - $attP)
    $out += [ordered]@{
        nome = $a.nome; pacchetto = $a.pacchetto
        download = [int]$dev; attivi = $att; disinstallazioni = [int]$uni
        download_prec = $devP; attivi_prec = $attP; disinstallazioni_prec = $uniP
        download30 = [int]$d30; dal = $primo; dati_al = $last.Date
    }
}
if (-not $out) { throw 'nessun dato' }
$quando = Get-Date -Format 'yyyy-MM-dd HH:mm'   # ora del controllo
$json = [ordered]@{ aggiornato = $quando; app = $out } | ConvertTo-Json -Depth 4
[IO.File]::WriteAllText($Dest, $json, (New-Object System.Text.UTF8Encoding($false)))
Write-Host "app: $($out.Count), aggiornato: $quando"
