#Requires -Version 6
<#
.SYNOPSIS
    生成 Polaris 更新清单 latest.json 与 APK SHA-256 文件。
.DESCRIPTION
    输入全部显式提供；任何校验失败返回非零退出码：
      - APK 必须存在，文件名必须为 Polaris-<versionName>-release.apk
      - versionName 必须为语义化三段式；versionCode 必须符合 major×10000+minor×100+patch
      - Git 标签必须为 v<versionName>
      - Release notes 必须为 1..20 条非空无序列表项，单项 <= 200 字符
    输出（UTF-8 无 BOM）：
      <OutputPath>/latest.json
      <OutputPath>/Polaris-<versionName>-release.apk.sha256
    生成后会重新解析 latest.json 并执行协议自检（对应发布步骤 14）。
#>
param(
    [Parameter(Mandatory = $true)][string]$ApkPath,
    [Parameter(Mandatory = $true)][long]$VersionCode,
    [Parameter(Mandatory = $true)][string]$VersionName,
    [Parameter(Mandatory = $true)][string]$GitTag,
    [Parameter(Mandatory = $true)][string]$ReleaseNotesPath,
    [Parameter(Mandatory = $true)][string]$OutputPath,
    # minSupportedVersionCode 由发布者显式提供（上一稳定版 versionCode），
    # 不在脚本内硬编码，避免协议数据失真。
    [Parameter(Mandatory = $true)][long]$MinSupportedVersionCode
)

$ErrorActionPreference = 'Stop'
$Utf8NoBom = [System.Text.UTF8Encoding]::new($false)
$MaxApkBytes = 200L * 1024L * 1024L
$MaxVersionCode = 2100000000L
$AllowedHosts = @('github.com', 'objects.githubusercontent.com', 'release-assets.githubusercontent.com')

function Fail([string]$message) {
    Write-Error "generate-update-manifest: $message"
    exit 2
}

# --- 1. 版本与命名 -----------------------------------------------------------
if ($VersionName -notmatch '^\d+\.\d+\.\d+$') {
    Fail "versionName '$VersionName' 不是 major.minor.patch 三段式"
}
$parts = $VersionName.Split('.')
# minor/patch 必须在 0..99（防 1.2.100 与 1.3.0 碰撞），与客户端解析规则一致。
if ($parts[1] -match '^\d+$' -and [long]$parts[1] -gt 99) {
    Fail "minor 版本段 $($parts[1]) 超出 0..99"
}
if ($parts[2] -match '^\d+$' -and [long]$parts[2] -gt 99) {
    Fail "patch 版本段 $($parts[2]) 超出 0..99"
}
$expectedCode = [long]$parts[0] * 10000L + [long]$parts[1] * 100L + [long]$parts[2]
if ($expectedCode -ne $VersionCode) {
    Fail "versionCode $VersionCode 与映射值 $expectedCode 不一致"
}
if ($VersionCode -le 0 -or $VersionCode -gt $MaxVersionCode) {
    Fail "versionCode $VersionCode 超出 (0, $MaxVersionCode]"
}
if ($MinSupportedVersionCode -lt 0 -or $MinSupportedVersionCode -gt $MaxVersionCode) {
    Fail "MinSupportedVersionCode $MinSupportedVersionCode 超出 [0, $MaxVersionCode]"
}
if ($GitTag -ne "v$VersionName") {
    Fail "Git 标签 '$GitTag' 必须为 'v$VersionName'"
}
$apkFileName = "Polaris-$VersionName-release.apk"
if ([System.IO.Path]::GetFileName($ApkPath) -ne $apkFileName) {
    Fail "APK 文件名必须为 $apkFileName，实际为 '$([System.IO.Path]::GetFileName($ApkPath))'"
}

# --- 2. APK 与哈希 -----------------------------------------------------------
if (-not (Test-Path -LiteralPath $ApkPath -PathType Leaf)) {
    Fail "APK 不存在：$ApkPath"
}
$apkItem = Get-Item -LiteralPath $ApkPath
if ($apkItem.Length -le 0 -or $apkItem.Length -gt $MaxApkBytes) {
    Fail "APK 大小 $($apkItem.Length) 超出 (0, 200MiB] 约束"
}
$sha256 = (Get-FileHash -LiteralPath $ApkPath -Algorithm SHA256).Hash.ToLowerInvariant()

