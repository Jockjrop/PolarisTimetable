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
    function New-TestHttpError([int]$StatusCode, [int]$RetryAfter = -1, [string]$Message = "test HTTP $StatusCode") {
        $exception = [Exception]::new($Message)
        $exception.Data['StatusCode'] = $StatusCode
        if ($RetryAfter -ge 0) { $exception.Data['RetryAfter'] = $RetryAfter }
        return $exception
    }

    $expectedPublishedAt = '2026-09-05T05:10:27Z'
    if ((ConvertTo-UtcPublishedAt '2026-09-05T05:10:27Z') -cne $expectedPublishedAt) {
        throw 'ISO UTC publishedAt 规范化失败'
    }
    $utcDateTime = [DateTime]::SpecifyKind([DateTime]::new(2026, 9, 5, 5, 10, 27),
        [DateTimeKind]::Utc)
    if ((ConvertTo-UtcPublishedAt $utcDateTime) -cne $expectedPublishedAt) {
        throw 'DateTime publishedAt 规范化失败'
    }
    $offsetDateTime = [DateTimeOffset]::new(2026, 9, 5, 13, 10, 27, [TimeSpan]::FromHours(8))
    if ((ConvertTo-UtcPublishedAt $offsetDateTime) -cne $expectedPublishedAt) {
        throw 'DateTimeOffset publishedAt UTC 换算失败'
    }
    Expect-Failure { ConvertTo-UtcPublishedAt '09/05/2026 05:10:27' } '区域格式 publishedAt'
    Expect-Failure { ConvertTo-UtcPublishedAt 'not-a-date' } '无效 publishedAt'

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
    $expectedTagCommit = '45be3fe662eda26d1fb77ab872bd3864c1224488'
    $reusableRelease = [pscustomobject]@{ id = [long]42; tag_name = 'v1.27.2'; prerelease = $false; target_commitish = $expectedTagCommit }
    if (-not (Assert-GiteeReleaseForReuse -Release $reusableRelease -Tag v1.27.2 -ExpectedCommitSha $expectedTagCommit)) {
        throw 'Gitee Release 完整 target_commitish 未验证'
    }
    Expect-Failure { Assert-GiteeReleaseForReuse -Release ([pscustomobject]@{ id = 0; tag_name = 'v1.27.2'; prerelease = $false }) -Tag v1.27.2 -ExpectedCommitSha $expectedTagCommit } 'Gitee Release 非法 id'
    Expect-Failure { Assert-GiteeReleaseForReuse -Release ([pscustomobject]@{ id = 42; tag_name = 'v1.27.1'; prerelease = $false }) -Tag v1.27.2 -ExpectedCommitSha $expectedTagCommit } 'Gitee Release tag 不一致'
    Expect-Failure { Assert-GiteeReleaseForReuse -Release ([pscustomobject]@{ id = 42; tag_name = 'v1.27.2'; prerelease = $true }) -Tag v1.27.2 -ExpectedCommitSha $expectedTagCommit } 'Gitee prerelease 不可复用'
    if (-not (Test-GiteeBrowserDownloadUrl 'https://gitee.com/Jockjrop/repo/releases/download/v1.27.2/a.apk') -or
            -not (Test-GiteeBrowserDownloadUrl 'https://foruda.gitee.com/attachment/a.apk') -or
            (Test-GiteeBrowserDownloadUrl 'https://example.test/a.apk')) {
        throw 'Gitee 附件 URL 白名单错误'
    }
    $copy = Join-Path $root 'copy.apk'
    Copy-Item -LiteralPath $apk -Destination $copy
    Assert-MatchingAsset -ExpectedPath $apk -ExistingPath $copy -Label APK
    [IO.File]::WriteAllBytes($copy, [byte[]](4, 3, 2, 1))
    Expect-Failure { Assert-MatchingAsset -ExpectedPath $apk -ExistingPath $copy -Label APK } 'Gitee APK hash 冲突'
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

    foreach ($status in @(400, 415, 422)) {
        Expect-FailureMatch {
            Invoke-GiteeApiRequest -Method Post -Uri 'https://gitee.test/releases' -Headers @{ Authorization = 'Bearer secret-token' } `
                -Request { throw (New-TestHttpError $status -Message "Gitee says token=secret-token status $status") }
        } "HTTP $status.*Gitee:.*token=\[redacted\]" "HTTP $status 安全诊断"
    }

    # 完整恢复模拟：已有 Release + 已有 APK，只补齐 .sha256/latest.json。
    $recoveryRoot = Join-Path $root 'recovery'
    $recoveryOutput = Join-Path $recoveryRoot 'output'
    New-Item -ItemType Directory -Force -Path $recoveryOutput | Out-Null
    $recoveryApk = Join-Path $recoveryRoot 'Polaris-1.27.2-release.apk'
    [IO.File]::WriteAllBytes($recoveryApk, [byte[]](9, 8, 7, 6, 5))
    $recoveryApkItem = Get-Item -LiteralPath $recoveryApk
    $recoverySha = (Get-FileHash -LiteralPath $recoveryApk -Algorithm SHA256).Hash.ToLowerInvariant()
    $notesPath = Join-Path $recoveryRoot 'notes.md'
    '- Recovery test note' | Set-Content -LiteralPath $notesPath -Encoding utf8NoBOM
    $githubManifestPath = Join-Path $recoveryRoot 'github-latest.json'
    [ordered]@{
        schemaVersion = 1; channel = 'stable'; packageName = 'com.polaris.timetable'; versionCode = 12702
        versionName = '1.27.2'; minSdk = 23; minSupportedVersionCode = 12701
        publishedAt = '2026-09-05T05:10:27Z'; required = $false
        apk = [ordered]@{ fileName = $recoveryApkItem.Name; url = 'https://github.com/Jockjrop/PolarisTimetable/releases/download/v1.27.2/Polaris-1.27.2-release.apk'; size = $recoveryApkItem.Length; sha256 = $recoverySha }
        releaseNotes = @('Recovery test note'); releaseNotesUrl = 'https://github.com/Jockjrop/PolarisTimetable/releases/tag/v1.27.2'
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $githubManifestPath -Encoding utf8NoBOM
    $existingApkUrl = 'https://gitee.com/Jockjrop/polaris-course-schedule/releases/download/v1.27.2/Polaris-1.27.2-release.apk'
    $mockState = [pscustomobject]@{
        Calls = [Collections.Generic.List[object]]::new()
        Assets = [Collections.Generic.List[object]]::new()
    }
    $mockState.Assets.Add([pscustomobject]@{ name = $recoveryApkItem.Name; size = $recoveryApkItem.Length; browser_download_url = $existingApkUrl; source = $recoveryApk })
    $mockApi = {
        param($request)
        $mockState.Calls.Add($request)
        if ($request.Method -eq 'Get' -and $request.Path -like '/tags*') {
            return [pscustomobject]@{ name = 'v1.27.2'; commit = [pscustomobject]@{ sha = $expectedTagCommit } }
        }
        if ($request.Method -eq 'Get' -and $request.Path -match '^/releases\?') {
            return [pscustomobject]@{ id = [long]42; tag_name = 'v1.27.2'; name = 'v1.27.2'; body = '- Recovery test note'; prerelease = $false; target_commitish = $expectedTagCommit }
        }
        if ($request.Method -eq 'Get' -and $request.Path -like '/releases/42/attach_files?*') { return $mockState.Assets.ToArray() }
        if ($request.Method -eq 'Post' -and $request.Path -eq '/releases') { throw '已有 Release 场景不应创建 Release' }
        if ($request.Method -eq 'Post' -and $request.Path -eq '/releases/42/attach_files') {
            $file = $request.Form.file
            $url = "https://gitee.com/Jockjrop/polaris-course-schedule/releases/download/v1.27.2/$($file.Name)"
            $asset = [pscustomobject]@{ name = $file.Name; size = $file.Length; browser_download_url = $url; source = $file.FullName }
            $mockState.Assets.Add($asset)
            return $asset
        }
        throw "未预期的模拟 API 调用：$($request.Method) $($request.Path)"
    }.GetNewClosure()
    $mockDownload = { param($url, $destination)
        $asset = @($mockState.Assets | Where-Object { $_.browser_download_url -eq $url })
        if ($asset.Count -ne 1) { throw "模拟下载资产不唯一：$url" }
        Copy-Item -LiteralPath $asset[0].source -Destination $destination
    }.GetNewClosure()
    $oldToken = $env:GITEE_TOKEN
    $oldOutput = $env:GITHUB_OUTPUT
    try {
        $env:GITEE_TOKEN = 'test-token-not-logged'
        $env:GITHUB_OUTPUT = Join-Path $recoveryRoot 'github-output.txt'
        try {
            & (Join-Path $PSScriptRoot 'publish-gitee-release.ps1') -Owner Jockjrop -Repo polaris-course-schedule `
                -Tag v1.27.2 -ExpectedCommitSha $expectedTagCommit -ApkPath $recoveryApk `
                -GitHubManifestPath $githubManifestPath -ReleaseNotesPath $notesPath -OutputPath $recoveryOutput `
                -ApiRequest $mockApi -DownloadAsset $mockDownload
        } catch { throw "$($_.ScriptStackTrace): $($_.Exception.Message)" }
        if ($LASTEXITCODE -ne 0) { throw '已有 Release recovery 模拟失败' }
        & (Join-Path $PSScriptRoot 'publish-gitee-release.ps1') -Owner Jockjrop -Repo polaris-course-schedule `
            -Tag v1.27.2 -ExpectedCommitSha $expectedTagCommit -ApkPath $recoveryApk `
            -GitHubManifestPath $githubManifestPath -ReleaseNotesPath $notesPath -OutputPath $recoveryOutput `
            -ApiRequest $mockApi -DownloadAsset $mockDownload
        if ($LASTEXITCODE -ne 0) { throw '已有 latest.json/.sha256 recovery 幂等模拟失败' }
    } finally { $env:GITEE_TOKEN = $oldToken; $env:GITHUB_OUTPUT = $oldOutput }
    if (@($mockState.Calls | Where-Object { $_.Method -eq 'Patch' -or $_.Method -eq 'Delete' }).Count -ne 0 -or
            @($mockState.Calls | Where-Object { $_.Method -eq 'Post' -and $_.Path -eq '/releases' }).Count -ne 0) {
        throw '已有 Release recovery 不得 PATCH、DELETE 或重建 Release'
    }
    $uploads = @($mockState.Calls | Where-Object { $_.Method -eq 'Post' -and $_.Path -eq '/releases/42/attach_files' } | ForEach-Object { $_.Form.file.Name })
    if (($uploads | Where-Object { $_ -eq $recoveryApkItem.Name }).Count -ne 0 -or
            (@($uploads | Sort-Object) -join ',') -ne "latest.json,$($recoveryApkItem.Name).sha256") {
        throw "已有 Release recovery 未精确补齐 .sha256/latest.json：$($uploads -join ',')"
    }
    $outputValues = Get-Content -LiteralPath (Join-Path $recoveryRoot 'github-output.txt')
    if ($outputValues -notcontains 'manifest_url=https://gitee.com/Jockjrop/polaris-course-schedule/releases/download/v1.27.2/latest.json' -or
            $outputValues -notcontains "apk_url=$existingApkUrl") {
        throw '已有 Release recovery 未输出正确公开 URL'
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
            ([regex]::Matches($publisher, "'/tags\?per_page=100&page=1'")).Count -ne 1 -or
            $publisher -match "Invoke-GiteeJson 'Patch'|Method = 'Patch'|Method 'Patch'") {
        throw 'Gitee 发布路径未彻底收敛为单次 tags API 调用'
    }
    Write-Host 'release safety tests: 41 passed'
} finally {
    Remove-Item -LiteralPath $root -Recurse -Force
}
