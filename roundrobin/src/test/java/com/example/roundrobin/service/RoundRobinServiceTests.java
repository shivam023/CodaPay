package com.example.roundrobin.service;

import com.example.roundrobin.service.RoundRobinService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
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
    }

    @Test
    void testForwardRequest_success() {
        // Arrange
        Map<String, Object> requestBody = Map.of("key", "value");
        ResponseEntity<Map> mockResponse = ResponseEntity.ok(Map.of("response", "success"));

        // Add instances
        roundRobinService.updateInstances(List.of("http://localhost:8081", "http://localhost:8082"));

        // Set mock behavior
        when(restTemplate.postForEntity(eq("http://localhost:8081"), eq(requestBody), eq(Map.class)))
                .thenThrow(new RestClientException("Connection refused"));
        when(restTemplate.postForEntity(eq("http://localhost:8082"), eq(requestBody), eq(Map.class)))
                .thenReturn(mockResponse);

        // Act
        ResponseEntity<?> response = roundRobinService.forwardRequest(requestBody);

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("success", ((Map<?, ?>) response.getBody()).get("response"));
    }

    @Test
    void testForwardRequest_allFailures() {
        // Arrange
        Map<String, Object> requestBody = Map.of("key", "value");

        // Add instances
        roundRobinService.updateInstances(List.of("http://localhost:8081", "http://localhost:8082"));

        // Mock failures
        when(restTemplate.postForEntity(eq("http://localhost:8081"), eq(requestBody), eq(Map.class)))
                .thenThrow(new RestClientException("Connection refused"));
        when(restTemplate.postForEntity(eq("http://localhost:8082"), eq(requestBody), eq(Map.class)))
                .thenThrow(new RestClientException("Connection refused"));

        // Act & Assert
        Exception exception = assertThrows(RestClientException.class, () -> {
            roundRobinService.forwardRequest(requestBody);
        });

        assertEquals("Connection refused", exception.getMessage());
    }

    @Test
    void testCircuitBreaker_open() {
        // Arrange
        String instance = "http://localhost:8081";

        // Simulate the instance being marked as failed recently (within the threshold)
        roundRobinService.circuitBreaker.put(instance, Instant.now().minusSeconds(5));

        // Act
        boolean isOpen = roundRobinService.isCircuitOpen(instance);

        // Assert
        assertTrue(isOpen, "Expected the circuit to be open since it failed recently.");
    }

    @Test
    void testCircuitBreaker_closed() {
        // Arrange
        String instance = "http://localhost:8081";

        // Simulate the instance being marked as failed over the threshold
        roundRobinService.circuitBreaker.put(instance, Instant.now().minusSeconds(11));

        // Act
        boolean isOpen = roundRobinService.isCircuitOpen(instance);

        // Assert
        assertFalse(isOpen, "Expected the circuit to be closed since it failed more than 10 seconds ago.");
    }

    @Test
    void testForwardRequest_partialFailure() {
        // Arrange
        Map<String, Object> requestBody = Map.of("key", "value");
        ResponseEntity<Map> mockResponse = ResponseEntity.ok(Map.of("response", "success"));

        // Add instances
        roundRobinService.updateInstances(List.of("http://localhost:8081", "http://localhost:8082"));

        // First instance fails, second succeeds
        when(restTemplate.postForEntity(eq("http://localhost:8081"), any(Map.class), eq(Map.class)))
                .thenThrow(new RestClientException("Connection refused"));
        when(restTemplate.postForEntity(eq("http://localhost:8082"), any(Map.class), eq(Map.class)))
                .thenReturn(mockResponse);

        // Act
        ResponseEntity<?> response = roundRobinService.forwardRequest(requestBody);

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("success", ((Map<?, ?>) response.getBody()).get("response"));
    }

    @Test
    void testForwardRequest_takesLongerThanExpected() {
        // Arrange
        Map<String, Object> requestBody = Map.of("key", "value");

        // Add instances
        roundRobinService.updateInstances(List.of("http://localhost:8081", "http://localhost:8082"));

        // Simulate a long response from the first instance
        when(restTemplate.postForEntity(eq("http://localhost:8081"), eq(requestBody), eq(Map.class)))
                .thenThrow(new RestClientException("Connection refused")); // first instance fails
        when(restTemplate.postForEntity(eq("http://localhost:8082"), eq(requestBody), eq(Map.class)))
                .thenThrow(new RestClientException("Timeout")); // second instance times out

        // Act & Assert
        Exception exception = assertThrows(RestClientException.class, () -> {
            roundRobinService.forwardRequest(requestBody);
        });

        assertTrue(exception.getMessage().contains("Connection refused") || exception.getMessage().contains("Timeout"));
    }
}