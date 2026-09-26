param([string]$OfficialCheckout = '')
$ErrorActionPreference = 'Stop'
$taskRoot = $PSScriptRoot
$taskRevision = '69d3065284003686b89c6e6bf4172dca796973f1'
if (-not $OfficialCheckout) {
    $OfficialCheckout = Join-Path $taskRoot '.vendor/SoundcoreSDKDemo'
    if (-not (Test-Path -LiteralPath $OfficialCheckout)) {
        git clone https://github.com/AnkerInnovations/SoundcoreSDKDemo.git $OfficialCheckout
        if ($LASTEXITCODE -ne 0) { throw 'Official SDK clone failed' }
    }
}
$taskActual = git -C $OfficialCheckout rev-parse HEAD
if ($LASTEXITCODE -ne 0 -or $taskActual -ne $taskRevision) {
    throw "Expected official revision $taskRevision; found $taskActual. Review SDK changes before updating."
}
$taskLibs = Join-Path $taskRoot 'android/app/libs'
New-Item -ItemType Directory -Path $taskLibs -Force | Out-Null
foreach ($taskName in @('module_spplink-release.aar', 'opus-lib-0.0.2.aar')) {
    $taskFile = Join-Path $OfficialCheckout "SoundcoreSDK AndroidDemo/app/libs/$taskName"
    if (-not (Test-Path -LiteralPath $taskFile)) { throw "Missing official artifact: $taskName" }
    Copy-Item -LiteralPath $taskFile -Destination (Join-Path $taskLibs $taskName)
    Get-FileHash -LiteralPath (Join-Path $taskLibs $taskName) -Algorithm SHA256
}
