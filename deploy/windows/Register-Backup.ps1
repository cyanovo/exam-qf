param([string]$At='02:00')
$ErrorActionPreference='Stop'
$time=[DateTime]::ParseExact($At,'HH:mm',[Globalization.CultureInfo]::InvariantCulture)
$script=Join-Path $PSScriptRoot 'Manage.ps1'
$action=New-ScheduledTaskAction -Execute 'powershell.exe' -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$script`" -Action Backup -NoBrowser"
$trigger=New-ScheduledTaskTrigger -Daily -At $time
$settings=New-ScheduledTaskSettingsSet -StartWhenAvailable -MultipleInstances IgnoreNew
$principal=New-ScheduledTaskPrincipal -UserId ([Security.Principal.WindowsIdentity]::GetCurrent().Name) -LogonType Interactive
Register-ScheduledTask -TaskName 'CyanExam-DailyBackup' -Action $action -Trigger $trigger -Settings $settings -Principal $principal -Force | Out-Null
Write-Host "Daily backup registered at $At, while the current Windows user is logged in. Missed runs start after login."
