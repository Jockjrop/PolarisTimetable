Set-StrictMode -Version Latest

function Get-FileSha256([string]$Path) {
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Assert-ReleaseVersionGate {
    param(
        [Parameter(Mandatory = $true)][string]$CurrentTag,
        [Parameter(Mandatory = $true)][string]$CurrentVersionName,
        [Parameter(Mandatory = $true)][long]$CurrentVersionCode,
        [Parameter(Mandatory = $true)][string]$LatestTag,
        [Parameter(Mandatory = $true)][string]$LatestManifestPath,
        [string]$PublishedApkPath
    )

    if ($CurrentTag -ne "v$CurrentVersionName") {
        throw "tag $CurrentTag 与 versionName $CurrentVersionName 不一致"
    }
    $parts = $CurrentVersionName.Split('.')
    if ($parts.Count -ne 3 -or $parts[1] -gt 99 -or $parts[2] -gt 99 -or
            ([long]$parts[0] * 10000L + [long]$parts[1] * 100L + [long]$parts[2]) -ne $CurrentVersionCode) {
        throw "versionCode $CurrentVersionCode 与 versionName $CurrentVersionName 映射不一致"
    }
    $manifest = Get-Content -Raw -LiteralPath $LatestManifestPath -Encoding UTF8 | ConvertFrom-Json
    if ($manifest.packageName -ne 'com.polaris.timetable') {
        throw "最新稳定清单 packageName 不符：$($manifest.packageName)"
    }
    if ($manifest.versionCode -isnot [long] -and $manifest.versionCode -isnot [int]) {
        throw '最新稳定清单 versionCode 非法'
    }
    $latestCode = [long]$manifest.versionCode
    if ($latestCode -le 0) {
        throw '最新稳定清单 versionCode 非法'
    }
    if ($LatestTag -ne "v$($manifest.versionName)" -or
            $manifest.apk.url -notmatch "/releases/download/$([regex]::Escape($LatestTag))/") {
        throw '最新稳定 Release 的 tag、versionName 与清单 URL 不一致'
    }

    if ($LatestTag -ne $CurrentTag) {
        if ($CurrentVersionCode -le $latestCode) {
            throw "versionCode $CurrentVersionCode 未大于其他稳定标签 $LatestTag 的 $latestCode"
        }
        return [pscustomobject]@{
            SameRelease = $false
            MinSupportedVersionCode = $latestCode
        }
    }

    if ([string]::IsNullOrWhiteSpace($PublishedApkPath)) {
        throw '同一正式 tag 重跑必须提供已发布 APK'
    }
    if ($manifest.versionName -ne $CurrentVersionName -or $latestCode -ne $CurrentVersionCode) {
        throw '同一 tag 的 versionName/versionCode 与当前发布不一致'
    }
    if ([long]$manifest.minSupportedVersionCode -lt 0 -or
            [long]$manifest.minSupportedVersionCode -ge $CurrentVersionCode) {
        throw '同一 tag 的 minSupportedVersionCode 非法'
    }
    $apk = Get-Item -LiteralPath $PublishedApkPath
    $expectedName = "Polaris-$CurrentVersionName-release.apk"
    $actualSha = Get-FileSha256 $PublishedApkPath
    if ($apk.Name -ne $expectedName -or
            $manifest.apk.fileName -ne $expectedName -or
            [long]$manifest.apk.size -ne $apk.Length -or
            $manifest.apk.sha256 -ne $actualSha) {
        throw '同一 tag 的已发布 APK 与清单身份不一致'
    }
    return [pscustomobject]@{
        SameRelease = $true
        MinSupportedVersionCode = [long]$manifest.minSupportedVersionCode
    }
}

function Assert-MatchingAsset {
    param(
        [Parameter(Mandatory = $true)][string]$ExpectedPath,
        [Parameter(Mandatory = $true)][string]$ExistingPath,
        [Parameter(Mandatory = $true)][string]$Label
    )
    $expected = Get-Item -LiteralPath $ExpectedPath
    $existing = Get-Item -LiteralPath $ExistingPath
    if ($expected.Length -ne $existing.Length -or
            (Get-FileSha256 $ExpectedPath) -ne (Get-FileSha256 $ExistingPath)) {
        throw "$Label 已存在但内容不一致，拒绝覆盖"
    }
}

function Get-GiteeReleaseAction([int]$MatchingReleaseCount) {
    if ($MatchingReleaseCount -eq 0) { return 'Create' }
    if ($MatchingReleaseCount -eq 1) { return 'Reuse' }
    throw '同一 tag 存在多个 Gitee Release，拒绝继续'
}

function Resolve-GitTagCommit {
    param(
        [Parameter(Mandatory = $true)][string]$Tag,
        [AllowEmptyCollection()][string[]]$LsRemoteOutput
    )

    $tagRef = "refs/tags/$Tag"
    $peeledRef = "$tagRef^{}"
    $refs = @{}
    foreach ($line in @($LsRemoteOutput)) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        if ($line -notmatch '^([0-9a-fA-F]{40})\s+(\S+)$') {
            throw "Gitee tag ref 输出格式异常：$line"
        }
        $sha = $Matches[1].ToLowerInvariant()
        $refName = $Matches[2]
        if ($refName -ne $tagRef -and $refName -ne $peeledRef) {
            throw "Gitee tag ref 输出包含意外引用：$refName"
        }
        if ($refs.ContainsKey($refName)) {
            throw "Gitee tag ref 输出包含重复引用：$refName"
        }
        $refs[$refName] = $sha
    }

    if ($refs.Count -eq 0) { return $null }
    if (-not $refs.ContainsKey($tagRef)) {
        throw "Gitee tag ref 输出缺少引用：$tagRef"
    }
    if ($refs.ContainsKey($peeledRef)) {
        return $refs[$peeledRef]
    }
    return $refs[$tagRef]
}

