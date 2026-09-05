#Requires -Version 7
param(
    [Parameter(Mandatory = $true)][string]$Owner,
    [Parameter(Mandatory = $true)][string]$Repo,
    [Parameter(Mandatory = $true)][string]$Tag,
    [Parameter(Mandatory = $true)][string]$ExpectedCommitSha,
    [Parameter(Mandatory = $true)][string]$ApkPath,
    [Parameter(Mandatory = $true)][string]$GitHubManifestPath,
    [Parameter(Mandatory = $true)][string]$ReleaseNotesPath,
    [Parameter(Mandatory = $true)][string]$OutputPath
)

$ErrorActionPreference = 'Stop'
Import-Module "$PSScriptRoot/ReleaseSafety.psm1" -Force
$token = $env:GITEE_TOKEN
if ([string]::IsNullOrWhiteSpace($token)) {
    throw 'GITEE_TOKEN 未配置，无法发布 Gitee 更新源'
}
$apiRoot = "https://gitee.com/api/v5/repos/$Owner/$Repo"
$headers = @{ Authorization = "Bearer $token"; Accept = 'application/json' }
$encodedTag = [Uri]::EscapeDataString($Tag)

function Invoke-GiteeJson([string]$Method, [string]$Path, [hashtable]$Body) {
    $parameters = @{
        Uri = "$apiRoot$Path"
        Method = $Method
        Headers = $headers
        ErrorAction = 'Stop'
    }
    if ($null -ne $Body) {
        $parameters.Body = $Body
    }
    try {
        return Invoke-RestMethod @parameters
    } catch {
        throw "Gitee API 请求失败：$Method $Path"
    }
}

# Pull 镜像可能稍晚同步 tag。只等待，不创建 tag；并核对它指向 GitHub 的同一 commit。
$tagEntry = $null
for ($attempt = 1; $attempt -le 12; $attempt++) {
    $tags = @(Invoke-GiteeJson 'Get' '/tags?per_page=100&page=1' $null)
    $tagEntry = $tags | Where-Object { $_.name -eq $Tag } | Select-Object -First 1
    if ($null -ne $tagEntry) { break }
    if ($attempt -lt 12) { Start-Sleep -Seconds 5 }
}
if ($null -eq $tagEntry) {
    throw '等待 Gitee 镜像 tag 超时'
}
Assert-TagCommitMatch -GitHubCommit $ExpectedCommitSha -GiteeCommit $tagEntry.commit.sha

$githubManifest = Get-Content -Raw -LiteralPath $GitHubManifestPath -Encoding UTF8 | ConvertFrom-Json
$apkItem = Get-Item -LiteralPath $ApkPath
$actualSha = (Get-FileHash -LiteralPath $ApkPath -Algorithm SHA256).Hash.ToLowerInvariant()
if ($apkItem.Name -ne $githubManifest.apk.fileName -or
        $apkItem.Length -ne $githubManifest.apk.size -or
        $actualSha -ne $githubManifest.apk.sha256) {
    throw '待上传 Gitee 的 APK 与已复验 GitHub 清单不是同一个文件'
}

# 一个 tag 只允许一个 Release；重复运行时复用已有 Release。
$releases = @(Invoke-GiteeJson 'Get' '/releases?per_page=100&page=1' $null)
$matchingReleases = @($releases | Where-Object { $_.tag_name -eq $Tag })
$releaseAction = Get-GiteeReleaseAction $matchingReleases.Count
$release = $matchingReleases | Select-Object -First 1
$releaseBody = Get-Content -Raw -LiteralPath $ReleaseNotesPath -Encoding UTF8
$releaseFields = @{
    tag_name = $Tag
    target_commitish = $ExpectedCommitSha
    name = $Tag
    body = $releaseBody
    prerelease = 'false'
}
if ($releaseAction -eq 'Create') {
    $release = Invoke-GiteeJson 'Post' '/releases' $releaseFields
} else {
    $release = Invoke-GiteeJson 'Patch' "/releases/$($release.id)" $releaseFields
}
if ($null -eq $release.id) {
    throw 'Gitee Release 未返回 release id'
}

$attachments = @(Invoke-GiteeJson 'Get' "/releases/$($release.id)/attach_files?per_page=100&page=1" $null)

function Upload-GiteeAsset([string]$Path) {
    try {
        $uploadParameters = @{
            Method = 'Post'
            Uri = "$apiRoot/releases/$($release.id)/attach_files"
            Headers = $headers
            Form = @{ file = Get-Item -LiteralPath $Path }
        }
        return Invoke-RestMethod @uploadParameters
    } catch {
        throw "Gitee Release 附件上传失败：$([IO.Path]::GetFileName($Path))"
    }
}

