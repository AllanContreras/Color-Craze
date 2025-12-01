package com.Color_craze.metrics;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Simple scheduler that records tick interval jitter for observability.
 * Fixed rate 1s; measures actual elapsed millis between ticks and pushes
 * to a distribution summary metric: game.tick.interval.ms
 */
@Component
public class TickMetrics {
    private final MeterRegistry meterRegistry;
    private volatile long lastTickMs = -1L;
    private final AtomicInteger tickCount = new AtomicInteger();
    private final AtomicLong lastIntervalMs = new AtomicLong();

    public TickMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        meterRegistry.gauge("game.tick.last.interval.ms", lastIntervalMs);
        meterRegistry.gauge("game.tick.count", tickCount);
    }

    @Scheduled(fixedRate = 1000)
    public void tick() {
        long now = Instant.now().toEpochMilli();
        if (lastTickMs != -1L) {
            long interval = now - lastTickMs;
            lastIntervalMs.set(interval);
            meterRegistry.summary("game.tick.interval.ms").record(interval);
        }
        lastTickMs = now;
        tickCount.incrementAndGet();
    }
}
