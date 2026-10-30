param([string]$Version='20261030-review-equipment-r3')
$ErrorActionPreference='Stop'
$repo=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$config=Join-Path $repo '.runtime-release/test-config.json'
$cfg=Get-Content -LiteralPath $config -Raw | ConvertFrom-Json
if($cfg.Database -notlike 'cyan_restore_*') { throw 'Requires isolated restored database' }
$name='failure-test-'+(Get-Date -Format 'yyyyMMddHHmmss')
$source=Join-Path $repo "releases/$Version"
$target=Join-Path $repo "releases/$name"
$before=Get-Content -LiteralPath "$repo/.runtime-release/smoke/state.json" -Raw | ConvertFrom-Json
Copy-Item -LiteralPath $source -Destination $target -Recurse
try {
    # A checksum-valid but unstartable artifact exercises automatic application recovery.
    [IO.File]::WriteAllText("$target/app.jar",'Deliberately invalid test artifact')
    $manifest=Get-Content -LiteralPath "$target/manifest.json" -Raw | ConvertFrom-Json
    $manifest.version=$name
    foreach($entry in $manifest.files) {
        if($entry.path -eq 'app.jar') { $entry.sha256=(Get-FileHash -LiteralPath "$target/app.jar").Hash }
    }
    $manifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath "$target/manifest.json" -Encoding UTF8
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$PSScriptRoot/Manage.ps1" -Action Deploy -Version $name -Config $config -Instance smoke -NoBrowser
    if($LASTEXITCODE -eq 0) { throw 'Broken release must fail deployment' }
    $after=Get-Content -LiteralPath "$repo/.runtime-release/smoke/state.json" -Raw | ConvertFrom-Json
    if($after.current -ne $before.current) { throw 'Previous release was not restored' }
    $health=Invoke-RestMethod "http://127.0.0.1:$($cfg.WebPort)/release-health"
    if($health.release -ne $before.current) { throw 'Recovered release health mismatch' }
    Write-Host 'PASS failed deployment automatically restarted the previous release'
} finally {
    $root=[IO.Path]::GetFullPath("$repo/releases").TrimEnd('\')
    $resolved=[IO.Path]::GetFullPath($target)
    if(!$resolved.StartsWith($root+'\',[StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid fixture path' }
    $rejected=Join-Path $repo '.runtime-release/rejected'
    New-Item -ItemType Directory -Path $rejected -Force | Out-Null
    Move-Item -LiteralPath $resolved -Destination (Join-Path $rejected $name)
}
