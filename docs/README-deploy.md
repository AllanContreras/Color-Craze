## Disponibilidad y Escalabilidad

- Entorno: producción en Railway con múltiples réplicas detrás de Load Balancer. Opcional: STOMP broker externo.

**Opción A — Escalabilidad Horizontal**
- Objetivo: P95 < 150 ms; CPU < 70%; autoscaling ≤ 90 s; 0 desconexiones.
- Variables (backend):
  - `SERVER_PORT`: recomendado `8080`.
  - `SPRING_PROFILES_ACTIVE`: `railway`.
  - `COLORCRAZE_STOMP_RELAY_ENABLED`: `true` si hay broker; `false` si se usa simple broker.
  - `COLORCRAZE_STOMP_RELAY_HOST`: host del broker (ej. `broker.yourdomain`).
  - `COLORCRAZE_STOMP_RELAY_PORT`: puerto (ej. `61613`).
  - `COLORCRAZE_STOMP_RELAY_LOGIN` / `COLORCRAZE_STOMP_RELAY_PASSCODE`.
  - `APP_REDIS_ENABLED`: `true` solo si hay Redis; caso contrario `false`.
  - `SPRING_DATA_MONGODB_URI`: cadena Mongo Atlas.
  - `JWT_SECRET`: secreto para firmar tokens.
- Variables (frontend):
  - `VITE_API_BASE`: URL del backend (ej. `https://<railway-app>.up.railway.app`).
  - `VITE_WS_PATH`: `/color-craze/ws`.
  - `VITE_WS_HEARTBEAT_MS`: `5000`.
- Pasos:
  - Desplegar 2+ réplicas del backend (Railway scale). Verificar health `/actuator/health`.
  - Activar broker STOMP si disponible y apuntar variables de relay.
  - Confirmar heartbeats y reconexión en frontend (`@stomp/stompjs`).
  - Prueba de subida súbita 20→120 usuarios en ≤2 min (k6). Medir métricas.

**Opción B — Tolerancia a Fallos**
- Objetivo: Reconexión < 5 s; pérdida < 1 frame; 0 partidas canceladas.
- Acciones:
  - Habilitar heartbeats `5000/5000` y `reconnectDelay=2000` en STOMP cliente.
  - Usar Redis snapshots cuando esté habilitado para continuidad de estado.
  - Configurar timeouts razonables en REST (`server.tomcat.connection-timeout`).
  - Validar failover: matar una instancia; observar reconexión y continuidad.

## Seguridad

**Autorización con JWT (mínimo viable)**
- Propósito: emitir JWT para autorización de rutas. No hay autenticación real.
- Endpoints:
  - `POST /api/auth/login` → retorna JWT firmado con `JWT_SECRET` y rol.
  - Rutas protegidas requieren `Authorization: Bearer <token>`.
- WebSocket/STOMP:
  - Requiere encabezado `Authorization` en `CONNECT` y será validado.
  - Conexiones sin token válido serán rechazadas.
- Medidas:
  - 100% endpoints críticos protegidos.
  - Token inválido → `401`.
  - WS inválido → rechazo inmediato (no `CONNECTED`).

**Mitigación de Flood / Rate Limit**
- Límite: ≤ 20 mensajes/s por conexión.
- Implementación sugerida:
  - Interceptor STOMP con contador por sesión y ventana deslizante (1 s).
  - Respuesta: descartar o cerrar sesión en exceso sostenido.
- Medición: P95 < 120 ms bajo carga maliciosa controlada.

## Trazabilidad y Recuperación

- Logging estructurado en JSON:
  - Peticiones HTTP, respuestas, errores, tiempos de ejecución.
  - Eventos WS importantes: CONNECT, SUBSCRIBE, SEND, DISCONNECT, errores.
- Centralización: enviar logs a proveedor (ej. Railway logs, opcional ELK).
- Metas: diagnóstico < 10 min; corrección < 5 min; precisión > 99%.

## CI/CD y Cobertura

- Pipeline CI (GitHub Actions + SonarCloud):
  - Build backend/ frontend.
  - Tests: backend ≥ 80%, frontend ≥ 40%.
  - Análisis SonarCloud y Quality Gate.
  - Tiempo total < 6 min.

## Rendimiento / Latencia

- Prueba con k6: simular 50 jugadores simultáneos.
- Métricas esperadas:
  - WS P95 < 120 ms.
  - Jitter < 10 ms.
  - Pérdida < 1%.
  - CPU < 70%.

**Scripts k6 incluidos**
- `scripts/load/k6-surge-120.js`: subida 20→120 VUs en 2 min.
- `scripts/load/k6-50-players.js`: 50 VUs sostenidos por 2 min.
- Ejecuta (PowerShell):
```
setx API_BASE "https://<tu-app>.up.railway.app"
setx JWT "<tu-token>"
k6 run scripts\load\k6-surge-120.js
k6 run scripts\load\k6-50-players.js
```

## Verificación rápida

- Comandos útiles (PowerShell):
```
curl https://<app>/actuator/health
curl -H "Authorization: Bearer <JWT>" https://<app>/api/games
```

## Notas de configuración

- No habilitar Redis sin URL válida; mantener `APP_REDIS_ENABLED=false` si no está configurado.
- Activar relay solo si existe broker STOMP válido; caso contrario usar simple broker.
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
