$ErrorActionPreference='Stop'
$workspace=(Resolve-Path $PSScriptRoot).Path
$runtime=(Get-Content "$workspace/target/dynamic-staging-path.txt" -Raw).Trim()
if(!(Resolve-Path $runtime).Path.StartsWith($workspace+'\output\local-dynamic-staging-',[StringComparison]::OrdinalIgnoreCase)){throw 'Unexpected staging location'}
$database=(Get-Content "$runtime/database.txt" -Raw).Trim()
if($database -notmatch '^v3_dynamic_staging_[0-9]+$'){throw 'Unexpected staging database'}
if(!(Test-Path "$runtime/generated/generated-sheets.json")){throw 'Dynamic generation validation evidence is required'}
$jar=Join-Path $runtime 'assessment.jar'
if(!(Test-Path -LiteralPath $jar)){throw 'Validated staging package is missing. Rebuild and review it before staging.'}
# Reuse this exact validated package. Restarting must not silently deploy a new build.
if(!(Get-NetTCPConnection -LocalPort 33319 -State Listen -ErrorAction SilentlyContinue)) {
 $dbServer=Start-Process 'C:/xampp2/mysql/bin/mysqld.exe' -ArgumentList @("--defaults-file=$runtime/validation.ini",'--console') -WindowStyle Hidden -PassThru -RedirectStandardOutput "$runtime/database-resume.out.log" -RedirectStandardError "$runtime/database-resume.err.log"
 for($i=0;$i -lt 40;$i++) {
  $dbServer.Refresh();if($dbServer.HasExited){throw 'Isolated database did not start'}
  if(netstat -ano | Select-String ':33319\s+.*LISTENING'){break};Start-Sleep -Milliseconds 250
 }
}
$dbOwner=Get-NetTCPConnection -LocalPort 33319 -State Listen | Select-Object -First 1
$dbProcess=Get-CimInstance Win32_Process -Filter "ProcessId=$($dbOwner.OwningProcess)"
if($dbProcess.Name -ne 'mysqld.exe' -or !$dbProcess.CommandLine.Contains("$runtime/validation.ini")){throw 'Unexpected owner of isolated database port 33319'}
$listener=Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if($listener) {
 $current=Invoke-RestMethod 'http://127.0.0.1:8080/api/v3/system/readiness' -TimeoutSec 5
 $process=Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
 $ownedArgs=$process.CommandLine -and ($process.CommandLine.Contains("$runtime/backend-true.args") -or $process.CommandLine.Contains("$runtime/backend-false.args"))
 if($current.data.databaseName -ne $database -or $process.Name -ne 'java.exe' -or !$ownedArgs){throw '8080 belongs to another backend. Stop that backend yourself before starting isolated staging.'}
 $ready=Invoke-RestMethod 'http://127.0.0.1:8080/api/v3/system/mobile-release-readiness' -TimeoutSec 5
 if($ready.data.backendReady -and $ready.data.writeApiEnabled){Write-Output 'Validated dynamic staging is already ready on 8080.';exit 0}
 throw 'Owned staging is running but not ready. Inspect its logs before restarting.'
}
# Reuse the existing Windows-protected key in child-process environment only.
# Never print it or write it into the launch argument file.
if([string]::IsNullOrWhiteSpace($env:V3_MFA_ENCRYPTION_KEY)) {
 $keyPath=Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'SMARTAssessment/secrets/v3-mfa-encryption-key.dpapi'
 if(!(Test-Path -LiteralPath $keyPath)){throw 'Existing local MFA key is required; refusing to replace it'}
 $secure=ConvertTo-SecureString ((Get-Content -LiteralPath $keyPath -Raw).Trim())
 $pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
 try {$env:V3_MFA_ENCRYPTION_KEY=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)}
 finally {[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)}
}
if([Convert]::FromBase64String($env:V3_MFA_ENCRYPTION_KEY).Length -ne 32){throw 'Invalid existing MFA key'}
$unix=$runtime.Replace('\','/')
New-Item -ItemType Directory -Force "$runtime/generated/scan-evidence" | Out-Null
$argsBase=@('-jar',$jar.Replace('\','/'),'--spring.profiles.active=v3,v3-mobile-release,v3-dynamic-staging',
 '--server.address=127.0.0.1',"--spring.datasource.url=jdbc:mysql://127.0.0.1:33319/$database`?useSSL=false&serverTimezone=UTC",
 '--spring.datasource.username=root','--spring.datasource.password=dynamic_staging_local',"--app.v3.baseline.expected-database=$database",
 '--app.v3.mobile.release.public-base-url=http://127.0.0.1:8080','--app.v3.mobile.release.adb-reverse-enabled=true',
 '--app.v3.mobile.release.mobile-wiring-verified=false','--app.v3.mobile.release.physical-scanner-verified=false',
 "--app.v3.scan-evidence.storage-directory=$unix/generated/scan-evidence",'--logging.level.org.springframework=INFO')
foreach($feature in @('scan-recovery','finalization','readback','evaluation-reference','reopen','correction','supersede')){$argsBase+="--app.v3.mobile.$feature-enabled=true"}
foreach($enabled in @('false','true')) {
 $argsFile="$runtime/backend-$enabled.args"
 ($argsBase+"--app.v3.mobile.release.http-enabled=$enabled") | ForEach-Object {'"'+$_+'"'} | Set-Content $argsFile -Encoding ascii
 $server=Start-Process -FilePath (Get-Command java).Source -ArgumentList "@$argsFile" -WorkingDirectory $workspace -WindowStyle Hidden -PassThru -RedirectStandardOutput "$runtime/backend-$enabled.out.log" -RedirectStandardError "$runtime/backend-$enabled.err.log"
 $response=$null
 for($i=0;$i -lt 100;$i++) {
  $server.Refresh();if($server.HasExited){throw "Staging startup failed in phase $enabled; inspect its private logs"}
  try {
   $request=[Net.HttpWebRequest]::Create('http://127.0.0.1:8080/api/v3/system/mobile-release-readiness');$request.Timeout=2000
   try{$reply=$request.GetResponse()}catch [Net.WebException]{$reply=$_.Exception.Response}
   if($reply){$reader=[IO.StreamReader]::new($reply.GetResponseStream());$response=$reader.ReadToEnd() | ConvertFrom-Json;$reader.Dispose();$reply.Dispose()}
  } catch {}
  if($response){break};Start-Sleep -Milliseconds 500
 }
 if(!$response){Stop-Process -Id $server.Id;throw 'Staging preflight did not respond'}
 $response | ConvertTo-Json -Depth 14 | Set-Content "$runtime/readiness-$enabled.json"
 $failures=@($response.data.checks | Where-Object {!$_.passed -and $_.check -ne 'write_api_switch'})
 if($failures.Count){Stop-Process -Id $server.Id;throw ('Staging preflight failed: '+($failures.check -join ', '))}
 if($enabled -eq 'false') {
  if($response.data.writeApiEnabled){Stop-Process -Id $server.Id;throw 'Writes were enabled before preflight'}
  Stop-Process -Id $server.Id
  for($i=0;$i -lt 40;$i++){if(!(Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue)){break};Start-Sleep -Milliseconds 250}
 } elseif(!$response.data.backendReady -or !$response.data.writeApiEnabled) {Stop-Process -Id $server.Id;throw 'Write-enabled release failed readiness'}
}
[pscustomobject]@{database=$database;backendPid=$server.Id;baseUrl='http://127.0.0.1:8080';databasePort=33319;startedAt=(Get-Date).ToString('o')} | ConvertTo-Json | Set-Content "$runtime/running.json"
Remove-Item Env:V3_MFA_ENCRYPTION_KEY -ErrorAction SilentlyContinue
Write-Output 'Dynamic isolated staging is ready at http://127.0.0.1:8080. Writes enabled after preflight; physical acceptance remains pending.'
