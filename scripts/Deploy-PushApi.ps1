param(
    [Parameter(Mandatory=$true)][string]$ResourceGroup,
    [Parameter(Mandatory=$true)][string]$AppName,
    [Parameter(Mandatory=$true)][string]$ServiceAccountFile
)
$ErrorActionPreference = 'Stop'
if (-not (Test-Path 'Winx.API/Winx.API.csproj')) { throw 'Run from the Winx project root.' }
if (-not (Get-Command az -ErrorAction SilentlyContinue)) { throw 'Install Azure CLI, then run az login first, or deploy using your existing Azure method.' }
if (-not (Test-Path $ServiceAccountFile)) { throw 'Service account file was not found.' }
$accountJson = (Get-Content -LiteralPath $ServiceAccountFile -Raw | ConvertFrom-Json) | ConvertTo-Json -Compress -Depth 10
$account = $accountJson | ConvertFrom-Json
$config = Get-Content 'app/google-services.json' -Raw | ConvertFrom-Json
if ($account.type -ne 'service_account' -or $account.project_id -ne $config.project_info.project_id) { throw 'Use a Firebase service account for the SAME project as the Android app.' }
$tempRoot = Join-Path $env:TEMP ('WinxPushDeploy-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force $tempRoot | Out-Null
try {
    $publish = Join-Path $tempRoot 'publish'
    dotnet publish Winx.API/Winx.API.csproj -c Release -r linux-x64 --self-contained false -p:UseAppHost=false -o $publish
    if ($LASTEXITCODE -ne 0) { throw 'Publish failed. Nothing has been deployed.' }
    $archive = Join-Path $tempRoot 'api.zip'
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem

    $zip = [IO.Compression.ZipFile]::Open(
        $archive,
        [IO.Compression.ZipArchiveMode]::Create
    )

    try {
        foreach ($file in Get-ChildItem -LiteralPath $publish -Recurse -File) {
            $relativePath = $file.FullName.Substring($publish.Length)
            $relativePath = $relativePath.TrimStart([char[]]'\/').Replace('\', '/')

            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
                $zip,
                $file.FullName,
                $relativePath,
                [IO.Compression.CompressionLevel]::Optimal
            ) | Out-Null
        }
    }
    finally {
        $zip.Dispose()
    }

    $checkZip = [IO.Compression.ZipFile]::OpenRead($archive)

    try {
        foreach ($entry in $checkZip.Entries) {
            if ($entry.FullName.Contains('\')) {
                throw 'ZIP contains an invalid Windows path.'
            }
        }

        if (-not $checkZip.GetEntry('Winx.API.dll')) {
            throw 'Winx.API.dll is missing from the ZIP root.'
        }
    }
    finally {
        $checkZip.Dispose()
    }
    $settingsFile = Join-Path $tempRoot 'private-settings.json'
    $settings = @{
        'Firebase__ProjectId' = $account.project_id
        'Firebase__ServiceAccountJson' = $accountJson
        'Push__Enabled' = 'true'
        'Database__MigrateOnStartup' = 'true'
    } | ConvertTo-Json -Depth 5
    [IO.File]::WriteAllText($settingsFile, $settings, (New-Object Text.UTF8Encoding($false)))
    # Output is suppressed because app settings contain a server private key.
    az webapp config appsettings set --resource-group $ResourceGroup --name $AppName --settings "@$settingsFile" --output none
    if ($LASTEXITCODE -ne 0) { throw 'Could not configure Azure settings.' }
    az webapp deploy --resource-group $ResourceGroup --name $AppName --src-path $archive --type zip --output none
    if ($LASTEXITCODE -ne 0) { throw 'Azure deployment failed. Check Azure Deployment Center.' }
    Write-Host 'Deployment submitted. Check Azure Log stream for successful migration and app startup.'
    Write-Host 'After verifying the migration, set Database__MigrateOnStartup=false in Azure Environment variables.'
    Write-Host 'FCM sending is configured. Confirm phone registration and test actual foreground/background delivery.'
} finally {
    Remove-Item -Recurse -Force $tempRoot -ErrorAction SilentlyContinue
    Remove-Variable accountJson, account, settings -ErrorAction SilentlyContinue
}
