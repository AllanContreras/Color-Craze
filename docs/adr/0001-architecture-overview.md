# ADR 0001: Initial Architecture Overview

## Status
Accepted

## Context
Color Craze is a multiplayer arena game using Spring Boot (REST + STOMP WebSocket) and a React frontend. Current deployment runs single-instance with in-memory board state. We need to evolve toward horizontal scalability, fault tolerance, and observability while maintaining fast real-time updates.

## Decision
Adopt modular service layering within a single Spring Boot application for now:
- Game & Board logic kept in-memory for low latency.
- STOMP over WebSocket with SimpleBroker initially; plan relay (RabbitMQ / Azure Service Bus) for multi-instance.
- JWT-based stateless authentication for REST and STOMP (interceptor validates token + room access).
- Rate limiting with Bucket4j at service layer for movement spam control.
- Structured JSON logging via Logback encoder including traceId/spanId placeholders (Micrometer tracing planned) and contextual MDC keys (gameCode, playerId).

## Rationale
Single-instance simplifies early development; introducing relay & Redis later avoids premature complexity. Bucket4j offers precise token bucket semantics and is production proven. Structured logs are necessary for security incidents and performance analysis.

## Consequences
+ Low latency path for moves.
+ Clear upgrade path to external broker & Redis without refactoring core domain objects.
+ JSON logs enable correlation and ingestion by ELK/Azure Monitor.
- In-memory state risks loss on node crash until Redis added.
- SimpleBroker limits horizontal scale; must prioritize relay integration soon.

## Next Steps
1. Add Micrometer tracing + Prometheus exports.
2. Introduce Redis persistence for GameSession & board snapshot.
3. Configure STOMP relay and validate multi-instance load test.
4. Add ADRs for scalability strategy, security enhancements, performance metrics.