function Get-GitRemoteTagCommit {
    param(
        [Parameter(Mandatory = $true)][string]$RepositoryUrl,
        [Parameter(Mandatory = $true)][string]$Tag
    )

    $output = @(& git ls-remote --tags $RepositoryUrl `
        "refs/tags/$Tag" "refs/tags/$Tag^{}" 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "读取 Gitee tag ref 失败（git ls-remote 退出码 $LASTEXITCODE）"
    }
    return Resolve-GitTagCommit -Tag $Tag -LsRemoteOutput $output
}

function Wait-GitRemoteTagCommit {
    param(
        [Parameter(Mandatory = $true)][string]$RepositoryUrl,
        [Parameter(Mandatory = $true)][string]$Tag,
        [ValidateRange(1, 100)][int]$MaxAttempts = 12,
        [ValidateRange(0, 300)][int]$RetryDelaySeconds = 5,
        [scriptblock]$Query,
        [scriptblock]$Delay
    )

    if ($null -eq $Query) {
        $Query = { param($Url, $TagName) Get-GitRemoteTagCommit -RepositoryUrl $Url -Tag $TagName }
    }
    if ($null -eq $Delay) {
        $Delay = { param($Seconds) Start-Sleep -Seconds $Seconds }
    }
    for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
        $commit = & $Query $RepositoryUrl $Tag
        if ($null -ne $commit) { return $commit }
        if ($attempt -lt $MaxAttempts) { & $Delay $RetryDelaySeconds }
    }
    throw "等待 Gitee 镜像 tag 超时：$Tag（已尝试 $MaxAttempts 次）"
}

function Assert-TagCommitMatch([string]$GitHubCommit, [string]$GiteeCommit) {
    $expected = if ($null -eq $GitHubCommit) { '' } else { $GitHubCommit.Trim() }
    $resolved = if ($null -eq $GiteeCommit) { '' } else { $GiteeCommit.Trim() }
    if ($expected -notmatch '^[0-9a-fA-F]{40}$') {
        throw "Expected GitHub commit 不是完整 40 位 SHA：$expected"
    }
    if ($resolved -notmatch '^[0-9a-fA-F]{40}$') {
        throw "Resolved Gitee tag commit 不是完整 40 位 SHA：$resolved"
    }
    $expected = $expected.ToLowerInvariant()
    $resolved = $resolved.ToLowerInvariant()
    if ($expected -ne $resolved) {
        throw "Gitee tag 与 GitHub tag commit 不一致`nExpected GitHub commit: $expected`nResolved Gitee tag commit: $resolved"
    }
}

Export-ModuleMember -Function Assert-ReleaseVersionGate, Assert-MatchingAsset, `
    Get-GiteeReleaseAction, Resolve-GitTagCommit, Get-GitRemoteTagCommit, `
    Wait-GitRemoteTagCommit, Assert-TagCommitMatch
