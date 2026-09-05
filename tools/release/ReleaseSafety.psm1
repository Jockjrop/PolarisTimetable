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

function Resolve-GiteeApiTagCommit {
    param(
        [Parameter(Mandatory = $true)][string]$Tag,
        [AllowEmptyCollection()][object[]]$Tags
    )

    $tagEntries = @($Tags | ForEach-Object { $_ | Write-Output })
    $matchingTags = @($tagEntries | Where-Object { $_.name -ceq $Tag })
    if ($matchingTags.Count -eq 0) { throw "Gitee API 中不存在 tag：$Tag" }
    if ($matchingTags.Count -ne 1) { throw "Gitee API 中 tag 不唯一：$Tag（$($matchingTags.Count) 项）" }
    $tagEntry = $matchingTags[0]
    $commit = $tagEntry.commit.sha
    if ($commit -isnot [string]) { throw 'Resolved Gitee API commit 必须是单个 System.String' }
    if ($commit.Trim() -notmatch '^[0-9a-fA-F]{40}$') {
        throw "Resolved Gitee API commit 不是完整 40 位 SHA：$($commit.Trim())"
    }
    return $commit.Trim().ToLowerInvariant()
}

function Get-GiteeApiErrorInfo([Management.Automation.ErrorRecord]$ErrorRecord) {
    $statusCode = $null
    $retryAfter = $null
    $responseProperty = $ErrorRecord.Exception.PSObject.Properties['Response']
    $response = if ($null -eq $responseProperty) { $null } else { $responseProperty.Value }
    if ($ErrorRecord.Exception.Data.Contains('StatusCode')) {
        $statusCode = [int]$ErrorRecord.Exception.Data['StatusCode']
    } elseif ($null -ne $response -and $null -ne $response.StatusCode) {
        $statusCode = [int]$response.StatusCode
    }
    if ($ErrorRecord.Exception.Data.Contains('RetryAfter')) {
        $retryAfter = [int]$ErrorRecord.Exception.Data['RetryAfter']
    } elseif ($null -ne $response -and $null -ne $response.Headers -and
            $null -ne $response.Headers.RetryAfter) {
        $header = $response.Headers.RetryAfter
        if ($null -ne $header.Delta) {
            $retryAfter = [int][Math]::Ceiling($header.Delta.TotalSeconds)
        } elseif ($null -ne $header.Date) {
            $retryAfter = [int][Math]::Ceiling(($header.Date - [DateTimeOffset]::UtcNow).TotalSeconds)
        }
    }
    return [pscustomobject]@{
        StatusCode = $statusCode
        RetryAfter = if ($retryAfter -gt 0) { $retryAfter } else { $null }
        Message = $ErrorRecord.Exception.Message
    }
}

function Invoke-GiteeApiRequest {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Uri,
        [hashtable]$Headers,
        [hashtable]$Body,
        [hashtable]$Form,
        [ValidateRange(1, 10)][int]$MaxAttempts = 5,
        [bool]$RetryServerErrors = $true,
        [scriptblock]$Request,
        [scriptblock]$Delay
    )

    if ($null -ne $Body -and $null -ne $Form) { throw 'Gitee API 请求不能同时使用 Body 和 Form' }
    if ($null -eq $Delay) { $Delay = { param($Seconds) Start-Sleep -Seconds $Seconds } }
    $backoff = @(2, 5, 10, 20)
    for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
        try {
            if ($null -ne $Request) {
                return & $Request ([pscustomobject]@{
                    Method = $Method; Uri = $Uri; Headers = $Headers; Body = $Body; Form = $Form
                })
            }
            $parameters = @{
                Uri = $Uri; Method = $Method; Headers = $Headers; ErrorAction = 'Stop'
            }
            if ($null -ne $Body) { $parameters.Body = $Body }
            if ($null -ne $Form) { $parameters.Form = $Form }
            return Invoke-RestMethod @parameters
        } catch {
            $info = Get-GiteeApiErrorInfo $_
            if ($info.StatusCode -eq 401) { throw "Gitee API 认证失败（HTTP 401）：$Method $Uri" }
            if ($info.StatusCode -eq 403) { throw "Gitee API 权限不足（HTTP 403）：$Method $Uri" }
            if ($info.StatusCode -eq 404) { throw "Gitee API 资源不存在（HTTP 404）：$Method $Uri" }
            $retryable = $info.StatusCode -eq 429 -or
                ($RetryServerErrors -and $info.StatusCode -ge 500 -and $info.StatusCode -le 599)
            if ($retryable -and $attempt -lt $MaxAttempts) {
                $delaySeconds = if ($info.StatusCode -eq 429 -and $null -ne $info.RetryAfter) {
                    $info.RetryAfter
                } else {
                    $backoff[[Math]::Min($attempt - 1, $backoff.Count - 1)]
                }
                & $Delay $delaySeconds
                continue
            }
            if ($info.StatusCode -eq 429) {
                throw "Gitee API 限流重试已达上限（HTTP 429，$attempt 次）：$Method $Uri"
            }
            if ($info.StatusCode -ge 500 -and $info.StatusCode -le 599) {
                throw "Gitee API 服务错误（HTTP $($info.StatusCode)，$attempt 次）：$Method $Uri"
            }
            $message = $info.Message
            if ($message -match '(?i)could not resolve|name or service not known') {
                throw "Gitee API DNS 解析失败：$Method $Uri"
            }
            if ($message -match '(?i)SSL|TLS|certificate') {
                throw "Gitee API TLS 连接失败：$Method $Uri"
            }
            if ($message -match '(?i)connect|connection|timed out|timeout') {
                throw "Gitee API 连接失败：$Method $Uri"
            }
            throw "Gitee API 请求失败：$Method $Uri"
        }
    }
}

function Assert-TagCommitMatch([string]$GitHubCommit, [string]$GiteeCommit) {
    $expected = if ($null -eq $GitHubCommit) { '' } else { $GitHubCommit.Trim() }
    $resolved = if ($null -eq $GiteeCommit) { '' } else { $GiteeCommit.Trim() }
    if ($expected -notmatch '^[0-9a-fA-F]{40}$') {
        throw "Expected GitHub commit 不是完整 40 位 SHA：$expected"
    }
    if ($resolved -notmatch '^[0-9a-fA-F]{40}$') {
        throw "Resolved Gitee API commit 不是完整 40 位 SHA：$resolved"
    }
    $expected = $expected.ToLowerInvariant()
    $resolved = $resolved.ToLowerInvariant()
    if ($expected -ne $resolved) {
        throw "Gitee tag 与 GitHub tag commit 不一致`nExpected GitHub commit: $expected`nResolved Gitee API commit: $resolved"
    }
}

Export-ModuleMember -Function Assert-ReleaseVersionGate, Assert-MatchingAsset, `
    Get-GiteeReleaseAction, Resolve-GiteeApiTagCommit, Invoke-GiteeApiRequest, `
    Assert-TagCommitMatch
