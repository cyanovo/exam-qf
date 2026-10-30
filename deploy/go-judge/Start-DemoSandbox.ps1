param([string]$RuntimeRoot = 'D:/app/cyan-exam', [int]$Port = 15050)
$ErrorActionPreference = 'Stop'
$buildDir = Join-Path $RuntimeRoot 'go-judge'
$binary = Join-Path $buildDir 'go-judge'
$expected = '509782f8959987256d99ac6dc8062ec593d776f291401c8e0c28bdc48dcd1880'
New-Item -ItemType Directory -Force $buildDir | Out-Null
if (-not (Test-Path -LiteralPath $binary)) {
    Invoke-WebRequest -Headers @{Accept='application/octet-stream'} -TimeoutSec 300 `
        'https://api.github.com/repos/criyle/go-judge/releases/assets/635760959' -OutFile $binary
}
if ((Get-FileHash -LiteralPath $binary -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected) {
    throw 'Official go-judge binary checksum mismatch; sandbox was not started.'
}
docker build --network none -f (Join-Path $PSScriptRoot 'Dockerfile') -t cyan-python-sandbox:1.13.3 $buildDir
if ($LASTEXITCODE -ne 0) { throw 'Sandbox image build failed' }
$network = docker network ls --filter name=^cyan-python-isolated$ --format '{{.Name}}'
if (-not $network) {
    docker network create --internal cyan-python-isolated
    if ($LASTEXITCODE -ne 0) { throw 'Network creation failed' }
}
if ((docker network inspect cyan-python-isolated --format '{{.Internal}}') -ne 'true') {
    throw 'The sandbox network must be internal'
}
$secretFile = Join-Path $RuntimeRoot 'sandbox-token.key'
if (-not (Test-Path -LiteralPath $secretFile)) {
    $bytes = New-Object byte[] 32
    [Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    [IO.File]::WriteAllText($secretFile, [Convert]::ToHexString($bytes))
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
    icacls $secretFile /inheritance:r /grant:r "${identity}:(F)" | Out-Null
}
$env:ES_AUTH_TOKEN = [IO.File]::ReadAllText($secretFile).Trim()
try {
    # Privilege is confined to Docker Desktop's Linux VM. No host paths or Docker socket are mounted.
    docker run -d --name cyan-python-demo --privileged --network cyan-python-isolated `
        --shm-size=256m --memory=768m --cpus=2 --pids-limit=128 `
        -e ES_AUTH_TOKEN -e ES_SILENT=true cyan-python-sandbox:1.13.3
    if ($LASTEXITCODE -ne 0) { throw 'Sandbox start failed (check whether the named container already exists)' }
    $config = Join-Path $PSScriptRoot 'nginx.conf'
    # Only the narrow HTTP relay has a routable interface; the execution service remains internal.
    docker create --name cyan-python-gateway --network bridge --read-only --cap-drop ALL `
        --user nginx --tmpfs /tmp:rw,noexec,nosuid,size=16m --memory=64m --pids-limit=32 `
        -p "127.0.0.1:${Port}:5050" --mount "type=bind,source=$config,target=/etc/nginx/nginx.conf,readonly" `
        --entrypoint nginx nginx:alpine -g 'daemon off;'
    if ($LASTEXITCODE -ne 0) { throw 'Gateway creation failed' }
    docker network connect cyan-python-isolated cyan-python-gateway
    if ($LASTEXITCODE -ne 0) { throw 'Gateway network attachment failed' }
    docker start cyan-python-gateway
    if ($LASTEXITCODE -ne 0) { throw 'Gateway start failed' }
} finally {
    Remove-Item Env:ES_AUTH_TOKEN -ErrorAction SilentlyContinue
}
