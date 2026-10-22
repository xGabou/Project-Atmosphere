param(
    [Parameter(Mandatory)][string]$CaptureLog,
    [Parameter(Mandatory)][string]$OutputFolder,
    [ValidateSet('T255','T256')][string]$Campaign = 'T255'
)
$ErrorActionPreference = 'Stop'
$log = (Resolve-Path -LiteralPath $CaptureLog).Path
$folder = (Resolve-Path -LiteralPath $OutputFolder).Path
$lines = Get-Content -LiteralPath $log
$timings = @()
$parity = @()
$poses = @()
$framing = @()
$traversal = @()
$camera = @()
$resets = @()
foreach ($line in $lines) {
    if ($line -match ($Campaign + '_(GPU|PIPELINE) shot=(\S+) samples=(\d+).*medianMs=([-\d.]+) p95Ms=([-\d.]+) maxMs=([-\d.]+)')) {
        $timings += [ordered]@{ scope=$Matches[1]; shot=$Matches[2]; samples=[int]$Matches[3]; median_ms=[double]$Matches[4]; p95_ms=[double]$Matches[5]; max_ms=[double]$Matches[6]; source_line=$line }
    }
    if ($line -match ($Campaign + '_PARITY shot=(\S+) samples=(\d+) occupied=(\d+) maxAbsError=(\S+) tolerance=(\S+) pass=(true|false)')) {
        $parity += [ordered]@{ shot=$Matches[1]; samples=[int]$Matches[2]; occupied=[int]$Matches[3]; max_absolute_error=[double]$Matches[4]; tolerance=[double]$Matches[5]; pass=$Matches[6] -eq 'true'; source_line=$line }
    }
    if ($line -match ($Campaign + '_POSE shot=(\S+) version=(\d+) signature=(\S+)')) {
        $poses += [ordered]@{ shot=$Matches[1]; version=[int]$Matches[2]; signature=$Matches[3]; source_line=$line }
    }
    if ($line -match ($Campaign + '_FRAMING shot=(\S+) occupiedEdgePixels=(\d+).*pass=(true|false)')) {
        $framing += [ordered]@{ shot=$Matches[1]; occupied_edge_pixels=[int]$Matches[2]; pass=$Matches[3] -eq 'true'; source_line=$line }
    }
    if ($line -match ($Campaign + '_TRAVERSAL_PAIR signature=(\S+) channels=(\d+) maxAbsRGBA=(\S+) tolerance=(\S+) pass=(true|false)')) {
        $traversal += [ordered]@{ signature=$Matches[1]; channels=[int]$Matches[2]; max_absolute_rgba_error=[double]$Matches[3]; tolerance=[double]$Matches[4]; pass=$Matches[5] -eq 'true'; source_line=$line }
    }
    if ($line -match ($Campaign + '_CAMERA_AUTHORITY shot=(\S+) probes=(\d+) maxAbsError=(\S+).*pass=(true|false)')) {
        $camera += [ordered]@{ shot=$Matches[1]; probes=[int]$Matches[2]; max_absolute_error=[double]$Matches[3]; pass=$Matches[4] -eq 'true'; source_line=$line }
    }
    if ($line -match ($Campaign + '_RESET .*pass=(true|false)')) {
        $resets += [ordered]@{ pass=$Matches[1] -eq 'true'; source_line=$line }
    }
}
$morphologyViews = @($timings | Where-Object { $_.scope -eq 'PIPELINE' -and $_.shot -notlike '*attribution_*' -and $_.shot -notlike '*control_*' -and $_.shot -notlike '*baseline_*' -and $_.shot -notlike '*envelope_*' -and $_.shot -notlike '*cost_*' })
$required = $morphologyViews
if ($Campaign -eq 'T256') { $required = @($morphologyViews | Where-Object { $_.shot -notlike '*__updraft_oblique' }) }
$invalid = @($timings | Where-Object { $_.samples -lt 30 -or $_.median_ms -le 0 -or $_.p95_ms -lt $_.median_ms -or $_.max_ms -lt $_.p95_ms })
$failures = @($required | Where-Object { $_.p95_ms -gt 10 })
$summary = [ordered]@{
    source_log=$log; log_sha256=(Get-FileHash -LiteralPath $log -Algorithm SHA256).Hash.ToLowerInvariant();
    authority='native fixture; not production weather'; lighting='neutral constant radiance, MESO, MICRO off';
    timer='fresh GL_TIMESTAMP serials; 32 presented warmup frames; result age <=32';
    pipeline_scope='weather_shadow_march_composite; not simulation/world rendering';
    captured_morphology_view_records=$morphologyViews.Count; all_morphology_p95_above_10ms=@($morphologyViews | Where-Object {$_.p95_ms -gt 10}).Count;
    required_view_records=$required.Count; invalid_timing_records=$invalid.Count;
    required_p95_above_10ms=$failures.Count; required_max_above_10ms=@($required | Where-Object {$_.max_ms -gt 10}).Count;
    parity_arms=$parity.Count; parity_failures=@($parity | Where-Object {!$_.pass}).Count;
    framing_records=$framing.Count; framing_failures=@($framing | Where-Object {!$_.pass -and $_.shot -notlike '*cost_*'}).Count;
    traversal_pairs=$traversal.Count; traversal_failures=@($traversal | Where-Object {!$_.pass}).Count;
    camera_checks=$camera.Count; camera_failures=@($camera | Where-Object {!$_.pass}).Count;
    reset_checks=$resets.Count; reset_failures=@($resets | Where-Object {!$_.pass}).Count;
    capture_complete=[bool]($lines -match 'T098_CAPTURE_COMPLETE'); rejected=[bool]($lines -match ($Campaign + '_CAPTURE_REJECTED'));
    production_acceptance='NOT_APPROVED'; poses=$poses; timings=$timings; parity=$parity; framing=$framing; traversal=$traversal; camera=$camera; resets=$resets
}
[System.IO.File]::WriteAllText((Join-Path $folder 'native-metrics.json'), ($summary | ConvertTo-Json -Depth 8))
$rows = @('| Shot | Samples | Median ms | p95 ms | Max ms |', '| --- | ---: | ---: | ---: | ---: |')
foreach ($record in ($required | Sort-Object {[double]$_.p95_ms} -Descending)) {
    $rows += '| ' + $record.shot + ' | ' + $record.samples + ' | ' + $record.median_ms.ToString('F3', [cultureinfo]::InvariantCulture) + ' | ' + $record.p95_ms.ToString('F3', [cultureinfo]::InvariantCulture) + ' | ' + $record.max_ms.ToString('F3', [cultureinfo]::InvariantCulture) + ' |'
}
[System.IO.File]::WriteAllText((Join-Path $folder 'native-metrics.md'), "# Native flat-light GPU measurements`n`nNot production-approved. Whole-hook GPU scope excludes simulation/world rendering. Realism is a separate review, not a numerical timing pass.`n`n" + ($rows -join "`n") + "`n")
Write-Output "Native metrics: $($required.Count) view records, $($failures.Count) p95 ceiling failures, $($invalid.Count) invalid timing records, $($parity.Count) parity arms."
