Add-Type -AssemblyName System.IO.Compression.FileSystem

$docx = "c:\Projects\teleprompter-web\sample_script.docx"
$zip = [System.IO.Compression.ZipFile]::OpenRead($docx)
foreach ($e in $zip.Entries) {
    Write-Output "Entry: $($e.FullName)"
}
$docEntry = $zip.Entries | Where-Object { $_.FullName -replace '\\', '/' -eq "word/document.xml" } | Select-Object -First 1

if ($null -ne $docEntry) {
    $stream = $docEntry.Open()
    $reader = New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::UTF8)
    $xmlContent = $reader.ReadToEnd()
    $reader.Close()
    $stream.Close()
    
    [xml]$xmlDoc = $xmlContent
    $ns = New-Object System.Xml.XmlNamespaceManager($xmlDoc.NameTable)
    $ns.AddNamespace("w", "http://schemas.openxmlformats.org/wordprocessingml/2006/main")
    $nodes = $xmlDoc.SelectNodes("//w:p", $ns)
    
    Write-Output "VERIFICATION SUCCESSFUL: Found $($nodes.Count) paragraphs in sample_script.docx"
    foreach ($p in $nodes) {
        $tNodes = $p.SelectNodes(".//w:t", $ns)
        $text = ($tNodes | ForEach-Object { $_.InnerText }) -join ""
        Write-Output " - $text"
    }
} else {
    Write-Error "word/document.xml not found!"
}

$zip.Dispose()
