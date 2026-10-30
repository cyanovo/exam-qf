param(
 [ValidateSet('Deploy','Start','Stop','Status','Backup','Rollback','Restore')][string]$Action='Start',
 [string]$Version='', [string]$Config='', [string]$BackupPath='', [string]$RestoreDatabase='',
 [switch]$NoBrowser, [ValidatePattern('^[a-zA-Z0-9_-]*$')][string]$Instance=''
)
$ErrorActionPreference='Stop'
$repo=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$runtime=Join-Path $repo '.runtime-release'
if($Instance) { $runtime=Join-Path $runtime $Instance }
New-Item -ItemType Directory -Path $runtime -Force | Out-Null
if(!$Config) { $Config=Join-Path $PSScriptRoot 'config.local.json' }
$utf8=New-Object System.Text.UTF8Encoding $false
function Write-Json($Path,$Value) { [IO.File]::WriteAllText($Path,($Value | ConvertTo-Json -Depth 12),$utf8) }
function Protect-File($Path) {
    $identity=[Security.Principal.WindowsIdentity]::GetCurrent().Name
    $permission=if(Test-Path -LiteralPath $Path -PathType Container) { '(OI)(CI)F' } else { '(F)' }
    & icacls.exe $Path /inheritance:r /grant:r "${identity}:$permission" "*S-1-5-18:$permission" | Out-Null
    if($LASTEXITCODE -ne 0) { throw "Cannot protect secret file: $Path" }
}
function Inside($Root,$Relative) {
    $base=[IO.Path]::GetFullPath($Root).TrimEnd('\','/')
    $p=[IO.Path]::GetFullPath((Join-Path $base $Relative))
    if(!$p.StartsWith($base+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Path outside managed directory' }
    return $p
}
function Port-Open($HostName,$Port) {
    $client=New-Object Net.Sockets.TcpClient
    try { $task=$client.ConnectAsync($HostName,[int]$Port); return ($task.Wait(1500) -and $client.Connected) }
    catch { return $false } finally { $client.Dispose() }
}
function Read-State { if(Test-Path -LiteralPath "$runtime/state.json") { return Get-Content -Raw -LiteralPath "$runtime/state.json" | ConvertFrom-Json }; return $null }
function Db($Sql,$Database=$cfg.Database) {
    $output=& $cfg.Mysql --protocol=TCP "--host=$($cfg.DatabaseHost)" "--port=$($cfg.DatabasePort)" "--user=$($cfg.DatabaseUser)" --default-character-set=utf8mb4 --batch --skip-column-names "--database=$Database" --execute=$Sql 2>&1
    if($LASTEXITCODE -ne 0) { throw "Database operation failed: $output" }
    return ($output -join "`n")
}
function Db-File($File,$Database=$cfg.Database) {
    foreach($value in @($cfg.DatabaseHost,$cfg.DatabaseUser,$Database)) {
        if($value -notmatch '^[a-zA-Z0-9_.:-]+$') { throw 'Unsupported database connection identifier' }
    }
    $args=@('--protocol=TCP',"--host=$($cfg.DatabaseHost)","--port=$($cfg.DatabasePort)","--user=$($cfg.DatabaseUser)",'--default-character-set=utf8mb4','--batch',"--database=$Database")
    $p=Start-Process -FilePath $cfg.Mysql -ArgumentList $args -WindowStyle Hidden -Wait -PassThru -RedirectStandardInput $File -RedirectStandardOutput "$runtime/migration-output.log" -RedirectStandardError "$runtime/migration-error.log"
    if($p.ExitCode -ne 0) {
        throw "SQL execution failed; no successful migration recorded. $(Get-Content -Raw -LiteralPath "$runtime/migration-error.log")"
    }
}
function Verify-Release($Name) {
    if($Name -notmatch '^[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}$') { throw 'Invalid release name' }
    $dir=Inside "$repo/releases" $Name
    $manifest=Get-Content -LiteralPath "$dir/manifest.json" -Raw | ConvertFrom-Json
    if($manifest.version -ne $Name -or $manifest.schemaCompatibility -ne 'review-equipment-additive-v1') { throw 'Incompatible release manifest' }
    foreach($entry in $manifest.files) {
        $file=Inside $dir $entry.path
        if((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -ne $entry.sha256) { throw "Release checksum mismatch: $($entry.path)" }
    }
    foreach($essential in @('app.jar','server.mjs','web/index.html','sql/exam_python_initial_code.sql','sql/exam_question_review_equipment.sql')) {
        if($essential -notin $manifest.files.path) { throw "Missing release artifact: $essential" }
    }
    return $dir
}
function Preflight {
    foreach($key in @('Java','Node','Mysql','Mysqldump')) {
        if(!(Test-Path -LiteralPath $cfg.$key -PathType Leaf)) { throw "Missing $key executable. Edit $Config" }
    }
    if($cfg.Database -notmatch '^[a-zA-Z0-9_]+$') { throw 'Invalid database name' }
    if($cfg.ApiPort -eq $cfg.WebPort) { throw 'API and web ports must differ' }
    $null=Db 'SELECT 1'
    $count=Db "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('cyan_question','exam_question_snapshot','exam_incident','exam_file_asset','exam_onsite_task','exam_code_submission','sys_role')"
    if([int]$count -ne 7) { throw 'This is an upgrade release. The existing exam database baseline is incomplete; no migration was applied.' }
}
function Schema-Check {
    $count=Db "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND ((table_name='cyan_question' AND column_name IN ('initial_code','review_state','revision')) OR (table_name='exam_question_snapshot' AND column_name='initial_code'))"
    if([int]$count -ne 4) { throw 'Required database columns missing. Run Deploy before Start.' }
    $count=Db "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('exam_equipment','exam_workstation','exam_question_review_event')"
    if([int]$count -ne 3) { throw 'Review/equipment tables missing. Run Deploy.' }
}
function Owned-Process($Record) {
    if(!$Record) { return $null }
    $p=Get-Process -Id $Record.pid -ErrorAction SilentlyContinue
    if(!$p) { return $null }
    if($p.StartTime.ToUniversalTime().ToString('o') -ne $Record.startedAt) { throw 'PID was reused; refusing to stop an unrelated process' }
    $details=Get-CimInstance Win32_Process -Filter "ProcessId=$($Record.pid)"
    if(!$details.CommandLine -or !$details.CommandLine.Contains($Record.marker)) { throw 'Process ownership mismatch; no process was stopped' }
    return $p
}
function Stop-Managed {
    $state=Read-State
    if(!$state) { return }
    foreach($record in @($state.web,$state.api)) {
        $p=Owned-Process $record
        if($p) { Stop-Process -Id $p.Id; Wait-Process -Id $p.Id -Timeout 20 -ErrorAction SilentlyContinue }
    }
    # Keep current/previous release selection; clear only process metadata.
    $state.web=$null; $state.api=$null
    Write-Json "$runtime/state.json" $state
}
function Backup-All {
    $dir=Join-Path $runtime ("backups/"+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
    New-Item -ItemType Directory -Path $dir -Force | Out-Null
    Protect-File $dir
    & $cfg.Mysqldump --protocol=TCP "--host=$($cfg.DatabaseHost)" "--port=$($cfg.DatabasePort)" "--user=$($cfg.DatabaseUser)" --single-transaction --routines --triggers --events --hex-blob --set-gtid-purged=OFF --default-character-set=utf8mb4 "--result-file=$dir/database.sql" $cfg.Database
    if($LASTEXITCODE -ne 0 -or (Get-Item -LiteralPath "$dir/database.sql").Length -eq 0) { throw 'Backup failed; migration cancelled' }
    Copy-Item -LiteralPath $Config -Destination "$dir/config.json"
    if(Test-Path -LiteralPath "$runtime/state.json") { Copy-Item -LiteralPath "$runtime/state.json" -Destination "$dir/state.json" }
    if(Test-Path -LiteralPath $cfg.JudgeTokenFile) { Copy-Item -LiteralPath $cfg.JudgeTokenFile -Destination "$dir/judge-token.key" }
    foreach($pair in @(@($cfg.Profile,'uploads'),@("$($cfg.Profile)-exam-private",'private'))) {
        if(Test-Path -LiteralPath $pair[0]) { Copy-Item -LiteralPath $pair[0] -Destination (Join-Path $dir $pair[1]) -Recurse }
    }
    $files=@(Get-ChildItem -LiteralPath $dir -File -Recurse | ForEach-Object {
        @{path=$_.FullName.Substring($dir.Length+1);sha256=(Get-FileHash -LiteralPath $_.FullName).Hash}
    })
    Write-Json "$dir/backup.json" @{database=$cfg.Database;createdAt=(Get-Date).ToString('o');files=$files}
    Write-Host "Backup: $dir"
    return $dir
}
function Dependencies {
    if(!(Port-Open $cfg.RedisHost $cfg.RedisPort)) {
        if($cfg.RedisHost -notin @('localhost','127.0.0.1')) { throw 'Remote Redis unavailable' }
        $redis=$cfg.RedisExecutable
        if(![IO.Path]::IsPathRooted($redis)) { $redis=Inside $repo $redis }
        if(!(Test-Path -LiteralPath $redis)) { throw 'Redis missing; configure RedisExecutable or start Redis' }
        New-Item -ItemType Directory "$runtime/redis" -Force | Out-Null
        $null=Start-Process -FilePath $redis -ArgumentList @('--bind','127.0.0.1','--port',"$($cfg.RedisPort)",'--dir',"`"$runtime/redis`"") -WindowStyle Hidden -PassThru -RedirectStandardOutput "$runtime/redis.log" -RedirectStandardError "$runtime/redis-error.log"
        Start-Sleep -Seconds 2
        if(!(Port-Open $cfg.RedisHost $cfg.RedisPort)) { throw 'Redis startup failed' }
    }
    $uri=[Uri]$cfg.JudgeUrl
    if(!(Port-Open $uri.Host $uri.Port)) {
        if(!(Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Python sandbox unavailable. Start Docker Desktop and provision the sandbox first.' }
        foreach($container in $cfg.JudgeContainers) {
            & docker start $container | Out-Null
            if($LASTEXITCODE -ne 0) { throw "Cannot start sandbox container $container. See usage document." }
        }
        Start-Sleep -Seconds 3
    }
    if(!(Test-Path -LiteralPath $cfg.JudgeTokenFile)) { throw 'Sandbox token file missing' }
    $headers=@{Authorization="Bearer $([IO.File]::ReadAllText($cfg.JudgeTokenFile).Trim())"}
    $response=Invoke-WebRequest -UseBasicParsing -Uri "$($cfg.JudgeUrl)/version" -Headers $headers -TimeoutSec 10
    if($response.StatusCode -ne 200) { throw 'Sandbox health check failed' }
}
function Wait-Http($Uri,$Process) {
    for($i=0;$i -lt 90;$i++) {
        if($Process.HasExited) { throw "Process exited. Check logs in $runtime/logs" }
        try { $r=Invoke-WebRequest -UseBasicParsing -Uri $Uri -TimeoutSec 2; if($r.StatusCode -eq 200) { return } } catch {}
        Start-Sleep -Seconds 1
    }
    throw "Startup timeout: $Uri. Check $runtime/logs"
}
function Start-Release($Name,$Previous) {
    $dir=Verify-Release $Name
    Schema-Check
    Dependencies
    foreach($port in @($cfg.ApiPort,$cfg.WebPort)) {
        if(Port-Open '127.0.0.1' $port) { throw "Port $port is occupied. No unrelated process was stopped; change ports in $Config." }
    }
    New-Item -ItemType Directory "$runtime/logs" -Force | Out-Null
    $environment=@{
        SERVER_ADDRESS='127.0.0.1';SERVER_PORT="$($cfg.ApiPort)"
        SPRING_DATASOURCE_DRUID_MASTER_URL="jdbc:mysql://$($cfg.DatabaseHost):$($cfg.DatabasePort)/$($cfg.Database)?useUnicode=true&characterEncoding=utf8&serverTimezone=GMT%2B8"
        SPRING_DATASOURCE_DRUID_MASTER_USERNAME=$cfg.DatabaseUser;SPRING_DATASOURCE_DRUID_MASTER_PASSWORD=$cfg.DatabasePassword
        SPRING_DATA_REDIS_HOST=$cfg.RedisHost;SPRING_DATA_REDIS_PORT="$($cfg.RedisPort)";SPRING_DATA_REDIS_DATABASE="$($cfg.RedisDatabase)";SPRING_DATA_REDIS_PASSWORD=$cfg.RedisPassword
        TOKEN_SECRET=$cfg.TokenSecret;RUOYI_PROFILE=$cfg.Profile;SPRING_QUARTZ_AUTO_STARTUP='false'
        SPRING_DATASOURCE_DRUID_STATVIEWSERVLET_ENABLED='false'
        EXAM_JUDGE_ENABLED='true';EXAM_JUDGE_PROVIDER='go-judge';EXAM_JUDGE_URL=$cfg.JudgeUrl
        EXAM_JUDGE_TOKEN=[IO.File]::ReadAllText($cfg.JudgeTokenFile).Trim()
        EXAM_JUDGE_RUNTIME_ID='go-judge-1.13.3/python-3.12.14'
        EXAM_API_TARGET="http://127.0.0.1:$($cfg.ApiPort)";EXAM_WEB_PORT="$($cfg.WebPort)";EXAM_RELEASE=$Name
    }
    $saved=@{}; $api=$null; $web=$null
    try {
        foreach($key in $environment.Keys) { $saved[$key]=[Environment]::GetEnvironmentVariable($key,'Process'); [Environment]::SetEnvironmentVariable($key,$environment[$key],'Process') }
        $stamp=Get-Date -Format 'yyyyMMdd-HHmmss'
        $api=Start-Process -FilePath $cfg.Java -ArgumentList @('-jar',"`"$dir/app.jar`"") -WindowStyle Hidden -PassThru -RedirectStandardOutput "$runtime/logs/api-$stamp.log" -RedirectStandardError "$runtime/logs/api-$stamp-error.log"
        Wait-Http "http://127.0.0.1:$($cfg.ApiPort)/captchaImage" $api
        $web=Start-Process -FilePath $cfg.Node -ArgumentList @("`"$dir/server.mjs`"","`"$dir/web`"") -WindowStyle Hidden -PassThru -RedirectStandardOutput "$runtime/logs/web-$stamp.log" -RedirectStandardError "$runtime/logs/web-$stamp-error.log"
        Wait-Http "http://127.0.0.1:$($cfg.WebPort)/release-health" $web
        Write-Json "$runtime/state.json" @{current=$Name;previous=$Previous;api=@{pid=$api.Id;startedAt=$api.StartTime.ToUniversalTime().ToString('o');marker="$dir/app.jar"};web=@{pid=$web.Id;startedAt=$web.StartTime.ToUniversalTime().ToString('o');marker="$dir/server.mjs"}}
    } catch {
        foreach($p in @($web,$api)) { if($p -and !$p.HasExited) { Stop-Process -Id $p.Id -ErrorAction SilentlyContinue } }
        throw
    } finally {
        foreach($key in $saved.Keys) { [Environment]::SetEnvironmentVariable($key,$saved[$key],'Process') }
    }
    $url="http://127.0.0.1:$($cfg.WebPort)"
    Write-Host "READY: $url (release $Name)"
    if(!$NoBrowser) { Start-Process $url }
}

$lock=$null; $oldPassword=$env:MYSQL_PWD
try {
    $lock=[IO.File]::Open("$runtime/operation.lock",'OpenOrCreate','ReadWrite','None')
    if(!(Test-Path -LiteralPath $Config)) {
        $new=Get-Content -Raw -LiteralPath "$PSScriptRoot/config.example.json" | ConvertFrom-Json
        $password=Read-Host 'MySQL password for the existing exam database' -AsSecureString
        $new.DatabasePassword=[Net.NetworkCredential]::new('',$password).Password
        $bytes=New-Object byte[] 48
        $rng=[Security.Cryptography.RandomNumberGenerator]::Create()
        try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
        $new.TokenSecret=[Convert]::ToBase64String($bytes)
        Write-Json $Config $new
        Protect-File $Config
    }
    $cfg=Get-Content -LiteralPath $Config -Raw | ConvertFrom-Json
    if(!$cfg.TokenSecret -or $cfg.TokenSecret.Length -lt 32) { throw 'Configure a random TokenSecret of at least 32 characters' }
    $env:MYSQL_PWD=$cfg.DatabasePassword
    if($Action -eq 'Stop') { Stop-Managed; Write-Host 'Managed API and web stopped; shared MySQL/Redis/sandbox left running.'; return }
    if($Action -eq 'Status') {
        $state=Read-State
        if(!$state) { Write-Host 'No managed release has been started.'; return }
        Write-Host "Release: $($state.current); previous: $($state.previous)"
        Write-Host "API running: $([bool](Owned-Process $state.api)); Web running: $([bool](Owned-Process $state.web))"
        return
    }
    Preflight
    if($Action -eq 'Backup') { $null=Backup-All; return }
    if($Action -eq 'Restore') {
        if($RestoreDatabase -notmatch '^cyan_restore_[a-zA-Z0-9_]+$' -or !$BackupPath) { throw 'Restore requires BackupPath and a NEW database name beginning cyan_restore_. Existing databases are never overwritten.' }
        $backup=Get-Content -LiteralPath "$BackupPath/backup.json" -Raw | ConvertFrom-Json
        foreach($entry in $backup.files) {
            if((Get-FileHash -LiteralPath (Inside $BackupPath $entry.path)).Hash -ne $entry.sha256) { throw 'Backup checksum mismatch' }
        }
        $null=Db "CREATE DATABASE $RestoreDatabase CHARACTER SET utf8mb4"
        $source=([IO.Path]::GetFullPath("$BackupPath/database.sql")).Replace('\','/')
        Db-File $source $RestoreDatabase
        $destination=Inside "$runtime/recovery" $RestoreDatabase
        New-Item -ItemType Directory $destination -Force | Out-Null
        foreach($name in @('uploads','private','config.json','judge-token.key')) {
            $targetName=if($name -eq 'private') { 'uploads-exam-private' } else { $name }
            if(Test-Path -LiteralPath "$BackupPath/$name") { Copy-Item -LiteralPath "$BackupPath/$name" -Destination "$destination/$targetName" -Recurse }
        }
        Protect-File $destination
        Write-Host "Restored into NEW database $RestoreDatabase and $destination. Live database unchanged."
        return
    }
    $state=Read-State
    if($Action -eq 'Start' -and $state -and (Owned-Process $state.api) -and (Owned-Process $state.web)) {
        Write-Host "Already running: http://127.0.0.1:$($cfg.WebPort)"
        if(!$NoBrowser) { Start-Process "http://127.0.0.1:$($cfg.WebPort)" }; return
    }
    if($Action -eq 'Start' -and (!$state -or !$state.current)) { $Action='Deploy' }
    if($Action -eq 'Start') { Stop-Managed; Start-Release $state.current $state.previous; return }
    if($Action -eq 'Rollback') {
        if(!$state -or !$state.previous) { throw 'No previous managed release to roll back to' }
        $Version=$state.previous
    } elseif(!$Version) {
        $latest=Get-ChildItem -LiteralPath "$repo/releases" -Directory | Where-Object {
            Test-Path -LiteralPath (Join-Path $_.FullName 'manifest.json')
        } | Sort-Object {
            [DateTimeOffset](Get-Content -LiteralPath (Join-Path $_.FullName 'manifest.json') -Raw | ConvertFrom-Json).createdAt
        } -Descending | Select-Object -First 1
        if(!$latest) { throw 'No release package. Run Build-Release.ps1 first.' }
        $Version=$latest.Name
    }
    $dir=Verify-Release $Version
    Dependencies
    $old=$null
    if($state) { $old=$state.current }
    Stop-Managed
    try {
        $backup=Backup-All
        if($Action -eq 'Deploy') {
            $null=Db 'CREATE TABLE IF NOT EXISTS exam_schema_migration(name VARCHAR(160) PRIMARY KEY,sha256 CHAR(64) NOT NULL,applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP)'
            foreach($name in @('exam_python_initial_code.sql','exam_question_review_equipment.sql','exam_user_organization.sql')) {
                $file=Inside $dir "sql/$name"
                if($name -eq 'exam_user_organization.sql' -and !(Test-Path -LiteralPath $file)) { continue }
                $hash=(Get-FileHash -LiteralPath $file).Hash
                $existing=Db "SELECT sha256 FROM exam_schema_migration WHERE name='$name'"
                if($existing) {
                    if($existing -ne $hash) { throw "Applied migration checksum changed: $name. Create a new migration instead." }
                    continue
                }
                Db-File $file
                $null=Db "INSERT INTO exam_schema_migration(name,sha256) VALUES('$name','$hash')"
            }
        }
        Schema-Check
        $previous=$old
        if($Version -eq $old -and $state) { $previous=$state.previous }
        Start-Release $Version $previous
    } catch {
        $failure=$_
        if($old) {
            Write-Warning 'New release failed. Attempting to restart previous application; additive database columns are retained.'
            try { Start-Release $old $state.previous } catch { Write-Warning "Previous release restart failed: $_" }
        }
        throw $failure
    }
} finally {
    $env:MYSQL_PWD=$oldPassword
    if($lock) { $lock.Dispose() }
}
