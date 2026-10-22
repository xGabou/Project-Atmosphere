$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../../../../..')).Path
Set-Location $repo
$driver = Join-Path $repo 'src/main/java/net/Gabou/projectatmosphere/clouds/client/render/volumetric/StormT098CaptureDriver.java'
if (!(Select-String -LiteralPath $driver -Pattern 'CloudReviewCapture.enabled' -Quiet)) {
    throw 'Install the archived tools/capture-driver.patch and tools/CloudReviewCapture.java as described in CAPTURE-HARNESS.md before starting a new capture.'
}
$env:PA_CLOUD_REVIEW = '1'
& (Join-Path $repo 'gradlew.bat') runClient --offline --no-daemon -PsimpleCloudsMandatory=false
exit $LASTEXITCODE
