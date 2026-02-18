param(
  [string]$Serial = "emulator-5554",
  [string]$ApkPath = "",
  [int]$TimeoutSec = 90
)

$ErrorActionPreference = "Stop"

function Get-Adb() {
  $sdk = $env:ANDROID_SDK_ROOT
  if ([string]::IsNullOrWhiteSpace($sdk)) {
    $sdk = $env:ANDROID_HOME
  }
  if ([string]::IsNullOrWhiteSpace($sdk)) {
    $sdk = "$env:LOCALAPPDATA\Android\Sdk"
  }

  $adb = Join-Path $sdk "platform-tools\adb.exe"
  if (!(Test-Path $adb)) {
    throw "adb not found at $adb (set ANDROID_SDK_ROOT or ANDROID_HOME)"
  }
  return $adb
}

$adb = Get-Adb

function ADB([string[]]$Args) {
  & $adb @Args
}

function Dump-UiXml() {
  ADB @("-s", $Serial, "shell", "uiautomator", "dump", "/sdcard/ui.xml") | Out-Null
  return (ADB @("-s", $Serial, "shell", "cat", "/sdcard/ui.xml"))
}

function Find-BoundsCenter([string]$xml, [string]$text) {
  $pattern = 'text="' + [regex]::Escape($text) + '"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
  $m = [regex]::Match($xml, $pattern)
  if (!$m.Success) {
    return $null
  }
  $x1 = [int]$m.Groups[1].Value
  $y1 = [int]$m.Groups[2].Value
  $x2 = [int]$m.Groups[3].Value
  $y2 = [int]$m.Groups[4].Value
  $cx = [int](($x1 + $x2) / 2)
  $cy = [int](($y1 + $y2) / 2)
  return @{ x = $cx; y = $cy }
}

function Tap-Text([string]$text, [int]$retries = 5, [int]$sleepMs = 600) {
  for ($i=0; $i -lt $retries; $i++) {
    $xml = Dump-UiXml
    $pt = Find-BoundsCenter $xml $text
    if ($pt -ne $null) {
      Write-Output "TAP '$text' at $($pt.x),$($pt.y)"
      ADB @("-s", $Serial, "shell", "input", "tap", "$($pt.x)", "$($pt.y)") | Out-Null
      Start-Sleep -Milliseconds $sleepMs
      return $true
    }
    Start-Sleep -Milliseconds $sleepMs
  }
  Write-Output "NOT_FOUND '$text'"
  return $false
}

function Wait-ForText([string]$text, [int]$timeoutSec = 15) {
  $deadline = (Get-Date).AddSeconds($timeoutSec)
  while ((Get-Date) -lt $deadline) {
    $xml = Dump-UiXml
    if ($xml -match [regex]::Escape($text)) {
      Write-Output "FOUND '$text'"
      return $true
    }
    Start-Sleep -Milliseconds 500
  }
  Write-Output "TIMEOUT_WAIT '$text'"
  return $false
}

# --- Start ---
Write-Output "SERIAL=$Serial"
ADB @("devices", "-l")

if (![string]::IsNullOrWhiteSpace($ApkPath)) {
  if (!(Test-Path $ApkPath)) {
    throw "APK not found: $ApkPath"
  }
  Write-Output "INSTALL $ApkPath"
  ADB @("-s", $Serial, "install", "--no-streaming", "-r", $ApkPath)
}

# Ensure permission (no-op if already)
ADB @("-s", $Serial, "shell", "pm", "grant", "com.listlens.app", "android.permission.CAMERA") | Out-Null
ADB @("-s", $Serial, "shell", "appops", "set", "com.listlens.app", "CAMERA", "allow") | Out-Null

Write-Output "LAUNCH"
ADB @("-s", $Serial, "shell", "am", "force-stop", "com.listlens.app") | Out-Null
ADB @("-s", $Serial, "shell", "am", "start", "-n", "com.listlens.app/.MainActivity") | Out-Null
Start-Sleep -Milliseconds 800

# Home -> Books
if (!(Tap-Text "Books")) { throw "Failed to tap Books" }

# Scan -> Debug dialog
if (!(Tap-Text "Debug: Enter ISBN")) { throw "Failed to tap Debug ISBN" }
if (!(Wait-ForText "Enter ISBN" 10)) { throw "Debug dialog didn't open" }

# Use (ISBN defaults to a known-good value)
if (!(Tap-Text "Use" 5 700)) { throw "Failed to tap Use" }

# Confirm
if (!(Wait-ForText "Detected ISBN:" 15)) { throw "Did not reach Confirm" }
if (!(Wait-ForText "Title:" 15)) { throw "No Title on Confirm" }

# Go to Photos
if (!(Tap-Text "Use this" 5 700)) { throw "Failed to tap Use this" }

# Assert photos screen counter (requested)
if (!(Wait-ForText "Photos: 0/5" 15)) { throw "Photos counter not present / not 0/5" }

Write-Output "SMOKE_OK"
exit 0
