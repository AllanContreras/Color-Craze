# ADR 0002: Security & Rate Limiting

## Status
Accepted

## Context
We require defense against unauthorized access (REST + STOMP) and flooding of movement messages (>50 msg/s). Current implementation performs JWT validation and ad-hoc per-second counters in `GameService`.

## Decision
1. Enforce JWT validation for REST endpoints via Spring Security filter chain and for STOMP via interceptor (already present).
2. Replace ad-hoc counters with Bucket4j token bucket: 20 move messages allowed per second per (gameCode, playerId). Excess messages are rejected in payload (`rateLimited=true`).
3. Prepare for emitting structured security logs (JSON) including `traceId`, `playerId`, `gameCode`, event type (AUTH_FAIL, RATE_LIMIT_EXCEEDED).
4. Plan alert integration by counting exceeded events (future Micrometer counter) and exporting to Prometheus.

## Rationale
Bucket4j provides proven, thread-safe rate limiting with clear semantics, easier to tune than custom counters. Structured logs simplify forensic analysis and anomaly detection. Separation of auth and rate limiting concerns reduces coupling in `GameService`.

## Consequences
+ Predictable enforcement of movement throughput.
+ Extensible for dynamic policies and distributed (later via Redis bucket state if needed).
+ Improved audit trail for security events.
- Additional dependencies and minimal performance overhead for token bucket checks.

## Alternatives Considered
- Spring Cloud Gateway w/ Redis rate limiter: deferred until multi-service architecture or external API gateway adoption.
- Simple counters: insufficient for burst smoothing and tuning.

## Next Steps
1. Add Micrometer counter for `movement.rate_limited.count`.
2. Implement security event logger utility.
3. Evaluate distributed Bucket4j (Redis) when horizontal scaling ready.
