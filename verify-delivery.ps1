$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080/api/public-works'
$checks = [System.Collections.Generic.List[object]]::new()
function Check($name, $ok) { $checks.Add(@{test=$name;passed=[bool]$ok}); if (-not $ok) { throw "FAIL: $name" } }
function Request($method, $path, $body, $status) {
    $args = @{Uri="$base$path";Method=$method;SkipHttpErrorCheck=$true;TimeoutSec=20}
    if ($null -ne $body) { $args.ContentType='application/json'; $args.Body=ConvertTo-Json -InputObject $body -Depth 8 -Compress }
    $r=Invoke-WebRequest @args
    Check "$method $path -> $status" ($r.StatusCode -eq $status)
    return $r.Content | ConvertFrom-Json
}
$projects = Request GET '/projects?size=2000' $null 200
$orders = Request GET '/work-orders?size=2000' $null 200
$summary = Request GET '/dashboard/summary' $null 200
Check 'Total proyectos real' ($summary.totalProjects -eq $projects.totalElements)
$open = @($orders.content | Where-Object { $_.status -notin @('COMPLETADA','VALIDADA') })
Check 'Ordenes abiertas reales' ($summary.openWorkOrders -eq $open.Count)
Check 'Ordenes externas reales' ($summary.externalWorkOrders -eq @($orders.content | Where-Object origin -ne 'MANUAL').Count)
Check 'Presupuesto estimado real' ($summary.estimatedBudget -eq ($projects.content | Measure-Object estimatedBudget -Sum).Sum)
Check 'Presupuesto aprobado real' ($summary.approvedBudget -eq ($projects.content | Measure-Object approvedBudget -Sum).Sum)
Check 'Avance fisico real' ($summary.averagePhysicalProgress -eq [math]::Round(($projects.content | Measure-Object physicalProgress -Average).Average,0,[MidpointRounding]::AwayFromZero))
foreach ($crew in $summary.crewLoads) { Check "Carga $($crew.name)" ($crew.openWorkOrders -eq @($open | Where-Object crew -eq $crew.name).Count) }
$today = [datetime]::Parse($summary.asOfDate)
Check 'Demoras por fecha' ($summary.delayedWorkOrders -eq @($open | Where-Object { $_.scheduledDate -and [datetime]::Parse($_.scheduledDate) -lt $today }).Count)
$existing = Request GET '/street-closures?size=2000' $null 200
foreach ($order in @($orders.content | Sort-Object id | Select-Object -First 3)) {
    $reason = "DEMO - Corte para OT $($order.id)"
    $body = @{workOrderId=$order.id;location=$order.location;affectedSections=@($order.location, 'Tramo adyacente DEMO');requestedFrom='2026-09-15';requestedTo='2026-09-16';reason=$reason}
    if ($reason -notin $existing.content.reason) {
        $created = Request POST '/street-closures' $body 201
        Check 'Corte pendiente con correlacion' ($created.status -eq 'PENDIENTE' -and $created.closureRequestId -and $created.workOrderId -eq $order.id -and $created.affectedSections.Count -eq 2)
    }
}
$saved = Request GET '/street-closures?size=2&sort=id,asc' $null 200
Check 'Cortes persistidos y paginados' ($saved.totalElements -ge 3 -and $saved.content.Count -eq 2)
$body.requestedTo='2026-09-14'
$errorResult = Request POST '/street-closures' $body 422
Check 'Error fechas consistente' ($errorResult.code -eq 'BUSINESS_RULE_VIOLATION' -and $null -ne $errorResult.details)
$body.requestedTo='2026-09-16'; $body.workOrderId=999999999
$null = Request POST '/street-closures' $body 404
$null = Request POST '/street-closures' @{} 400
$before = (Request GET '/street-closures' $null 200).totalElements
Check 'Entradas invalidas no persisten' ($before -eq $saved.totalElements)
$assigned = $orders.content | Where-Object status -eq 'ASIGNADA' | Select-Object -First 1
if ($assigned) {
    $scheduled = Request PATCH "/work-orders/$($assigned.id)/schedule" @{scheduledDate='2026-09-20'} 200
    Check 'Programar asignada conserva cuadrilla' ($scheduled.status -eq 'ASIGNADA' -and $scheduled.crew -eq $assigned.crew)
    $restored = Request PATCH "/work-orders/$($assigned.id)/schedule" @{scheduledDate=$assigned.scheduledDate} 200
    Check 'Reprogramar asignada' ($restored.scheduledDate -eq $assigned.scheduledDate)
}
$api = Invoke-WebRequest 'http://localhost:8080/v3/api-docs'
$spec=$api.Content | ConvertFrom-Json
Check 'OpenAPI contiene endpoints nuevos' ($null -ne $spec.paths.'/api/public-works/street-closures'.post -and $null -ne $spec.paths.'/api/public-works/dashboard/summary'.get)
$api.Content | Set-Content -Encoding utf8 "$PSScriptRoot/openapi.json"
$summary | ConvertTo-Json -Depth 10 | Set-Content -Encoding utf8 "$PSScriptRoot/dashboard-example.json"
$checks | ConvertTo-Json -Depth 10 | Set-Content -Encoding utf8 "$PSScriptRoot/delivery-results.json"
Write-Host "RESULT: $($checks.Count) checks, 0 failures"
