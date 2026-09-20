param([Parameter(Mandatory)][string]$OutputPath)
$ErrorActionPreference = 'Stop'
# Only writes a local secret file. It never changes Azure or the database.
$roles = [ordered]@{
    'personal.obras'='PERSONAL_OBRAS'
    'responsable'='RESPONSABLE_AUTORIZADO'
    'jefe.cuadrilla'='JEFE_CUADRILLA'
    'operario'='OPERARIO_CONTRATISTA'
    'inspector'='INSPECTOR_OBRA'
}
$accounts = foreach ($entry in $roles.GetEnumerator()) {
    $bytes = [byte[]]::new(24)
    [Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    $password = [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+','-').Replace('/','_')
    @{username=$entry.Key;password=$password;role=$entry.Value}
}
$path = [IO.Path]::GetFullPath($OutputPath)
# CreateNew refuses to overwrite existing credentials by accident.
$stream = [IO.File]::Open($path,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
try {
    $bytes = [Text.Encoding]::UTF8.GetBytes((ConvertTo-Json -InputObject @($accounts) -Depth 4))
    $stream.Write($bytes,0,$bytes.Length)
} finally {
    $stream.Dispose()
    $accounts = $null
    $password = $null
}
Write-Output "Cuentas de demo creadas en archivo local privado: $path. No subirlo a Git ni compartirlo por canales publicos."
