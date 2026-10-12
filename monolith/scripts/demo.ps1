#requires -Version 7.0
param([string]$BaseUrl = 'http://localhost:8080')
$ErrorActionPreference = 'Stop'
function Send-Json([string]$Path, $Body) {
    $json = ConvertTo-Json $Body -Compress
    Invoke-RestMethod -NoProxy -Uri "$BaseUrl$Path" -Method Post -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($json))
}
# Reuse the fixture technician; each execution creates a new demonstration order.
$technicians = Invoke-RestMethod -NoProxy "$BaseUrl/api/technicians"
$technician = $technicians | Where-Object phone -eq '13800000001' | Select-Object -First 1
if (-not $technician) {
    $technician = Send-Json '/api/technicians' @{name='Demo plumber';phone='13800000001';skill='PLUMBING';available=$true}
}
$order = Send-Json '/api/repair-orders' @{residentName='Demo resident';contactPhone='13900000001';location='Building 1 Room 101';description='Leaking pipe';category='PLUMBING'}
Write-Output "Created order $($order.id): $($order.status)"
$reviewed = Send-Json "/api/repair-orders/$($order.id)/review" @{approved=$true;note='Approved for dispatch'}
Write-Output "Reviewed: $($reviewed.status)"
$assigned = Send-Json "/api/repair-orders/$($order.id)/assign" @{technicianId=$technician.id}
Write-Output "Assigned: $($assigned.status)"
$accepted = Send-Json "/api/repair-orders/$($order.id)/accept" @{technicianId=$technician.id}
Write-Output "Accepted: $($accepted.status)"
Invoke-RestMethod -NoProxy "$BaseUrl/api/repair-orders/$($order.id)" | ConvertTo-Json -Depth 5
