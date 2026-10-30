param([string]$Version = (Get-Date -Format 'yyyyMMdd-HHmmss'),
      [string]$Maven = 'D:/app/apache-maven-3.9.12/apache-maven-3.9.12/bin/mvn.cmd',
      [string]$JavaHome = 'D:/java/jdk25', [string]$Node = 'D:/app/nodejs/node.exe')
$ErrorActionPreference='Stop'
$repo=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
if($Version -notmatch '^[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}$') { throw 'Invalid version' }
$output=Join-Path $repo "releases/$Version"
if(Test-Path -LiteralPath $output) { throw 'Release already exists; choose another version' }
$oldJava=$env:JAVA_HOME
try {
    $env:JAVA_HOME=$JavaHome
    Push-Location (Join-Path $repo 'cyan-exam')
    try {
        & $Maven -pl cyan-exam-admin -am package "-Dexam.build.finalName=cyan-exam-release-$Version"
        if($LASTEXITCODE -ne 0) { throw 'Backend tests/build failed' }
    } finally { Pop-Location }
    Push-Location (Join-Path $repo 'cyan-exam-vue')
    try {
        & $Node node_modules/vite/bin/vite.js build
        if($LASTEXITCODE -ne 0) { throw 'Frontend build failed' }
        if(Get-ChildItem dist/static/js -Filter '*.js' | Select-String -SimpleMatch '__DEFINES__' | Select-Object -First 1) {
            throw 'Development client leaked into production assets'
        }
    } finally { Pop-Location }
    New-Item -ItemType Directory -Path "$output/sql","$output/web" -Force | Out-Null
    Copy-Item -LiteralPath "$repo/cyan-exam/cyan-exam-admin/target/cyan-exam-release-$Version.jar" -Destination "$output/app.jar"
    # Ship environment-only settings, never the developer's local database credentials.
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive=[IO.Compression.ZipFile]::Open("$output/app.jar",'Update')
    try {
        foreach($name in @('application.yml','application-druid.yml')) {
            $entry=$archive.GetEntry("BOOT-INF/classes/$name")
            if($entry) { $entry.Delete() }
            $null=[IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive,"$PSScriptRoot/$name","BOOT-INF/classes/$name")
        }
    } finally { $archive.Dispose() }
    Copy-Item -Path "$repo/cyan-exam-vue/dist/*" -Destination "$output/web" -Recurse
    Copy-Item -LiteralPath "$PSScriptRoot/server.mjs" -Destination "$output/server.mjs"
    foreach($name in @('exam_python_initial_code.sql','exam_question_review_equipment.sql','exam_user_organization.sql')) {
        Copy-Item -LiteralPath "$repo/cyan-exam/sql/$name" -Destination "$output/sql/$name"
    }
    $files=@(Get-ChildItem -LiteralPath $output -File -Recurse | ForEach-Object {
        @{path=$_.FullName.Substring($output.Length+1).Replace('\','/'); sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash}
    })
    @{version=$Version;createdAt=(Get-Date).ToString('o');schemaCompatibility='review-equipment-additive-v1';files=$files} |
        ConvertTo-Json -Depth 6 | Set-Content -LiteralPath "$output/manifest.json" -Encoding UTF8
    Write-Host "Release created: $output"
} finally { $env:JAVA_HOME=$oldJava }
