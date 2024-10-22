package com.example.roundrobin.service;

import com.example.roundrobin.service.interfaces.IRoundRobinService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RoundRobinService implements IRoundRobinService {
    private static final Logger logger = LoggerFactory.getLogger(RoundRobinService.class);

    private final RestTemplate restTemplate;

    @Value("${application.instances}")
    String instanceUrls;

    private List<String> instances;
    private final AtomicInteger counter = new AtomicInteger(0);
    private int maxAttempts;
    final ConcurrentHashMap<String, Instant> circuitBreaker = new ConcurrentHashMap<>();

    public RoundRobinService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @PostConstruct
    void init() {
        if (instanceUrls == null || instanceUrls.isEmpty()) {
            logger.error("No instances provided in configuration. Please check your configuration.");
            throw new IllegalArgumentException("Instance URLs cannot be null or empty");
        }
        this.instances = List.of(instanceUrls.split(","));
        this.maxAttempts = instances.size();
    }

    @Override
    public ResponseEntity<?> forwardRequest(Map<String, Object> requestBody) {
        int attempts = 0;
        String targetInstance;

        while (attempts < maxAttempts) {
            targetInstance = getNextInstance();
            logger.info("Attempting to forward request to instance: {}", targetInstance);
            try {
                // can use facade design pattern for different rest client calls
                return restTemplate.postForEntity(Objects.requireNonNull(targetInstance), requestBody, Map.class);
            } catch (RestClientException e) {
                attempts++;
                logger.error("Failed to forward request to instance: {}. Error: {}", targetInstance, e.getMessage());

                // Add the instance to the circuit breaker on failure
                circuitBreaker.put(Objects.requireNonNull(targetInstance), Instant.now());

                if (attempts >= maxAttempts) {
                    logger.error("All instances failed after {} attempts.", attempts);
                    throw e;
                }

                logger.info("Retrying with the next instance. Attempt: {}", attempts);
            }
        }

        // If all retries fail, return a failure response
        return ResponseEntity.status(503).body(Map.of("error", "All instances are unavailable"));
    }

    // Get the next instance in round-robin fashion, skipping instances with open circuits
    private String getNextInstance() {
        int attempts = 0;
        String instance;
        while (attempts < maxAttempts) {
            instance = instances.get(counter.getAndUpdate(i -> (i + 1) % instances.size()));

            if (isCircuitOpen(instance)) {
                logger.warn("Instance {} is in circuit breaker mode. Skipping.", instance);
                attempts++;
            } else {
                return instance;
            }
        }
        return null;
    }

    private boolean isCircuitOpen(String instance) {
        if (circuitBreaker.containsKey(instance)) {
            Instant failedTime = circuitBreaker.get(instance);
            Instant now = Instant.now();
            // If the instance failed more than 10 seconds ago, close the circuit
            if (now.isAfter(failedTime.plusSeconds(10))) {
                logger.info("Circuit for instance {} is closed.", instance);
                circuitBreaker.remove(instance);
                return false;
            } else {
                return true;
            }
        }
        return false;
    }
}
