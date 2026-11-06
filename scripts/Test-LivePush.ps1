param(
    [ValidateSet('Create','Update','Delete','Restore')][string]$Action = 'Create',
    [int]$EntryId = 0,
    [string]$ApiBase = 'https://winx-api-part3-d9b31478.azurewebsites.net'
)
$ErrorActionPreference = 'Stop'
if (-not (Test-Path 'app/google-services.json')) { throw 'Run from the Winx project root.' }
if ($Action -ne 'Create' -and $EntryId -le 0) { throw 'Provide -EntryId for Update, Delete or Restore.' }
$config = Get-Content 'app/google-services.json' -Raw | ConvertFrom-Json
$key = $config.client[0].api_key[0].current_key
$email = Read-Host 'Email of the test account signed in on the phone'
$password = Read-Host 'Password of that email account' -AsSecureString
$credential = New-Object System.Management.Automation.PSCredential($email, $password)
$loginBody = @{ email = $email; password = $credential.GetNetworkCredential().Password; returnSecureToken = $true } | ConvertTo-Json
try {
    $login = Invoke-RestMethod -Method Post -ContentType 'application/json' -Uri "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$key" -Body $loginBody
    $headers = @{ Authorization = "Bearer $($login.idToken)" }
    $api = $ApiBase.TrimEnd('/')
    switch ($Action) {
        'Create' {
            $body = @{ title = "POE push demo $(Get-Date -Format 'HHmmss')"; location = 'Durban'; country = 'South Africa'; date = (Get-Date).ToString('yyyy-MM-ddTHH:mm:ss'); rating = 4; notes = 'Created through the authenticated API to test a real background push.' } | ConvertTo-Json
            $entry = Invoke-RestMethod -Method Post -Uri "$api/api/Entries" -Headers $headers -ContentType 'application/json' -Body $body
            $EntryId = $entry.id
            Write-Host "Created entry $EntryId. Check the physical phone for Memory saved."
        }
        'Update' {
            $entry = Invoke-RestMethod -Uri "$api/api/Entries/$EntryId" -Headers $headers
            $entry.notes = "Updated by the live push test at $(Get-Date -Format 'HH:mm:ss')"
            $body = $entry | ConvertTo-Json -Depth 5
            Invoke-RestMethod -Method Put -Uri "$api/api/Entries/$EntryId" -Headers $headers -ContentType 'application/json' -Body $body | Out-Null
            Write-Host 'Updated entry. Check the phone for Memory updated.'
        }
        'Delete' {
            Invoke-RestMethod -Method Delete -Uri "$api/api/Entries/$EntryId" -Headers $headers | Out-Null
            Write-Host 'Moved entry to Recently Deleted. Check the phone.'
        }
        'Restore' {
            Invoke-RestMethod -Method Post -Uri "$api/api/Entries/$EntryId/restore" -Headers $headers -ContentType 'application/json' -Body '{}' | Out-Null
            Write-Host 'Restored entry. Check the phone for Memory restored.'
        }
    }
    Start-Sleep -Seconds 2
    Invoke-RestMethod -Uri "$api/api/Notifications" -Headers $headers |
        Select-Object -First 5 id, title, body, kind, read | Format-Table -Wrap
    Write-Host "Entry ID for the next command: $EntryId"
    Write-Host 'An inbox row proves the event was stored. Also verify the actual phone alert and FCM acceptance log.'
} finally {
    Remove-Variable password, credential, loginBody, login, headers -ErrorAction SilentlyContinue
}