function Find-GiteeAsset([string]$Name) {
    $matches = @($attachments | Where-Object { $_.name -eq $Name })
    if ($matches.Count -gt 1) {
        throw "Gitee Release 存在多个同名附件 $Name，拒绝继续"
    }
    return $matches | Select-Object -First 1
}

$checkDirectory = Join-Path $OutputPath '.existing-assets'
New-Item -ItemType Directory -Force -Path $checkDirectory | Out-Null
function Assert-RemoteAssetMatches([object]$Attachment, [string]$ExpectedPath) {
    if ($Attachment.browser_download_url -notmatch '^https://gitee\.com/') {
        throw "Gitee 附件 $($Attachment.name) 未返回可信的公开下载地址"
    }
    $downloaded = Join-Path $checkDirectory $Attachment.name
    try {
        Invoke-WebRequest -Uri $Attachment.browser_download_url -OutFile $downloaded `
            -MaximumRedirection 5 -ConnectionTimeoutSeconds 15 -OperationTimeoutSeconds 300
    } catch {
        throw "Gitee 已有附件下载验证失败：$($Attachment.name)"
    }
    Assert-MatchingAsset -ExpectedPath $ExpectedPath -ExistingPath $downloaded -Label $Attachment.name
}

function Ensure-GiteeAsset([string]$Path) {
    $name = [IO.Path]::GetFileName($Path)
    $existing = Find-GiteeAsset $name
    if ($null -ne $existing) {
        Assert-RemoteAssetMatches $existing $Path
        return $existing
    }
    return Upload-GiteeAsset $Path
}

# APK 先复用或上传；已有同名 APK 必须与 GitHub 正式资产完全一致。
$apkAttachment = Ensure-GiteeAsset $ApkPath
if ($apkAttachment.browser_download_url -notmatch '^https://gitee\.com/') {
    throw 'Gitee APK 附件未返回可信的公开下载地址'
}

& "$PSScriptRoot/generate-update-manifest.ps1" `
    -ApkPath $ApkPath `
    -VersionCode $githubManifest.versionCode `
    -VersionName $githubManifest.versionName `
    -GitTag $Tag `
    -ReleaseNotesPath $ReleaseNotesPath `
    -OutputPath $OutputPath `
    -MinSupportedVersionCode $githubManifest.minSupportedVersionCode `
    -ApkUrl $apkAttachment.browser_download_url `
    -ReleaseNotesUrl "https://gitee.com/$Owner/$Repo/releases/tag/$encodedTag" `
    -PublishedAt $githubManifest.publishedAt
if ($LASTEXITCODE -ne 0) { throw 'Gitee latest.json 生成或自检失败' }

$giteeManifestPath = Join-Path $OutputPath 'latest.json'
$giteeManifest = Get-Content -Raw -LiteralPath $giteeManifestPath -Encoding UTF8 | ConvertFrom-Json
$checks = @('schemaVersion', 'channel', 'packageName', 'versionCode', 'versionName', 'minSdk',
    'minSupportedVersionCode', 'publishedAt', 'required')
foreach ($field in $checks) {
    if ($githubManifest.$field -ne $giteeManifest.$field) {
        throw "GitHub/Gitee 清单字段不一致：$field"
    }
}
$githubNotesJson = @($githubManifest.releaseNotes) | ConvertTo-Json -Compress
$giteeNotesJson = @($giteeManifest.releaseNotes) | ConvertTo-Json -Compress
if ($githubManifest.apk.fileName -ne $giteeManifest.apk.fileName -or
        $githubManifest.apk.size -ne $giteeManifest.apk.size -or
        $githubManifest.apk.sha256 -ne $giteeManifest.apk.sha256 -or
        $githubNotesJson -ne $giteeNotesJson) {
    throw 'GitHub/Gitee 清单的 APK 身份或发布说明不一致'
}

Ensure-GiteeAsset (Join-Path $OutputPath "$($apkItem.Name).sha256") | Out-Null
$manifestAttachment = Ensure-GiteeAsset $giteeManifestPath
if ($manifestAttachment.browser_download_url -notmatch '^https://gitee\.com/') {
    throw 'Gitee latest.json 未返回可信的公开下载地址'
}

if (-not [string]::IsNullOrWhiteSpace($env:GITHUB_OUTPUT)) {
    "manifest_url=$($manifestAttachment.browser_download_url)" | Out-File `
        -FilePath $env:GITHUB_OUTPUT -Encoding utf8 -Append
    "apk_url=$($apkAttachment.browser_download_url)" | Out-File `
        -FilePath $env:GITHUB_OUTPUT -Encoding utf8 -Append
}
Remove-Item -LiteralPath $checkDirectory -Recurse -Force
Write-Host "Gitee Release 已同步：$Tag（APK/latest.json/.sha256）"
