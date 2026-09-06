# Generate Polaris launcher legacy icons by directly scaling the source PNG.
# The source already has transparent corners (rounded square artwork),
# so a plain high-quality resize is the complete icon.
# Usage: powershell -NoProfile -File make_icons.ps1 -Source <png> -ResRoot <app/src/main/res>
param(
    [Parameter(Mandatory = $true)][string]$Source,
    [Parameter(Mandatory = $true)][string]$ResRoot
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$src = [System.Drawing.Bitmap]::new($Source)
try {
    $sizes = @{ mdpi = 48; hdpi = 72; xhdpi = 96; xxhdpi = 144; xxxhdpi = 192 }
    foreach ($k in $sizes.Keys) {
        $size = $sizes[$k]
        $dst = New-Object System.Drawing.Bitmap($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $g = [System.Drawing.Graphics]::FromImage($dst)
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
        $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
        $g.DrawImage($src, 0, 0, $size, $size)
        $g.Dispose()
        $path = Join-Path $ResRoot "mipmap-$k\ic_launcher.png"
        $dst.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
        $dst.Dispose()
        "mipmap-$k -> $size px"
    }
}
finally {
    $src.Dispose()
}
"done"
