package com.example.roundrobin.service;

import com.example.roundrobin.service.interfaces.IRoundRobinService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private volatile List<String> instances;  // Mark the instance list as volatile for thread safety
    private final AtomicInteger counter = new AtomicInteger(0);
    private final AtomicInteger maxAttempts = new AtomicInteger(0);
    final ConcurrentHashMap<String, Instant> circuitBreaker = new ConcurrentHashMap<>();

    public RoundRobinService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public void updateInstances(List<String> newInstances) {
        this.instances = newInstances;
        maxAttempts.set(instances.size());
        logger.info("Updated instance list: {}", newInstances);
    }

    @Override
    public List<String> getInstances() {
        return instances;
    }

    @Override
    public ResponseEntity<?> forwardRequest(Map<String, Object> requestBody) {
        int attempts = 0;
        String targetInstance;

        int maxAttemptsValue = maxAttempts.get();

        while (attempts < maxAttemptsValue) {
            targetInstance = getNextInstance(maxAttemptsValue);
            logger.info("Attempting to forward request to instance: {}", targetInstance);
            try {
                return restTemplate.postForEntity(Objects.requireNonNull(targetInstance), requestBody, Map.class);
            } catch (RestClientException e) {
                attempts++;
                logger.error("Failed to forward request to instance: {}. Error: {}", targetInstance, e.getMessage());

                // Add the instance to the circuit breaker on failure
                circuitBreaker.put(Objects.requireNonNull(targetInstance), Instant.now());

                if (attempts >= maxAttemptsValue) {
                    logger.error("All instances failed after {} attempts.", attempts);
                    return ResponseEntity.status(503).body(Map.of("error", "All instances are unavailable after " + attempts + " attempts."));
                }

                logger.info("Retrying with the next instance. Attempt: {}", attempts);
            }
        }

        // If all retries fail, return a failure response
        return ResponseEntity.status(503).body(Map.of("error", "All instances are unavailable"));
    }

    // Get the next instance in round-robin fashion, skipping instances with open circuits
    private String getNextInstance(int maxAttemptsValue) {
        int attempts = 0;
        String instance;
        while (attempts < maxAttemptsValue) {
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

    boolean isCircuitOpen(String instance) {
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
