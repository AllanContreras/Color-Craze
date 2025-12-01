# Despliegue — Color-Craze (LB + Relay + Redis)

## Variables de entorno
- `colorcraze.stomp.relay.enabled=true`
- `colorcraze.stomp.relay.host=<rabbit-host>`
- `colorcraze.stomp.relay.port=61613`
- `colorcraze.stomp.relay.clientLogin=<user>`
- `colorcraze.stomp.relay.clientPasscode=<pass>`
- `colorcraze.stomp.relay.systemLogin=<user>`
- `colorcraze.stomp.relay.systemPasscode=<pass>`
- `colorcraze.stomp.relay.virtualHost=/`
- `redis.host=<redis-host>`
- `redis.port=6379`

## Azure — Load Balancer / Autoscale
- Instancias: 2–3 (App Service o Container Apps)
- Health probe: `GET /actuator/health` cada 10s; timeout 5s; 2 fallos → retirar nodo
- Autoscale:
  - CPU > 70% por 2 min → +1 instancia
  - CPU < 40% por 5 min → -1 instancia

## RabbitMQ — STOMP Relay
- Habilitar plugin STOMP; abrir 61613; crear usuario/vhost; permisos
- Verificar conectividad desde backend (logs startup)

## Redis — Estado vivo
- Claves: `game:{code}`, `board:{code}`
- Persistencia en cada movimiento (ya implementada)
- TTL opcional vía configuración si se desea rotación

## Smoke Test
1. Instancia A: generar juego y publicar `/topic/board/{code}/state` (inicio o movimientos)
2. Cliente conectado vía SockJS/STOMP a Instancia B: suscrito a `/topic/board/{code}/state`
3. Validar recepción del mensaje (relay activo)

## Observabilidad
- Métricas Micrometer: `game.move.*`, `ws.move.rate_limited`, `game.rooms.*`, latencias
- Logs JSON con `traceId` y `clientIp` (REST) y `WARN` en rate limit WS

## SonarCloud / CI
- Azure DevOps pipeline ejecuta build/tests y `mvn sonar:sonar`
- Cobertura: JaCoCo (backend) + LCOV (frontend)
