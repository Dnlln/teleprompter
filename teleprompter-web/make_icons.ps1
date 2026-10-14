Add-Type -AssemblyName System.Drawing

function Create-Icon($size, $path) {
    $bmp = New-Object System.Drawing.Bitmap $size, $size
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $rect = New-Object System.Drawing.Rectangle 0, 0, $size, $size
    $brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush $rect, ([System.Drawing.Color]::FromArgb(99, 102, 241)), ([System.Drawing.Color]::FromArgb(15, 23, 42)), 45.0
    $g.FillRectangle($brush, $rect)
    
    $pen = New-Object System.Drawing.Pen ([System.Drawing.Color]::White)
    $pen.Width = [float]($size / 18)
    $camSize = [int]($size * 0.42)
    $camX = [int](($size - $camSize) / 2)
    $camY = [int](($size - $camSize) / 2)
    $g.DrawEllipse($pen, $camX, $camY, $camSize, $camSize)
    
    $redBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(239, 68, 68))
    $dotSize = [int]($camSize * 0.45)
    $dotX = [int](($size - $dotSize) / 2)
    $dotY = [int](($size - $dotSize) / 2)
    $g.FillEllipse($redBrush, $dotX, $dotY, $dotSize, $dotSize)
    
    $g.Dispose()
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

Create-Icon 192 "c:\Projects\teleprompter-web\icon-192.png"
Create-Icon 512 "c:\Projects\teleprompter-web\icon-512.png"
Write-Output "ICONS_GENERATED_OK"
