# 0005 — STOMP Broker Relay

Status: Proposed
Date: 2025-12-01

Context:
- WebSocket/STOMP traffic needs horizontal scale across multiple backend instances.
- Spring's simple broker is in-memory and bound to a single instance.
- A relay to an external broker (RabbitMQ or Azure Service Bus) enables message distribution across instances.

Decision:
- Enable `enableStompBrokerRelay` when running in multi-instance environments.
- Preferred broker: RabbitMQ (open-source, STOMP plugin, robust routing). Alternative: Azure Service Bus with AMQP bridge via Spring integration when RabbitMQ is not available.
- Topics/queues:
  - `/topic/board.{code}.state` broadcasting via a fanout/exchange.
  - `/queue/session.{code}.{player}` for targeted messages (optional).

Consequences:
- Pros: Horizontal scalability, durable routing, clear separation of concerns, operational visibility.
- Cons: Operational overhead (broker provisioning/monitoring), additional network hops.

Rollout Plan:
1. Provision RabbitMQ (managed or self-hosted) with STOMP enabled.
2. Configure properties `colorcraze.stomp.relay.*` (host, port, login, passcode) and set `enabled=true`.
3. Health-check relay connectivity at startup; fall back to simple broker if relay unreachable.
4. Update CI/CD to pass relay creds via environment.
5. Load test (k6) in multi-instance staging and validate P95 latency/jitter.

Operations:
- Monitor broker metrics (connections, channels, message rates).
- Alert on connection drops; clients auto-reconnect and re-subscribe (frontend reconnection logic implemented).
