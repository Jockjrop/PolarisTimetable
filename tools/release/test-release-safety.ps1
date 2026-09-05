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
    Assert-TagCommitMatch -GitHubCommit ('a' * 40) -GiteeCommit ('a' * 40)
    Expect-Failure { Assert-TagCommitMatch -GitHubCommit ('a' * 40) -GiteeCommit ('b' * 40) } 'tag commit 冲突'
    Expect-Failure { Assert-TagCommitMatch -GitHubCommit ('a' * 40) -GiteeCommit ('a' * 7) } '短 tag SHA'

    $lightweightCommit = Resolve-GitTagCommit -Tag v1.27.2 -LsRemoteOutput @(
        "$('c' * 40)`trefs/tags/v1.27.2"
    )
    if ($lightweightCommit -ne ('c' * 40)) { throw 'lightweight tag 解析失败' }

    $annotatedCommit = Resolve-GitTagCommit -Tag v1.27.2 -LsRemoteOutput @(
        "$('d' * 40)`trefs/tags/v1.27.2",
        "$('e' * 40)`trefs/tags/v1.27.2^{}"
    )
    if ($annotatedCommit -ne ('e' * 40)) { throw 'annotated tag 未使用 peeled commit' }
    Expect-Failure {
        Resolve-GitTagCommit -Tag v1.27.2 -LsRemoteOutput @(
            "$('c' * 40)`trefs/tags/v1.27.2",
            "$('d' * 40)`trefs/tags/v1.27.2"
        )
    } '重复 tag ref'
    Expect-Failure {
        Resolve-GitTagCommit -Tag v1.27.2 -LsRemoteOutput @(
            "$('c' * 39)`trefs/tags/v1.27.2"
        )
    } '异常 tag ref 格式'

    $attempts = 0
    $delays = 0
    Expect-Failure {
        Wait-GitRemoteTagCommit -RepositoryUrl 'https://gitee.example/repo.git' -Tag v1.27.2 `
            -MaxAttempts 3 -RetryDelaySeconds 0 `
            -Query { param($Url, $TagName) $script:attempts++; return $null } `
            -Delay { param($Seconds) $script:delays++ }
    } '找不到 tag 时有限重试'
    if ($attempts -ne 3 -or $delays -ne 2) { throw '找不到 tag 时重试次数不正确' }

    $apiCommit = 'f' * 40
    $gitRefCommit = 'a' * 40
    if ($apiCommit -eq $gitRefCommit) { throw 'REST API 差异测试数据无效' }
    Assert-TagCommitMatch -GitHubCommit ('a' * 40) -GiteeCommit $gitRefCommit

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

    $testToken = 'gitee-test-token-must-not-leak'
    $capturedRequest = $null
    $askPassContents = $null
    $authenticatedCommit = Get-GitRemoteTagCommit `
        -RepositoryUrl 'https://Jockjrop@gitee.com/Jockjrop/polaris-course-schedule.git' `
        -Tag v1.27.2 -Username Jockjrop -Token $testToken -CommandRunner {
            param($Request)
            $script:capturedRequest = $Request
            $script:askPassContents = @($Request.AskPassFiles | ForEach-Object {
                Get-Content -Raw -LiteralPath $_
            }) -join "`n"
            return [pscustomobject]@{
                ExitCode = 0
                StandardOutput = "$('a' * 40)`trefs/tags/v1.27.2`n"
                StandardError = ''
            }
        }
    if ($authenticatedCommit -ne ('a' * 40)) { throw '认证 Git ls-remote 成功结果解析失败' }
    if ($capturedRequest.Environment.GIT_ASKPASS -ne $capturedRequest.AskPassPath -or
            $capturedRequest.Environment.GIT_ASKPASS_REQUIRE -ne 'force' -or
            $capturedRequest.Environment.GIT_TERMINAL_PROMPT -ne '0' -or
            $capturedRequest.Environment.GITEE_GIT_USERNAME -ne 'Jockjrop' -or
            $capturedRequest.Environment.GITEE_TOKEN -ne $testToken) {
        throw 'GIT_ASKPASS 进程环境不完整'
    }
    $commandText = @($capturedRequest.FileName) + @($capturedRequest.Arguments) -join ' '
    if ($commandText.Contains($testToken) -or $askPassContents.Contains($testToken) -or
            $capturedRequest.Arguments -notcontains 'credential.helper=') {
        throw 'Git 命令、URL 或 askpass 文件泄露 Token，或未清空 credential.helper'
    }
    Expect-Failure {
        Get-GitRemoteTagCommit `
            -RepositoryUrl 'https://Jockjrop@gitee.com/Jockjrop/polaris-course-schedule.git' `
            -Tag v1.27.2 -Username Jockjrop -Token '' -CommandRunner { throw '不应执行匿名 Git' }
    } '缺少凭据时拒绝匿名访问'

    try {
        Get-GitRemoteTagCommit `
            -RepositoryUrl 'https://Jockjrop@gitee.com/Jockjrop/polaris-course-schedule.git' `
            -Tag v1.27.2 -Username Jockjrop -Token $testToken -CommandRunner {
                return [pscustomobject]@{
                    ExitCode = 128
                    StandardOutput = ''
                    StandardError = "fatal: unable to access repository: HTTP 403 Forbidden $testToken"
                }
            }
        throw '认证 Git exit 128 未被拒绝'
    } catch {
        $failure = $_.Exception.Message
        if ($failure -eq '认证 Git exit 128 未被拒绝') { throw }
        if ($failure -notmatch 'category: HTTP 403' -or $failure -notmatch 'git exit code: 128' -or
                $failure -notmatch 'stderr:' -or $failure.Contains($testToken)) {
            throw '认证 Git exit 128 未保留安全、可诊断的 stderr'
        }
    }

    $transportCases = @(
        @{ Error = 'fatal: Could not resolve host: gitee.com'; Category = 'DNS' },
        @{ Error = 'fatal: SSL certificate problem'; Category = 'TLS' },
        @{ Error = 'fatal: HTTP 401 Unauthorized'; Category = 'HTTP 401' },
        @{ Error = 'fatal: HTTP 403 Forbidden'; Category = 'HTTP 403' },
        @{ Error = 'fatal: Failed to connect to gitee.com'; Category = 'Connection' },
        @{ Error = 'fatal: Authentication failed'; Category = 'Authentication' },
        @{ Error = 'fatal: unexpected transport failure'; Category = 'Other' }
    )
    foreach ($case in $transportCases) {
        $script:transportError = $case.Error
        try {
            Get-GitRemoteTagCommit `
                -RepositoryUrl 'https://Jockjrop@gitee.com/Jockjrop/polaris-course-schedule.git' `
                -Tag v1.27.2 -Username Jockjrop -Token $testToken -CommandRunner {
                    return [pscustomobject]@{
                        ExitCode = 128
                        StandardOutput = ''
                        StandardError = $script:transportError
                    }
                }
            throw "Git transport 分类未失败：$($case.Category)"
        } catch {
            if ($_.Exception.Message -eq "Git transport 分类未失败：$($case.Category)") { throw }
            if ($_.Exception.Message -notmatch "category: $([regex]::Escape($case.Category))") {
                throw "Git transport 错误分类失败：$($case.Category)"
            }
        }
    }

    $recovery = $workflow.Substring($workflow.IndexOf('  recover-gitee-release:'))
    if ($recovery -match 'gh release (create|edit|upload|delete)') {
        throw 'workflow_dispatch 恢复 job 不得修改 GitHub Release'
    }
    Write-Host 'release safety tests: 25 passed'
} finally {
    Remove-Item -LiteralPath $root -Recurse -Force
}
