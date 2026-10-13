package com.zliy221.dataops.metric;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Component
@ConditionalOnProperty(name = "app.metric-simulator.enabled", havingValue = "true")
public class MetricSimulator {
    private static final List<String> SERVICES = List.of("gateway", "order-service", "member-service", "search-service");
    private final MetricService service;
    private final AtomicLong sequence = new AtomicLong();

    public MetricSimulator(MetricService service) { this.service = service; }

    @Scheduled(initialDelay = 1000, fixedDelayString = "${app.metric-simulator.interval-ms:5000}")
    public void emit() {
        long index = sequence.getAndIncrement();
        String serviceName = SERVICES.get((int) (index % SERVICES.size()));
        long requests = 8000 + (index * 719) % 9000;
        boolean incident = index > 0 && index % 11 == 0;
        long errors = incident ? Math.round(requests * 0.072) : 25 + (index * 17) % 180;
        long latency = incident ? 960 : 180 + (index * 43) % 520;
        service.ingest(new MetricRequest(serviceName, requests, errors, latency, Instant.now()));
    }
}

