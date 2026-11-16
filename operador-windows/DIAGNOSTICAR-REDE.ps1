# Diagnostico somente leitura - nao altera configuracoes.
Write-Host "SERIDOPLAST | DIAGNOSTICO DE CONEXAO" -ForegroundColor Cyan
Write-Host "Perfis de rede (precisam estar Privados):"
Get-NetConnectionProfile | Select-Object InterfaceAlias,Name,NetworkCategory | Format-Table -AutoSize
Write-Host "IPs privados do computador:"
Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue | Where-Object { $_.IPAddress -match '^(10\.|192\.168\.|172\.(1[6-9]|2[0-9]|3[01])\.)' } | Select-Object InterfaceAlias,IPAddress,PrefixLength | Format-Table -AutoSize
Write-Host "Servidor HTTP porta 8765:"
Get-NetTCPConnection -State Listen -LocalPort 8765 -ErrorAction SilentlyContinue | Select-Object LocalAddress,LocalPort,State | Format-Table -AutoSize
Write-Host "Descoberta UDP porta 8766:"
Get-NetUDPEndpoint -LocalPort 8766 -ErrorAction SilentlyContinue | Select-Object LocalAddress,LocalPort | Format-Table -AutoSize
Write-Host "Regras do firewall Seridoplast:"
Get-NetFirewallRule -ErrorAction SilentlyContinue | Where-Object { $_.DisplayName -like 'Seridoplast Observacoes*' } | Select-Object DisplayName,Enabled,Profile,Direction,Action | Format-Table -AutoSize
Write-Host "Se o painel estiver aberto e nao aparecer TCP 8765 ou UDP 8766, confira se o EXE continua executando." -ForegroundColor Yellow
Write-Host "Se o perfil for Publico, altere para Privado nas Configuracoes de Rede do Windows." -ForegroundColor Yellow
