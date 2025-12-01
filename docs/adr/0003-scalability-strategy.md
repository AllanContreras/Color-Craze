# ADR 0003: Scalability & State Strategy

## Status
Proposed

## Context
Real-time gameplay with WebSockets must scale from tens to hundreds of concurrent players. Current implementation uses Spring SimpleBroker in-memory and stores game/board state in local memory. This limits horizontal scaling and resilience.

Key constraints:
- Maintain low latency (<150ms P95 move broadcast).
- Preserve game session state on node failure.
- Support auto-scaling instances behind a Load Balancer.

## Decision
Adopt phased scalability approach:
1. Short term: Single instance with improved metrics & rate limiting (already implemented).
2. Mid term: Introduce STOMP broker relay (RabbitMQ preferred for familiarity; Azure Service Bus alternative) to externalize subscription routing.
3. Persist volatile game state snapshots (GameSession + platform states + player positions) to Redis every N seconds and on significant events (join, move affecting score). Use Redis hashes keyed by `game:{code}`.
4. Use stateless JWT authentication; rely on Redis for reconnect continuity if a client is routed to a different instance.
5. Avoid sticky sessions once Redis snapshotting is active. If Redis not yet deployed, enable Load Balancer affinity (cookies) as temporary measure.

## Rationale
- Relay decouples message distribution from application threads enabling scale-out.
- Redis offers low-latency key access and pub/sub options for future cross-instance events (e.g., move propagation or scoreboard updates).
- Stateless backend simplifies autoscaling; persistence of minimal game snapshot avoids heavy serialization overhead.
- Phased rollout reduces risk compared to large refactor.

## Consequences
Positive:
- Clear migration path without breaking current APIs.
- Resilience improved (node crash recoverable via Redis snapshot on reconnect).
- LB can distribute new connections evenly.

Negative:
- Additional operational components (Redis, RabbitMQ) increase complexity and cost.
- Need consistency strategy for near-real-time moves (eventual vs immediate). Initial plan keeps authoritative state in one instance; later phase may introduce partitioning or sharding.

## Alternatives Considered
- Direct Hazelcast cluster for shared memory: adds complexity and tighter coupling.
- Full CQRS + event sourcing: overkill for current scale target.

## Implementation Plan
1. Add Redis dependency + configuration class.
2. Implement `GameStateSnapshotService` for periodic and event-driven persistence.
3. Integrate broker relay configuration (Spring STOMP relay) with environment variables for host/port/credentials.
4. Add reconnect logic in frontend to re-fetch `/game/{code}` then resubscribe.
5. Load test multi-instance (k6) and validate metrics.

## Metrics
- `game.state.snapshot.duration` timer.
- `game.relay.messages.out` counter.
- Redis snapshot age gauge per game.

## Next Steps
Create Redis configuration and snapshot service; implement broker relay configuration and test dual-instance run.
