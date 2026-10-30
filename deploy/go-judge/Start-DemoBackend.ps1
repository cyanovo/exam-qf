param(
    [string]$Java = 'D:/java/jdk25/bin/java.exe',
    [string]$RuntimeRoot = 'D:/app/cyan-exam',
    [int]$Port = 9010,
    [int]$RedisDatabase = 3,
    [string]$JarPath = ''
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$jar = Join-Path $repo 'cyan-exam/cyan-exam-admin/target/cyan-exam-python-demo.jar'
if ($JarPath) { $jar = $JarPath }
$secret = Join-Path $RuntimeRoot 'sandbox-token.key'
if (-not (Test-Path -LiteralPath $jar)) { throw 'Build the python-demo JAR first; see docs/python-grading-20261029.md' }
if (-not (Test-Path -LiteralPath $secret)) { throw 'Start the isolated sandbox first' }
if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) {
    throw "Port $Port is occupied; no existing process was stopped."
}
$configuration = @{
    SERVER_PORT = "$Port"
    SERVER_ADDRESS = '127.0.0.1'
    SPRING_DATASOURCE_DRUID_MASTER_URL = 'jdbc:mysql://localhost:3306/cyan_exam_python_demo_20261029?useUnicode=true&characterEncoding=utf8&zeroDateTimeBehavior=convertToNull&useSSL=true&serverTimezone=GMT%2B8'
    SPRING_DATA_REDIS_DATABASE = "$RedisDatabase"
    TOKEN_SECRET = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
    SPRING_QUARTZ_AUTO_STARTUP = 'false'
    SPRING_DATASOURCE_DRUID_STATVIEWSERVLET_ENABLED = 'false'
    RUOYI_PROFILE = (Join-Path $RuntimeRoot 'python-demo-uploads')
    EXAM_JUDGE_ENABLED = 'true'
    EXAM_JUDGE_PROVIDER = 'go-judge'
    EXAM_JUDGE_URL = 'http://127.0.0.1:15050'
    EXAM_JUDGE_TOKEN = [IO.File]::ReadAllText($secret).Trim()
    EXAM_JUDGE_RUNTIME_ID = 'go-judge-1.13.3/python-3.12.14/base-sha256-09f7da3bc104798d0afb40bc08d23ab2da20a76130cec1f2ef170848f5d85217'
}
$previous = @{}
try {
    foreach ($key in $configuration.Keys) {
        $previous[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
        [Environment]::SetEnvironmentVariable($key, $configuration[$key], 'Process')
    }
    $process = Start-Process -FilePath $Java -ArgumentList @('-jar', "`"$jar`"") -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $RuntimeRoot "python-demo-$Port-out.log") `
        -RedirectStandardError (Join-Path $RuntimeRoot "python-demo-$Port-err.log") -PassThru
    Write-Output "Started PID $($process.Id); API http://127.0.0.1:$Port. Check logs before opening the UI."
} finally {
    foreach ($key in $previous.Keys) {
        [Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process')
    }
}
