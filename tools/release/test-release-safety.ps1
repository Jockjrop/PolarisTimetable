#Requires -Version 7
$ErrorActionPreference = 'Stop'
Import-Module "$PSScriptRoot/ReleaseSafety.psm1" -Force

$root = Join-Path ([IO.Path]::GetTempPath()) ("polaris-release-test-" + [guid]::NewGuid())
New-Item -ItemType Directory -Path $root | Out-Null
try {
    $apk = Join-Path $root 'Polaris-1.27.2-release.apk'
    [IO.File]::WriteAllBytes($apk, [byte[]](1, 2, 3, 4))
    $sha = (Get-FileHash $apk -Algorithm SHA256).Hash.ToLowerInvariant()
    function Write-Manifest([string]$Path, [string]$VersionName, [long]$VersionCode,
            [string]$Hash, [long]$Size) {
        [ordered]@{
            packageName = 'com.polaris.timetable'
            versionName = $VersionName
            versionCode = $VersionCode
            minSupportedVersionCode = 12701
            apk = [ordered]@{
                fileName = "Polaris-$VersionName-release.apk"
                url = "https://github.com/Jockjrop/PolarisTimetable/releases/download/v$VersionName/Polaris-$VersionName-release.apk"
                size = $Size
                sha256 = $Hash
            }
        } | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $Path -Encoding utf8NoBOM
    }
    function Expect-Failure([scriptblock]$Action, [string]$Name) {
        try { & $Action; throw "未拒绝：$Name" } catch {
            if ($_.Exception.Message -eq "未拒绝：$Name") { throw }
        }
    }
    function Expect-FailureMatch([scriptblock]$Action, [string]$Pattern, [string]$Name) {
        try { & $Action; throw "未拒绝：$Name" } catch {
            if ($_.Exception.Message -eq "未拒绝：$Name") { throw }
            if ($_.Exception.Message -notmatch $Pattern) { throw "失败信息不明确：$Name" }
        }
    }
    function New-TestHttpError([int]$StatusCode, [int]$RetryAfter = -1) {
        $exception = [Exception]::new("test HTTP $StatusCode")
        $exception.Data['StatusCode'] = $StatusCode
        if ($RetryAfter -ge 0) { $exception.Data['RetryAfter'] = $RetryAfter }
        return $exception
    }

    $previous = Join-Path $root 'previous.json'
    Write-Manifest $previous '1.27.1' 12701 $sha 4
    $result = Assert-ReleaseVersionGate -CurrentTag v1.27.2 -CurrentVersionName 1.27.2 `
        -CurrentVersionCode 12702 -LatestTag v1.27.1 -LatestManifestPath $previous
    if ($result.SameRelease -or $result.MinSupportedVersionCode -ne 12701) { throw '新版本单调校验失败' }

    $same = Join-Path $root 'same.json'
    Write-Manifest $same '1.27.2' 12702 $sha 4
    $result = Assert-ReleaseVersionGate -CurrentTag v1.27.2 -CurrentVersionName 1.27.2 `
        -CurrentVersionCode 12702 -LatestTag v1.27.2 -LatestManifestPath $same -PublishedApkPath $apk
    if (-not $result.SameRelease) { throw '同 Release 重跑未放行' }

    Expect-Failure {
        Assert-ReleaseVersionGate -CurrentTag v1.27.2 -CurrentVersionName 1.27.2 `
            -CurrentVersionCode 12702 -LatestTag v1.27.1 -LatestManifestPath $same
    } '同 versionCode 不同 tag'
    $differentHash = Join-Path $root 'different-hash.json'
    Write-Manifest $differentHash '1.27.2' 12702 ('0' * 64) 4
    Expect-Failure {
        Assert-ReleaseVersionGate -CurrentTag v1.27.2 -CurrentVersionName 1.27.2 `
            -CurrentVersionCode 12702 -LatestTag v1.27.2 -LatestManifestPath $differentHash -PublishedApkPath $apk
    } '同 tag 不同 SHA'
    $newer = Join-Path $root 'newer.json'
    Write-Manifest $newer '1.27.3' 12703 $sha 4
    Expect-Failure {
        Assert-ReleaseVersionGate -CurrentTag v1.27.2 -CurrentVersionName 1.27.2 `
            -CurrentVersionCode 12702 -LatestTag v1.27.3 -LatestManifestPath $newer
    } '最新版本更高'

    if ((Get-GiteeReleaseAction 0) -ne 'Create') { throw 'Gitee Release 创建分支失败' }
    if ((Get-GiteeReleaseAction 1) -ne 'Reuse') { throw 'Gitee Release 复用分支失败' }
    $copy = Join-Path $root 'copy.apk'
    Copy-Item -LiteralPath $apk -Destination $copy
    Assert-MatchingAsset -ExpectedPath $apk -ExistingPath $copy -Label APK
    [IO.File]::WriteAllBytes($copy, [byte[]](4, 3, 2, 1))
    Expect-Failure { Assert-MatchingAsset -ExpectedPath $apk -ExistingPath $copy -Label APK } 'Gitee APK hash 冲突'
    $expectedTagCommit = '45be3fe662eda26d1fb77ab872bd3864c1224488'
    $apiTags = @(1..9 | ForEach-Object {
        $name = if ($_ -eq 7) { 'v1.27.2' } elseif ($_ -eq 5) { 'v1.27.0' } `
            elseif ($_ -eq 6) { 'v1.27.1' } else { "v1.26.$_" }
        $commit = if ($_ -eq 7) { $expectedTagCommit } else { '{0:x40}' -f $_ }
        [pscustomobject]@{ name = $name; commit = [pscustomobject]@{ sha = $commit } }
    })
    $validTag = $apiTags[6]
    # Invoke-RestMethod 可把 JSON 顶层数组作为一个对象输出；用嵌套形态复现 Build #71。
    $apiCommit = Resolve-GiteeApiTagCommit -Tag v1.27.2 -Tags (,$apiTags)
    if ($apiCommit -isnot [string] -or $apiCommit -cne $expectedTagCommit) {
        throw 'Gitee 多 tag API 响应未解析为目标 scalar string'
    }
    Assert-TagCommitMatch -GitHubCommit $expectedTagCommit -GiteeCommit $apiCommit
    Expect-Failure { Resolve-GiteeApiTagCommit -Tag v1.27.2 -Tags @() } 'Gitee tag 不存在'
    Expect-Failure {
        Resolve-GiteeApiTagCommit -Tag v1.27.2 -Tags @($validTag, $validTag)
    } 'Gitee tag 重复'
    $shortTag = [pscustomobject]@{
        name = 'v1.27.2'; commit = [pscustomobject]@{ sha = '45be3fe' }
    }
    Expect-Failure { Resolve-GiteeApiTagCommit -Tag v1.27.2 -Tags @($shortTag) } 'Gitee API 短 SHA'
    Expect-Failure { Assert-TagCommitMatch -GitHubCommit ('a' * 40) -GiteeCommit ('b' * 40) } 'tag commit 冲突'
    Expect-Failure { Assert-TagCommitMatch -GitHubCommit ('a' * 7) -GiteeCommit ('a' * 40) } 'GitHub 短 SHA'

    $apiAttempts = 0
    $delays = @()
    $apiResult = Invoke-GiteeApiRequest -Method Get -Uri 'https://gitee.test/tags' `
        -Request {
            $script:apiAttempts++
            if ($script:apiAttempts -eq 1) { throw (New-TestHttpError 429 7) }
            return 'ok'
        } -Delay { param($Seconds) $script:delays += $Seconds }
    if ($apiResult -ne 'ok' -or $apiAttempts -ne 2 -or $delays.Count -ne 1 -or $delays[0] -ne 7) {
        throw 'HTTP 429 未遵循 Retry-After'
    }

    $apiAttempts = 0
    $delays = @()
    $apiResult = Invoke-GiteeApiRequest -Method Get -Uri 'https://gitee.test/tags' `
        -Request {
            $script:apiAttempts++
            if ($script:apiAttempts -lt 3) { throw (New-TestHttpError 429) }
            return 'ok'
        } -Delay { param($Seconds) $script:delays += $Seconds }
    if ($apiResult -ne 'ok' -or $apiAttempts -ne 3 -or
            (@($delays) -join ',') -ne '2,5') {
        throw 'HTTP 429 指数退避错误'
    }

    $apiAttempts = 0
    $delays = @()
    Expect-FailureMatch {
        Invoke-GiteeApiRequest -Method Get -Uri 'https://gitee.test/tags' -MaxAttempts 3 `
            -Request { $script:apiAttempts++; throw (New-TestHttpError 429) } `
            -Delay { param($Seconds) $script:delays += $Seconds }
    } 'HTTP 429.*3 次' 'HTTP 429 超过上限'
    if ($apiAttempts -ne 3 -or (@($delays) -join ',') -ne '2,5') {
        throw 'HTTP 429 超限重试次数错误'
    }

    Expect-FailureMatch {
        Invoke-GiteeApiRequest -Method Get -Uri 'https://gitee.test/tags' `
            -Request { throw (New-TestHttpError 401) } -Delay { throw '401 不应等待' }
    } '认证失败.*HTTP 401' 'HTTP 401'
    Expect-FailureMatch {
        Invoke-GiteeApiRequest -Method Get -Uri 'https://gitee.test/tags' `
            -Request { throw (New-TestHttpError 403) } -Delay { throw '403 不应等待' }
    } '权限不足.*HTTP 403' 'HTTP 403'

    $apiAttempts = 0
    $delays = @()
    Expect-FailureMatch {
        Invoke-GiteeApiRequest -Method Get -Uri 'https://gitee.test/tags' -MaxAttempts 3 `
            -Request { $script:apiAttempts++; throw (New-TestHttpError 503) } `
            -Delay { param($Seconds) $script:delays += $Seconds }
    } '服务错误.*HTTP 503.*3 次' 'HTTP 5xx'
    if ($apiAttempts -ne 3 -or (@($delays) -join ',') -ne '2,5') {
        throw 'HTTP 5xx 有限重试错误'
    }

    $workflow = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot '../../.github/workflows/build.yml')
    $expectedInstrumentedCondition = "    if: github.event_name != 'workflow_dispatch' && (github.event_name == 'pull_request' || github.ref == 'refs/heads/main' || startsWith(github.ref, 'refs/tags/v'))"
    if ($workflow -notmatch "(?m)^$([regex]::Escape($expectedInstrumentedCondition))\r?$") {
        throw 'instrumented job 未使用预期的单行 fail-closed 条件'
    }
    foreach ($requiredCondition in @(
            "    if: github.event_name != 'workflow_dispatch'",
            "    if: startsWith(github.ref, 'refs/tags/v') && needs.instrumented.result == 'success'",
            "    if: github.event_name == 'workflow_dispatch'")) {
        if ($workflow -notmatch "(?m)^$([regex]::Escape($requiredCondition))\r?$") {
            throw "workflow 缺少预期 job 条件：$requiredCondition"
        }
    }
    function Test-InstrumentedCondition([string]$EventName, [string]$Ref) {
        return $EventName -ne 'workflow_dispatch' -and (
            $EventName -eq 'pull_request' -or $Ref -eq 'refs/heads/main' -or $Ref.StartsWith('refs/tags/v'))
    }
    if (Test-InstrumentedCondition workflow_dispatch refs/heads/main) {
        throw 'workflow_dispatch 不应启动 instrumented'
    }
    if (-not (Test-InstrumentedCondition push refs/heads/main)) { throw 'push main 应启动 instrumented' }
    if (-not (Test-InstrumentedCondition push refs/tags/v1.27.2)) { throw 'push tag 应启动 instrumented' }
    if (-not (Test-InstrumentedCondition pull_request refs/pull/1/merge)) { throw 'PR 应启动 instrumented' }

    $recovery = $workflow.Substring($workflow.IndexOf('  recover-gitee-release:'))
    if ($recovery -match 'gh release (create|edit|upload|delete)') {
        throw 'workflow_dispatch 恢复 job 不得修改 GitHub Release'
    }
    $publisher = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'publish-gitee-release.ps1')
    if ($publisher -match 'ls-remote|GIT_ASKPASS|GIT_TERMINAL_PROMPT|credential\.helper' -or
            ([regex]::Matches($publisher, "'/tags\?per_page=100&page=1'")).Count -ne 1) {
        throw 'Gitee 发布路径未彻底收敛为单次 tags API 调用'
    }
    Write-Host 'release safety tests: 27 passed'
} finally {
    Remove-Item -LiteralPath $root -Recurse -Force
}
