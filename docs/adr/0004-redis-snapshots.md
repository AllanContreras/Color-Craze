# ADR 0004: Persistencia de estado con Redis (Snapshots de GameSession y Board)

## Contexto
La aplicación Color Craze necesita tolerancia a fallos y capacidad de recuperación de partidas en curso. El estado de juego (`GameSession`) y el estado del tablero (posiciones y plataformas pintadas) viven principalmente en memoria y se reflejan parcialmente en Mongo. En escenarios de caída de instancia o despliegues, es valioso contar con snapshots rápidos para restaurar la sesión y el tablero.

## Decisión
- Introducir un servicio de snapshots con Redis: `GameStateSnapshotService`.
- Claves y contenido:
  - `game:{code}`: JSON serializado de `GameSession`.
  - `board:{code}`: JSON con `positions` y `platforms` del tablero.
- TTL configurable para las claves de snapshot mediante `redis.ttl.seconds` (por defecto 86400s = 24h) para evitar acumulación indefinida.
- Integración en el ciclo de juego:
  - `createGame` y `startGame`: guardar snapshot de `GameSession` y snapshot inicial de `board`.
  - `handlePlayerMove`: actualizar snapshot de `board` tras cada movimiento (posiciones + plataformas exportadas).
  - `endGame`: eliminar snapshot de `board` y registrar métrica de duración de sesión.
  - `restartGame`: limpiar snapshot de `board` y actualizar snapshot de `GameSession` en estado `WAITING`.
- Endpoints admin (seguridad aplicada):
  - `GET /admin/snapshot/{code}`: obtener JSON de `GameSession`.
  - `POST /admin/restore/{code}`: restaurar `GameSession` desde snapshot.
  - `GET /admin/snapshot/board/{code}`: obtener JSON del snapshot del tablero.
  - `DELETE /admin/snapshot/board/{code}`: eliminar snapshot del tablero.
- Seguridad: `/admin/**` protegido con `ROLE_ADMIN`. Se habilita un admin en memoria opcional con `admin.user` y `admin.pass` para entornos locales.

## Estado
Implementado en código y protegido. Redis es opcional; si no está configurado, el servicio usa un fallback en memoria (adecuado solo para desarrollo local).

## Consecuencias
- Pros:
  - Recuperación más rápida de estados de juego ante fallos.
  - Control de vida de snapshots con TTL para limpieza automática.
  - Herramientas admin para inspección y restauración.
- Contras / trade-offs:
  - Complejidad operativa: requiere Redis gestionado en producción.
  - Consistencia eventual entre Mongo y snapshots; deben definirse playbooks de recuperación.
  - Seguridad: exponer endpoints admin requiere control de acceso y auditoría.

## Configuración
En `application.properties`:
```
redis.host=localhost
redis.port=6379
redis.ttl.seconds=86400
admin.user=admin
admin.pass=changeme
```

## Operación
- Arranque local de Redis (Docker): `docker run -d --name redis -p 6379:6379 redis:7-alpine`.
- Verificar claves:
  - `redis-cli GET game:ABC123`
  - `redis-cli GET board:ABC123`
- Endpoints admin (Basic Auth si se usa admin en memoria):
  - `GET /admin/snapshot/{code}`
  - `POST /admin/restore/{code}`
  - `GET /admin/snapshot/board/{code}`
  - `DELETE /admin/snapshot/board/{code}`

## Métricas relacionadas
- `game.session.duration.ms`: resumen de duración de partidas.
- `game.rooms.active`: gauge de salas activas.
- `game.tick.interval.ms`: distribución de intervalos del scheduler (jitter observability).

## Futuro
- Snapshot y restauración completa del tablero desde Redis (no solo inspección).
- Playbook de recuperación en múltiples instancias y coordinación con STOMP relay.
- Auditoría y logging específico de operaciones admin.