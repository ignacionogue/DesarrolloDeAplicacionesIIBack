param([switch]$VerifyOnly)
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080'
$results = [System.Collections.Generic.List[object]]::new()
function Api($method, $path, $body = $null, $expected = 200) {
    $args = @{ Uri = "$base$path"; Method = $method; SkipHttpErrorCheck = $true; TimeoutSec = 20 }
    if ($null -ne $body) { $args.ContentType = 'application/json'; $args.Body = if ($body -is [string]) { $body } else { ConvertTo-Json -InputObject $body -Depth 10 -Compress } }
    $r = Invoke-WebRequest @args
    $data = try { $r.Content | ConvertFrom-Json } catch { $null }
    $ok = [int]$r.StatusCode -eq $expected
    $results.Add(@{ test="$method $path"; expected=$expected; actual=[int]$r.StatusCode; passed=$ok })
    if ($expected -ge 400) { Check "Formato error $method $path" ($null -ne $data.message -and $null -ne $data.code -and $null -ne $data.details) }
    if (-not $ok) { Write-Host "FAIL $method $path expected=$expected actual=$($r.StatusCode): $($r.Content)" }
    return $data
}
function Check($name, $condition) { $results.Add(@{test=$name;passed=[bool]$condition}); if (-not $condition) { Write-Host "FAIL $name" } }
$prefix = '/api/public-works'
if (-not $VerifyOnly) {
    foreach ($name in @('Demo Norte','Demo Centro','Demo Sur')) {
        $existing = @(Api GET "$prefix/crews")
        if ($name -notin $existing.nombre) { $null = Api POST "$prefix/crews" @{nombre=$name} 201 }
    }
    $projects = @()
    foreach ($name in @('Repavimentacion Av. Lima','Plaza del Centro','Desagues Barrio Sur','Veredas Escuela 12')) {
        $body = @{name="DEMO - $name";description='Datos ficticios para integracion del frontend';scope='Mejora de infraestructura municipal';location='Av. Lima 700';estimatedBudget=1000000;approvedBudget=800000;usedBudget=200000;physicalProgress=25;estimatedStartDate='2026-09-15';estimatedDurationDays=30;approvedDeadlineDays=28;technicalManager='Ing. Demo';contractor='Constructora Demo'}
        $p = Api POST "$prefix/projects" $body 201
        Check 'POST conserva avances enviados' ($p.physicalProgress -eq 25 -and $p.budgetProgress -eq 25)
        $projects += $p.id
        $body.description += ' - revisado'
        $null = Api PUT "$prefix/projects/$($p.id)" $body
    }
    $null = Api PATCH "$prefix/projects/$($projects[0])/approve" $null 409
    foreach ($id in $projects[1..3]) { $null = Api PATCH "$prefix/projects/$id/submit-approval" }
    $approved = Api PATCH "$prefix/projects/$($projects[2])/approve"
    Check 'Aprobado expone SIN_INICIAR (contrato actual)' ($approved.status -eq 'SIN_INICIAR')
    $null = Api PATCH "$prefix/projects/$($projects[3])/reject"
    $null = Api PATCH "$prefix/projects/$($projects[2])/approve" $null 409
    $states = @('PENDIENTE','PROGRAMADA','ASIGNADA','EN_EJECUCION','PAUSADA','COMPLETADA','VALIDADA','REABIERTA')
    $orders = @()
    for ($i=0; $i -lt $states.Count; $i++) {
        $state = $states[$i]
        $body = @{sourceRequestId="demo-ticket-$i";origin=@('MANUAL','ATENCION_CIUDADANA','INSPECCION')[$i % 3];description="DEMO - Reparacion $state";interventionType='Calzada';location="Av. Lima $(710+$i)";priority=@('BAJA','MEDIA','ALTA')[$i % 3];estimatedDurationHours=6}
        $ot = Api POST "$prefix/work-orders" $body 201
        $id = $ot.id; $orders += $id
        $body.description += ' - revisada'
        $null = Api PUT "$prefix/work-orders/$id" $body
        if ($i -ge 1) {
            $schedule = @{scheduledDate='2026-09-16'}
            if ($i -ge 2) { $schedule.crew = @('Demo Norte','Demo Centro','Demo Sur')[$i % 3] }
            $null = Api PATCH "$prefix/work-orders/$id/schedule" $schedule
        }
        if ($i -ge 3) { $null = Api PATCH "$prefix/work-orders/$id/start" }
        if ($i -eq 4) { $null = Api PATCH "$prefix/work-orders/$id/pause" }
        if ($i -ge 5) { $null = Api PATCH "$prefix/work-orders/$id/complete" @{outcome='Reparacion realizada con materiales de prueba'} }
        if ($i -ge 6) { $null = Api PATCH "$prefix/work-orders/$id/validate" @{approved=($i -eq 6);observations='Inspeccion de prueba'} }
        $saved = Api GET "$prefix/work-orders/$id"
        Check "OT persiste $state" ($saved.status -eq $state)
    }
    $null = Api PATCH "$prefix/work-orders/$($orders[0])/start" $null 409
    $null = Api PATCH "$prefix/work-orders/$($orders[6])/pause" $null 409
    $null = Api PATCH "$prefix/work-orders/$($orders[7])/start"
    $null = Api PATCH "$prefix/work-orders/$($orders[7])/pause"
    $null = Api PATCH "$prefix/work-orders/$($orders[7])/start"
    $null = Api PATCH "$prefix/work-orders/$($orders[7])/complete"
    $null = Api PATCH "$prefix/work-orders/$($orders[7])/validate" @{approved=$false}
}
$null = Api GET '/api/health'
$null = Api GET '/v3/api-docs'
$null = Api GET '/swagger-ui/index.html'
$p = Api GET "$prefix/projects?search=DEMO&size=2&page=0"
Check 'Paginacion proyectos' ($p.content.Count -eq 2 -and $p.totalElements -ge 4 -and $p.size -eq 2)
$p2 = Api GET "$prefix/projects?search=DEMO&size=2&page=1"
Check 'Paginas diferentes' ($p.content[0].id -notin $p2.content.id)
$p = Api GET "$prefix/projects?search=DEMO&status=RECHAZADO"
Check 'Filtro proyectos' ($p.totalElements -ge 1 -and @($p.content | Where-Object status -ne 'RECHAZADO').Count -eq 0)
foreach ($state in @('PENDIENTE','PROGRAMADA','ASIGNADA','EN_EJECUCION','PAUSADA','COMPLETADA','VALIDADA','REABIERTA')) {
    $o = Api GET "$prefix/work-orders?search=DEMO&status=$state&size=100"
    Check "Filtro OT $state" ($o.totalElements -ge 1 -and @($o.content | Where-Object status -ne $state).Count -eq 0)
}
$o = Api GET "$prefix/work-orders?search=DEMO&priority=ALTA&origin=INSPECCION"
Check 'Filtros combinados OT' ($o.totalElements -ge 1 -and @($o.content | Where-Object { $_.priority -ne 'ALTA' -or $_.origin -ne 'INSPECCION' }).Count -eq 0)
$resources = Api GET "$prefix/resources"
Check 'Catalogos demo presentes' ($resources.crews.Count -ge 3 -and $resources.materials.Count -ge 3 -and $resources.machinery.Count -ge 2)
$cors = Invoke-WebRequest -Uri "$base$prefix/projects" -Method Options -Headers @{Origin='http://localhost:5173';'Access-Control-Request-Method'='POST';'Access-Control-Request-Headers'='content-type'} -SkipHttpErrorCheck
Check 'CORS preflight frontend local' ($cors.StatusCode -eq 200 -and $cors.Headers['Access-Control-Allow-Origin'] -contains 'http://localhost:5173')
$null = Api GET "$prefix/projects/999999999" $null 404
$null = Api POST "$prefix/projects" @{} 400
$null = Api POST "$prefix/work-orders" @{origin='INVALIDO';priority='ALTA';description='Error esperado'} 400
$null = Api POST "$prefix/work-orders" @{origin='MANUAL';priority='ALTA';description='Error esperado';crew='NO_EXISTE'} 404
$null = Api POST "$prefix/projects" '{"name":' 400
$null = Api GET "$prefix/projects/no-es-id" $null 400
$fallback = Api GET "$prefix/projects?page=abc"
Check 'Paginacion invalida usa pagina cero (Spring Pageable)' ($fallback.page -eq 0)
$results | ConvertTo-Json -Depth 10 | Set-Content -Encoding utf8 "$PSScriptRoot/verification-results.json"
$failed = @($results | Where-Object { -not $_.passed }).Count
Write-Host "RESULT: $($results.Count) checks, $failed failures"
if ($failed -gt 0) { exit 1 }
