# Restart Spring Boot backend, rebuild jar, free port 8080 if needed, and health-check
# Usage (from repo root or any folder):
#   powershell -ExecutionPolicy Bypass -File .\scripts\restart-backend.ps1

$ErrorActionPreference = 'Stop'

# Move to repo root (this script lives in scripts/)
$repoRoot = Join-Path $PSScriptRoot '..'
Set-Location -Path $repoRoot

Write-Host "[Restart] Repo root: $repoRoot"

# 1) Free port 8080 (if an old java is running)
try {
  $conns = Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue
  if ($conns) {
    $pids = $conns | Select-Object -ExpandProperty OwningProcess -Unique | Where-Object { $_ -ne $null }
    foreach ($pid in $pids) {
      try {
        Write-Host "[Restart] Stopping process on 8080 PID=$pid"
        Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue
      } catch {}
    }
  } else {
    # Fallback using netstat if Get-NetTCPConnection is unavailable
    Write-Host "[Restart] Trying netstat fallback to find PID on :8080"
    $lines = (& netstat -ano) | Select-String ":8080"
    foreach ($line in $lines) {
      $text = $line.ToString().Trim()
      if (-not [string]::IsNullOrWhiteSpace($text)) {
        $parts = $text -split "\s+"
        if ($parts.Length -ge 5) {
          $pidStr = $parts[-1]
          if ($pidStr -match '^[0-9]+$') {
            try {
              Write-Host "[Restart] Stopping process PID=$pidStr (netstat)"
              Stop-Process -Id ([int]$pidStr) -Force -ErrorAction SilentlyContinue
            } catch {}
          }
        }
      }
    }
  }
} catch {
  Write-Warning "[Restart] Could not query/stop port 8080. You can also run: netstat -ano | findstr :8080 -> taskkill /PID <pid> /F"
}

# 2) Build jar
Write-Host "[Restart] Packaging app (mvnw.cmd -DskipTests package)"
# Extra safety: kill any lingering java.exe that may lock the jar
try {
  Write-Host "[Restart] Killing any lingering java.exe (safety)"
  Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
} catch {}

$useSpringRun = $false
& .\mvnw.cmd -DskipTests package
if ($LASTEXITCODE -ne 0) {
  Write-Warning "[Restart] Maven package failed (code $LASTEXITCODE). Falling back to spring-boot:run"
  $useSpringRun = $true
}

if (-not $useSpringRun) {
  # 3) Start jar detached
  $jarPath = Join-Path $repoRoot 'target\Color-craze-0.0.1-SNAPSHOT.jar'
  if (!(Test-Path $jarPath)) { throw "JAR not found: $jarPath" }
  Write-Host "[Restart] Starting jar: $jarPath"
  Start-Process -FilePath java -ArgumentList '-jar', $jarPath -NoNewWindow
} else {
  Write-Host "[Restart] Starting with spring-boot:run"
  Start-Process -FilePath .\mvnw.cmd -ArgumentList '-DskipTests','spring-boot:run' -NoNewWindow
}

# 4) Health check with retries
$ok = $false
for ($i=0; $i -lt 15; $i++) {
  try {
    Start-Sleep -Seconds 1
    $r = Invoke-WebRequest -UseBasicParsing -Method Post -Uri http://localhost:8080/api/auth/guest -TimeoutSec 3
    if ($r.StatusCode -eq 200) { $ok = $true; break }
  } catch {}
}

if ($ok) {
  Write-Host "[Restart] BACKEND_OK" -ForegroundColor Green
} else {
  Write-Warning "[Restart] BACKEND_FAIL (server did not respond in time)"
}