# --- 3. Release notes --------------------------------------------------------
if (-not (Test-Path -LiteralPath $ReleaseNotesPath -PathType Leaf)) {
    Fail "Release notes 文件不存在：$ReleaseNotesPath"
}
$noteLines = Get-Content -LiteralPath $ReleaseNotesPath -Encoding UTF8 |
    ForEach-Object { $_.Trim() } |
    Where-Object { $_.StartsWith('- ') } |
    ForEach-Object { $_.Substring(2).Trim() }
if ($noteLines.Count -lt 1 -or $noteLines.Count -gt 20) {
    Fail "Release notes 需为 1..20 条一级无序列表，实际 $($noteLines.Count) 条"
}
foreach ($note in $noteLines) {
    if ($note.Length -lt 1 -or $note.Length -gt 200) {
        Fail "Release notes 单项长度必须在 1..200：'$($note.Substring(0, [Math]::Min(40, $note.Length)))…'"
    }
}

# --- 4. 组装并写出 -----------------------------------------------------------
$manifest = [ordered]@{
    schemaVersion           = 1
    channel                 = 'stable'
    packageName             = 'com.polaris.timetable'
    versionCode             = $VersionCode
    versionName             = $VersionName
    minSdk                  = 23
    minSupportedVersionCode = $MinSupportedVersionCode
    publishedAt             = (Get-Date).ToUniversalTime().ToString("yyyy-MM-dd'T'HH:mm:ss'Z'")
    apk                     = [ordered]@{
        fileName = $apkFileName
        url      = "https://github.com/Jockjrop/PolarisTimetable/releases/download/v$VersionName/$apkFileName"
        size     = $apkItem.Length
        sha256   = $sha256
    }
    releaseNotes            = @($noteLines)
    releaseNotesUrl         = "https://github.com/Jockjrop/PolarisTimetable/releases/tag/v$VersionName"
    required                = $false
}

New-Item -ItemType Directory -Force -Path $OutputPath | Out-Null
$jsonPath = Join-Path $OutputPath 'latest.json'
$shaPath = Join-Path $OutputPath "$apkFileName.sha256"
$json = (ConvertTo-Json -InputObject $manifest -Depth 6) -replace "`r`n", "`n"
[System.IO.File]::WriteAllText($jsonPath, $json + "`n", $Utf8NoBom)
[System.IO.File]::WriteAllText($shaPath, "$sha256  $apkFileName`n", $Utf8NoBom)

# --- 5. 协议自检（发布步骤 14）-----------------------------------------------
$parsed = Get-Content -LiteralPath $jsonPath -Encoding UTF8 | ConvertFrom-Json
if ($parsed.schemaVersion -ne 1) { Fail '自检失败：schemaVersion != 1' }
if ($parsed.channel -ne 'stable') { Fail '自检失败：channel != stable' }
if ($parsed.packageName -ne 'com.polaris.timetable') { Fail '自检失败：packageName 不符' }
if ($parsed.versionCode -ne $VersionCode) { Fail '自检失败：versionCode 不一致' }
if ($parsed.versionName -ne $VersionName) { Fail '自检失败：versionName 不一致' }
if ($parsed.apk.fileName -ne $apkFileName) { Fail '自检失败：apk.fileName 不一致' }
if ($parsed.apk.size -ne $apkItem.Length) { Fail '自检失败：apk.size 不一致' }
if ($parsed.apk.sha256 -ne $sha256) { Fail '自检失败：apk.sha256 不一致' }
$apkUri = [Uri]$parsed.apk.url
if ($apkUri.Scheme -ne 'https') { Fail '自检失败：apk.url 非 HTTPS' }
if ($AllowedHosts -notcontains $apkUri.Host) { Fail '自检失败：apk.url 主机不在白名单' }
if ($apkUri.AbsolutePath -notmatch "/v$([regex]::Escape($VersionName))/") { Fail '自检失败：apk.url 标签段不符' }
$notesUri = [Uri]$parsed.releaseNotesUrl
if ($notesUri.Scheme -ne 'https' -or $AllowedHosts -notcontains $notesUri.Host) { Fail '自检失败：releaseNotesUrl 非法' }
if ($parsed.minSdk -lt 0 -or $parsed.minSupportedVersionCode -lt 0) { Fail '自检失败：min 字段非法' }
if (@($parsed.releaseNotes).Count -lt 1) { Fail '自检失败：releaseNotes 为空' }

Write-Host "latest.json 已生成：$jsonPath"
Write-Host "SHA-256 文件已生成：$shaPath"
Write-Host "APK: $apkFileName ($($apkItem.Length) bytes)"
Write-Host "sha256: $sha256"
exit 0
