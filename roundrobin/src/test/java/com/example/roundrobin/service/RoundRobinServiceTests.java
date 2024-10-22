package com.example.roundrobin.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RoundRobinServiceTest {

    private RoundRobinService roundRobinService;
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        restTemplate = Mockito.mock(RestTemplate.class);
        roundRobinService = new RoundRobinService(restTemplate);
    }

    @Test
    void testUpdateInstances() {
        // Arrange
        List<String> newInstances = List.of("http://localhost:8081", "http://localhost:8082");

        // Act
        roundRobinService.updateInstances(newInstances);

        // Assert
        assertEquals(2, roundRobinService.getInstances().size());
        assertTrue(roundRobinService.getInstances().contains("http://localhost:8081"));
        assertTrue(roundRobinService.getInstances().contains("http://localhost:8082"));
    }

    @Test
    void testForwardRequest_success() {
        // Arrange
        Map<String, Object> requestBody = Map.of("key", "value");
        ResponseEntity<Map> mockResponse = ResponseEntity.ok(Map.of("response", "success"));

        roundRobinService.updateInstances(List.of("http://localhost:8081", "http://localhost:8082"));

        when(restTemplate.postForEntity(eq("http://localhost:8081"), eq(requestBody), eq(Map.class)))
                .thenReturn(mockResponse);

        // Act
        ResponseEntity<?> response = roundRobinService.forwardRequest(requestBody);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("success", ((Map<?, ?>) response.getBody()).get("response"));
    }

    @Test
    void testForwardRequest_retryOnFailure() {
        // Arrange
        Map<String, Object> requestBody = Map.of("key", "value");
        ResponseEntity<Map> mockResponse = ResponseEntity.ok(Map.of("response", "success"));

        roundRobinService.updateInstances(List.of("http://localhost:8081", "http://localhost:8082"));

        when(restTemplate.postForEntity(eq("http://localhost:8081"), eq(requestBody), eq(Map.class)))
                .thenThrow(new RestClientException("Connection refused"));
        when(restTemplate.postForEntity(eq("http://localhost:8082"), eq(requestBody), eq(Map.class)))
                .thenReturn(mockResponse);

        // Act
        ResponseEntity<?> response = roundRobinService.forwardRequest(requestBody);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("success", ((Map<?, ?>) response.getBody()).get("response"));
    }

    @Test
    void testForwardRequest_allFailures() {
        // Arrange
        Map<String, Object> requestBody = Map.of("key", "value");

        roundRobinService.updateInstances(List.of("http://localhost:8081", "http://localhost:8082"));

        when(restTemplate.postForEntity(any(String.class), eq(requestBody), eq(Map.class)))
                .thenThrow(new RestClientException("Connection refused"));

        // Act
        ResponseEntity<?> response = roundRobinService.forwardRequest(requestBody);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("All instances are unavailable after 2 attempts.", ((Map<?, ?>) response.getBody()).get("error"));
    }

    @Test
    void testGetNextInstance_success() {
        // Arrange
        roundRobinService.updateInstances(List.of("http://localhost:8081", "http://localhost:8082"));

        // Act
        String firstInstance = roundRobinService.getNextInstance(2);
        String secondInstance = roundRobinService.getNextInstance(2);

        // Assert
        assertEquals("http://localhost:8081", firstInstance);
        assertEquals("http://localhost:8082", secondInstance);
    }

    @Test
    void testIsCircuitOpen_open() {
        // Arrange
        String instance = "http://localhost:8081";
        roundRobinService.circuitBreaker.put(instance, Instant.now());

        // Act
        boolean isOpen = roundRobinService.isCircuitOpen(instance);

        // Assert
        assertTrue(isOpen, "Circuit should be open for this instance.");
    }

    @Test
    void testIsCircuitOpen_closed() {
        // Arrange
        String instance = "http://localhost:8081";
        Instant pastTime = Instant.now().minusSeconds(11);
        roundRobinService.circuitBreaker.put(instance, pastTime);

        // Act
        boolean isOpen = roundRobinService.isCircuitOpen(instance);

        // Assert
        assertFalse(isOpen, "Circuit should be closed after the timeout period.");
    }

    @Test
    void testCircuitBreakerTimeout_closesAfterTimeout() throws InterruptedException {
        // Arrange
        String instance = "http://localhost:8081";
        roundRobinService.circuitBreaker.put(instance, Instant.now());

        // Simulate a delay to allow timeout
        Thread.sleep(11000);

        // Act
        boolean isOpen = roundRobinService.isCircuitOpen(instance);

        // Assert
        assertFalse(isOpen, "Circuit should be closed after 10 seconds.");
    }

    @Test
    void testForwardRequest_circuitBreaker() throws InterruptedException {
        // Arrange
        Map<String, Object> requestBody = Map.of("key", "value");

        roundRobinService.updateInstances(List.of("http://localhost:8081", "http://localhost:8082"));

        // First, fail the first instance
        when(restTemplate.postForEntity(eq("http://localhost:8081"), eq(requestBody), eq(Map.class)))
                .thenThrow(new RestClientException("Connection refused"));

        // Simulate circuit breaker open
        roundRobinService.forwardRequest(requestBody);
        assertTrue(roundRobinService.isCircuitOpen("http://localhost:8081"), "Circuit should be open after failure.");

        // Second, successful call to the second instance
        when(restTemplate.postForEntity(eq("http://localhost:8082"), eq(requestBody), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("response", "success")));

        ResponseEntity<?> response = roundRobinService.forwardRequest(requestBody);

        // Simulate waiting for circuit breaker to reset
        Thread.sleep(11000);

        // Act - Circuit should now close and the first instance should be retried
        boolean isCircuitOpenAfterTimeout = roundRobinService.isCircuitOpen("http://localhost:8081");

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("success", ((Map<?, ?>) response.getBody()).get("response"));
        assertFalse(isCircuitOpenAfterTimeout, "Circuit should be closed after timeout.");
    }
}