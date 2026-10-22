param(
    [Parameter(Mandatory)][string]$CaptureFolder,
    [Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]+$')][string]$PassName,
    [string]$CaptureLog,
    [switch]$RequireComplete,
    [ValidateSet('T255','T256')][string]$Campaign = 'T255',
    [string]$OutputRoot
)
$ErrorActionPreference = 'Stop'
$source = (Resolve-Path -LiteralPath $CaptureFolder).Path
$output = Join-Path $PSScriptRoot "native/$PassName"
if ($OutputRoot) { $output = Join-Path $OutputRoot $PassName }
$types = @('cumulonimbus_calvus', 'cumulonimbus_capillatus', 'supercell')
$views = @('side', 'opposite_side', 'front', 'back', 'above', 'below', 'low_angle', 'far', 'close', 'updraft_oblique')
if ($Campaign -eq 'T256') { $views += @('upwind','downwind','crosswind_left','crosswind_right','tower','transition','canopy_anvil') }
$seeds = @('20261008', '20268927', '20276846')
$evidence = @()
$missing = @()
$rejected = @{}
$captureLogPath = $null
if ($CaptureLog) {
    $captureLogPath = (Resolve-Path -LiteralPath $CaptureLog).Path
    foreach ($line in Get-Content -LiteralPath $captureLogPath) {
        if ($line -match ($Campaign + '_FRAMING shot=(\S+) occupiedEdgePixels=(\d+).*pass=false') -and $Matches[1] -notmatch '__cost_') {
            $rejected[$Matches[1]] = "viewport clipping: $($Matches[2]) occupied edge pixels"
        }
        if ($line -match ($Campaign + '_CAPTURE_REJECTED shot=(\S+)') -and !$rejected.ContainsKey($Matches[1])) {
            $rejected[$Matches[1]] = 'capture guard rejected this arm'
        }
    }
}
if ($RequireComplete -and !$CaptureLog) { throw 'Complete evidence requires its capture log, not just existing PNG files.' }
New-Item -ItemType Directory -Path $output -Force | Out-Null
foreach ($file in Get-ChildItem -LiteralPath $source -Filter ($Campaign + '_*.png')) {
    if ($file.Name -notmatch ('^' + $Campaign + '_(cumulonimbus_calvus|cumulonimbus_capillatus|supercell)__(\d+)__(.+)\.png$')) { continue }
    $cloudType = $Matches[1]; $seed = $Matches[2]; $view = $Matches[3]
    $folder = Join-Path $output "$cloudType/$seed"
    New-Item -ItemType Directory -Path $folder -Force | Out-Null
    $target = Join-Path $folder "$view.png"
    Copy-Item -LiteralPath $file.FullName -Destination $target -Force
    $shotKey = "${Campaign}_${cloudType}__${seed}__" + ($view -replace '__raw$', '')
    $evidence += [ordered]@{ type=$cloudType; seed=$seed; view=$view; source=$file.FullName; file="$cloudType/$seed/$view.png"; sha256=(Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant(); capture_rejection=$rejected[$shotKey] }
}
$style = '<style>body{background:#10151d;color:#dee8f4;font:16px system-ui;margin:24px}a{color:#83c2ff}img{width:100%;background:#000}figure{margin:0;border:1px solid #344155;padding:8px}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(340px,1fr));gap:12px}figcaption{padding:8px}</style>'
foreach ($cloudType in $types) {
    $folder = Join-Path $output $cloudType
    New-Item -ItemType Directory -Path $folder -Force | Out-Null
    $reference = Join-Path $PSScriptRoot "../cloud-review-2026-10-08/$cloudType"
    Copy-Item -LiteralPath (Join-Path $reference 'reference.jpg') -Destination (Join-Path $folder 'reference.jpg') -Force
    Copy-Item -LiteralPath (Join-Path $reference 'reference-source.json') -Destination (Join-Path $folder 'reference-source.json') -Force
    $html = "<!doctype html><meta charset='utf-8'><title>$Campaign $cloudType — $PassName</title>$style<h1>$cloudType</h1><p>Experimental native geometry; not production-approved. Flat neutral radiance. Exact framing and geometry hashes belong to the capture log. This gallery does not establish realism or a performance pass.</p><p><a href='../index.html'>Comparison index</a> · <a href='reference-source.json'>Real-photo source / licence</a></p><figure><img src='reference.jpg'><figcaption>Credited IRL reference reused from the 2026-10-08 review; physical distance/size is not calibrated.</figcaption></figure>"
    foreach ($seed in $seeds) {
        $html += "<h2>Seed $seed</h2><div class='grid'>"
        foreach ($view in $views) {
            $image = Join-Path $folder "$seed/$view.png"
            $raw = Join-Path $folder "$seed/${view}__raw.png"
            $reason = $rejected["${Campaign}_${cloudType}__${seed}__$view"]
            if ($reason) {
                $missing += "$cloudType/$seed/$view"
                $html += "<figure><img src='$seed/${view}__raw.png'><figcaption>$view — REJECTED: $reason. Retained for diagnosis, not valid framing evidence.</figcaption></figure>"
                continue
            }
            if (!(Test-Path -LiteralPath $image) -or !(Test-Path -LiteralPath $raw)) {
                $missing += "$cloudType/$seed/$view"
                $html += "<figure><figcaption>$view — missing/unaccepted</figcaption></figure>"
                continue
            }
            $html += "<figure><a href='$seed/$view.png'><img src='$seed/${view}__raw.png'></a><figcaption>$view · <a href='$seed/$view.png'>native screenshot</a> · <a href='$seed/${view}__raw.png'>actual cloud target</a></figcaption></figure>"
        }
        $html += '</div>'
    }
    $extras = $evidence | Where-Object { $_.type -eq $cloudType -and $_.view -notmatch '__raw$' -and $_.view -notin $views }
    if ($extras) {
        $html += "<h2>Control / attribution arms</h2><div class='grid'>"
        foreach ($extra in $extras) {
            $html += "<figure><a href='$($extra.seed)/$($extra.view).png'><img src='$($extra.seed)/$($extra.view)__raw.png'></a><figcaption>$($extra.view), seed $($extra.seed)</figcaption></figure>"
        }
        $html += '</div>'
    }
    [System.IO.File]::WriteAllText((Join-Path $folder 'index.html'), $html)
}
$index = "<!doctype html><meta charset='utf-8'><title>$Campaign — $PassName</title>$style<h1>$Campaign native storm experiment — $PassName</h1><p>Default-off prototype. Missing frames and failed visual/performance gates are not concealed.</p><ul>"
foreach ($cloudType in $types) { $index += "<li><a href='$cloudType/index.html'>$cloudType</a></li>" }
$index += "</ul><p>Missing standard evidence views: $($missing.Count). See evidence.json and the architecture report for status.</p><h2>Identical-frame side comparison, seed 20261008</h2><div class='grid'>"
foreach ($cloudType in $types) { $index += "<figure><img src='$cloudType/20261008/side__raw.png'><figcaption>$cloudType</figcaption></figure>" }
$index += '</div>'
if ($Campaign -eq 'T256') {
    foreach ($cloudType in $types) {
        $index += "<h2>${cloudType}: T255 / envelope A / growth B, identical framing</h2><div class='grid'>"
        foreach ($arm in @('baseline_T255_side','envelope_A_side','envelope_B_side')) {
            $index += "<figure><img src='$cloudType/20261008/${arm}__raw.png'><figcaption>$arm — raw target, no crop</figcaption></figure>"
        }
        $index += '</div>'
    }
}
[System.IO.File]::WriteAllText((Join-Path $output 'index.html'), $index)
$manifest = [ordered]@{ pass=$PassName; source=$source; capture_log=$captureLogPath; rejected_arms=$rejected; missing=$missing; files=$evidence; acceptance='NOT_APPROVED'; geometry_authority='isolated client fixture, not production weather'; lighting='flat neutral'; cpu_previews_are_native=$false }
[System.IO.File]::WriteAllText((Join-Path $output 'evidence.json'), ($manifest | ConvertTo-Json -Depth 8))
Write-Output "Exported $($evidence.Count) native files; missing $($missing.Count) standard views; NOT acceptance."
if ($RequireComplete -and $missing.Count -gt 0) { throw "Incomplete native gallery: $($missing -join ', ')" }
