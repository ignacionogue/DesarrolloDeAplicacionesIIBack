# Demo scripts receive existing tokens; they never create users or sign JWTs.
function Assert-ApiTokens([hashtable]$Tokens, [string[]]$Roles) {
    foreach ($role in $Roles) {
        if ([string]::IsNullOrWhiteSpace($Tokens[$role])) {
            throw "Falta el token del rol $role. Pasar -Tokens con JWT validos del entorno; no guardarlos en Git."
        }
    }
}

function Get-ApiToken([hashtable]$Tokens, [string]$Method, [string]$Path) {
    $role = 'PERSONAL_OBRAS'
    if ($Method -eq 'PATCH') {
        if ($Path -match '/projects/[^/]+/(approve|reject)$') { $role = 'RESPONSABLE_AUTORIZADO' }
        elseif ($Path -match '/work-orders/[^/]+/(schedule|start|pause)$') { $role = 'JEFE_CUADRILLA' }
        elseif ($Path -match '/work-orders/[^/]+/complete$') { $role = 'OPERARIO_CONTRATISTA' }
        elseif ($Path -match '/work-orders/[^/]+/validate$') { $role = 'INSPECTOR_OBRA' }
    }
    Assert-ApiTokens $Tokens @($role)
    return $Tokens[$role]
}
