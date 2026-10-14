param([int]$Port = 8088)

$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add("http://localhost:$Port/")
$listener.Prefixes.Add("http://127.0.0.1:$Port/")
$listener.Start()
Write-Output "Server running on http://localhost:$Port/"

$root = $PSScriptRoot

$mimeTypes = @{
    ".html" = "text/html; charset=utf-8"
    ".css"  = "text/css; charset=utf-8"
    ".js"   = "application/javascript; charset=utf-8"
    ".json" = "application/json; charset=utf-8"
    ".png"  = "image/png"
    ".docx" = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    ".ico"  = "image/x-icon"
    ".apk"  = "application/vnd.android.package-archive"
}

try {
    while ($listener.IsListening) {
        $context = $listener.GetContext()
        try {
            $req = $context.Request
            $resp = $context.Response

            $rawUrl = $req.Url.LocalPath
            if ($rawUrl -eq "/" -or $rawUrl -eq "") {
                $rawUrl = "/index.html"
            }

            $localPath = Join-Path $root ($rawUrl.TrimStart('/'))
            if (Test-Path $localPath -PathType Leaf) {
                $ext = [System.IO.Path]::GetExtension($localPath).ToLower()
                $mime = if ($mimeTypes.ContainsKey($ext)) { $mimeTypes[$ext] } else { "application/octet-stream" }
                $resp.ContentType = $mime
                $bytes = [System.IO.File]::ReadAllBytes($localPath)
                $resp.ContentLength64 = $bytes.Length

                if ($req.HttpMethod -ne "HEAD") {
                    $resp.OutputStream.Write($bytes, 0, $bytes.Length)
                }
            } else {
                $resp.StatusCode = 404
                $msg = [System.Text.Encoding]::UTF8.GetBytes("Not Found: $rawUrl")
                $resp.ContentLength64 = $msg.Length
                if ($req.HttpMethod -ne "HEAD") {
                    $resp.OutputStream.Write($msg, 0, $msg.Length)
                }
            }
        } catch {
            # Log and continue loop without crashing server
            Write-Warning $_.Exception.Message
        } finally {
            try { $context.Response.OutputStream.Close() } catch {}
        }
    }
} finally {
    $listener.Stop()
}
