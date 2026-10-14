Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$destDocx = "c:\Projects\teleprompter-web\sample_script.docx"
if (Test-Path $destDocx) { Remove-Item $destDocx -Force }

$zipStream = [System.IO.File]::Open($destDocx, [System.IO.FileMode]::CreateNew)
$archive = New-Object System.IO.Compression.ZipArchive($zipStream, [System.IO.Compression.ZipArchiveMode]::Create)

function Add-ZipEntry($zipArchive, $entryPath, $contentString) {
    $entry = $zipArchive.CreateEntry($entryPath)
    $stream = $entry.Open()
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($contentString)
    $stream.Write($bytes, 0, $bytes.Length)
    $stream.Close()
}

$contentTypes = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>'
Add-ZipEntry $archive "[Content_Types].xml" $contentTypes

$rels = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>'
Add-ZipEntry $archive "_rels/.rels" $rels

$p1 = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String("0KLQtdGB0YLQvtCy0YvQuSDRgdGG0LXQvdCw0YDQuNC5INC00LvRjyDRgtC10LvQtdGB0YPRhNC70LXRgNCw"))
$p2 = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String("0JfQtNGA0LDQstGB0YLQstGD0LnRgtC1LCDRg9Cy0LDQttCw0LXQvNGL0LUg0LfRgNC40YLQtdC70LghINCt0YLQvtGCINGC0LXQutGB0YIg0LHRi9C7INGD0YHQv9C10YjQvdC+INC40LzQv9C+0YDRgtC40YDQvtCy0LDQvSDQuNC3INGE0LDQudC70LAgRE9DWCAoTWljcm9zb2Z0IFdvcmQpLg=="))
$p3 = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String("0JHQtdCz0YPRidCw0Y8g0YHRgtGA0L7QutCwINGA0LDRgdC/0L7Qu9C+0LbQtdC90LAg0LIg0YHQsNC80L7QvSDQstC10YDRhdGDINGN0LrRgNCw0L3QsCwg0L/QvtGN0YLQvtC80YMg0LLRi9GB0YLRg9C/0LDRjtGJ0LjQuSDRgdC80L7RgtGA0LjRgiDRgtC+0YfQvdC+INCyINC+0LHRitC10LrRgtC40LIg0LrQsNC80LXRgNGLIQ=="))
$p4 = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String("0JLQu9Cw0LTQtdC70LXRhg=="))

$docXml = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>' +
  "<w:p><w:r><w:t>$p1</w:t></w:r></w:p>" +
  "<w:p><w:r><w:t>$p2</w:t></w:r></w:p>" +
  "<w:p><w:r><w:t>$p3</w:t></w:r></w:p>" +
  '</w:body></w:document>'

Add-ZipEntry $archive "word/document.xml" $docXml

$archive.Dispose()
$zipStream.Close()
Write-Output "CANONICAL DOCX CREATED: $destDocx"
