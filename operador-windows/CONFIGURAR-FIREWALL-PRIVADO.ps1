# Regras limitadas ao perfil Privado e a computadores da rede local.
$ErrorActionPreference = 'Stop'
foreach ($rule in @('Seridoplast Observacoes TCP 8765', 'Seridoplast Observacoes Descoberta UDP 8766', 'Seridoplast Observacoes Anuncio UDP 8767')) {
    Get-NetFirewallRule -DisplayName $rule -ErrorAction SilentlyContinue | Remove-NetFirewallRule -ErrorAction SilentlyContinue
}
New-NetFirewallRule -DisplayName 'Seridoplast Observacoes TCP 8765' -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8765 -Profile Private -RemoteAddress LocalSubnet | Out-Null
New-NetFirewallRule -DisplayName 'Seridoplast Observacoes Descoberta UDP 8766' -Direction Inbound -Action Allow -Protocol UDP -LocalPort 8766 -Profile Private -RemoteAddress LocalSubnet | Out-Null
New-NetFirewallRule -DisplayName 'Seridoplast Observacoes Anuncio UDP 8767' -Direction Outbound -Action Allow -Protocol UDP -RemotePort 8767 -RemoteAddress '239.255.42.99' -Profile Private | Out-Null
Write-Host 'TCP 8765 e UDP 8766 liberadas para a rede local privada; anuncio multicast UDP 8767 liberado.' -ForegroundColor Green
Read-Host 'Pressione Enter para finalizar'
