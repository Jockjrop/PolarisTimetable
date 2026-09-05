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
    $recovery = $workflow.Substring($workflow.IndexOf('  recover-gitee-release:'))
    if ($recovery -match 'gh release (create|edit|upload|delete)') {
        throw 'workflow_dispatch 恢复 job 不得修改 GitHub Release'
    }
    Write-Host 'release safety tests: 17 passed'
} finally {
    Remove-Item -LiteralPath $root -Recurse -Force
}
