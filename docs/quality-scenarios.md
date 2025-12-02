# Quality Attribute Scenarios and Tactics

This document consolidates the agreed quality attribute scenarios and the associated architectural tactics to support professor review and Azure implementation. Format follows Stimulus | Source | Environment | Artifact | Response | Response Measure.

## Availability / Scalability

### A. Horizontal Scaling with Load Balancer (Primary)
- Stimulus: Sudden increase of concurrent users from 20 → 120 within ≤ 2 minutes.
- Source: Real users joining rooms and playing.
- Environment: Production; backend multi-instance behind Azure Load Balancer; STOMP broker relay active (or sticky sessions documented).
- Artifact: Backend services (API + WebSocket); distributed game state.
- Response: Load Balancer distributes new connections; additional instances are provisioned (if autoscale is configured); WebSocket sessions remain without forced loss; event latency within SLA.
- Response Measure: 0 forced disconnects; P95 WS latency < 150 ms; per-instance CPU < 70%; scale-out completed ≤ 90 s.

### B. Fault Tolerance on Node Failure (Optional)
- Stimulus: One backend instance crashes abruptly during an active match.
- Source: Infra failure / process crash.
- Environment: 3 instances behind LB; STOMP relay or sticky sessions enabled.
- Artifact: WebSocket connections; room/match state.
- Response: LB removes failed node; clients reconnect to healthy instances; persistent/replicated state avoids score/progress loss.
- Response Measure: Reconnection < 5 s; message loss < 1 paint frame; 0 matches cancelled.

## Security (Three Scenarios)

### 1. Authentication and Authorization for Room Access
- Stimulus: Unauthenticated user attempts to join a private room via REST and open a WebSocket.
- Source: Client without a valid token.
- Environment: Production, Spring Security filter + STOMP channel interceptor active.
- Artifact: Endpoint `joinGame`; channel `/topic/board/{code}/state`.
- Response: REST returns 401; WebSocket handshake/subscription rejected; audit event recorded.
- Response Measure: 100% correct blocks; log with traceId + IP; no `PlayerEntry` created.

### 2. Flood Mitigation / Rate Limiting on Movement
- Stimulus: Malicious client sends >50 movement messages per second.
- Source: Automated actor.
- Environment: Match in `PLAYING`; rate limiter active (Bucket4j / API Gateway policy).
- Artifact: Move endpoint / WS handler.
- Response: Excess messages discarded or 429 returned; security alert logged (WARN); legitimate players unaffected.
- Response Measure: Processed ≤ 20 msgs/s per client; P95 latency for legitimate players < 120 ms; alert recorded.

### 3. Traceability and Incident Recovery
- Stimulus: Anomaly detected (e.g., unexpected negative score).
- Source: Monitoring engine / operator.
- Environment: Production with structured JSON logs and correlation IDs.
- Artifact: Paint events and scoreboard updates.
- Response: Filter logs by `code` and `traceId` to reconstruct sequence; execute correction script (rollback / recalculation) without stopping other matches.
- Response Measure: Diagnosis < 10 min; correction < 5 min; >99% post-correction accuracy.

## Maintainability (Continuous Inspection)

- Stimulus: Push to `main` with backend and frontend changes.
- Source: Developer via Git.
- Environment: Azure DevOps CI pipeline integrated with SonarCloud.
- Artifact: Repository (Java + React).
- Response: Pipeline runs build, unit tests, and static analysis; Sonar quality gate evaluated; if gate fails (critical smells, low coverage), deployment is blocked and notification sent.
- Response Measure: Analysis < 6 min; backend coverage ≥ 80%; frontend coverage ≥ 40%; Quality Gate = Passed (Rating A).

## Performance / Latency (Real-Time)

- Stimulus: 50 simulated players (k6) move and paint for 60 s.
- Source: k6 load test script.
- Environment: Staging multi-instance (≥2 backends), broker relay active, standard DB.
- Artifact: WS channel `/topic/board/{code}/arena`, `tick` loop and broadcast.
- Response: System processes inputs and emits frames without backlog; delivery latency and tick-cycle times within SLA.
- Response Measure: P95 server→client WS latency (position messages) < 120 ms; tick loop jitter < 10 ms; frame loss < 1%; backend CPU < 70%; memory growth < +15%.

## Tactics by Scenario

- Availability: Azure Load Balancer, horizontal replicas; STOMP broker relay (RabbitMQ/Azure Service Bus) or sticky sessions; optional Redis for shared state (`game:{code}:state`, TTL = match duration).
- Fault Tolerance: Health probes; automatic reconnection in client; idempotent state sync.
- Security: JWT + Spring Security; STOMP interceptor validates token and room; rate limiting (Bucket4j / Gateway); structured logging (JSON) + audit trail; tracing (Sleuth).
- Maintainability: Azure Pipelines YAML; SonarCloud quality gate; Jacoco (Maven) and Vitest coverage; ADRs under `/docs/adr/`.
- Performance: Broadcast batching (paint ~150 ms, positions ~22 ms); scheduler tuning; Micrometer metrics (`ws.broadcast.latency`, `tick.duration`, `game.active.count`); k6 load test.

## Implementation Tasks (Actionable)

### Availability
- Configure Azure LB and at least 2 backend instances; health probes.
- Evaluate Redis for volatile state or document sticky session limitations.
- Configure STOMP broker relay (RabbitMQ / Azure Service Bus) if feasible; otherwise justify sticky sessions.

### Security
- Implement JWT issuance and validation.
- Add STOMP interceptor validating token and room code.
- Apply rate limiter on movement messages (≤20 msg/s per client).
- Enable logback JSON; add traceId via Sleuth; prepare score recalculation script.

### Maintainability
- Create Azure Pipelines YAML: Maven build + tests with Jacoco; Vite build + Vitest coverage; SonarCloud analysis and quality gate.
- Bind SonarCloud project; enforce gate on PR/main.
- Create ADRs documenting key decisions.

### Performance
- Expose Micrometer metrics for tick/broadcast; instrument payload timestamps for latency measurement.
- Author k6 script to simulate 50 users (WS connect, movement send, receive positions).
- Adjust broadcast intervals/tuning if SLA not met.

## Notes
- Availability A is the primary deliverable; diagrams will be prepared last per request.
- Azure infrastructure will be handled separately; this doc focuses on scenarios and code-level tactics.
