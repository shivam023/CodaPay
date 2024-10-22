package com.example.roundrobin.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoundRobinServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private RoundRobinService roundRobinService;

    private Map<String, Object> requestBody;

    @BeforeEach
    void setUp() {
        // Initialize Mockito annotations
        MockitoAnnotations.openMocks(this);

        // Manually set instance URLs for testing
        roundRobinService.instanceUrls = "http://instance1.com,http://instance2.com";

        // Manually call the @PostConstruct method to initialize instances
        roundRobinService.init();

        // Prepare the request body for testing
        requestBody = new HashMap<>();
        requestBody.put("key", "value");
    }

    @Test
    void testForwardRequest_Successful() {
        // Mock a successful response from the first instance
        ResponseEntity<Map> response = new ResponseEntity<>(new HashMap<>(), HttpStatus.OK);
        when(restTemplate.postForEntity(eq("http://instance1.com"), any(), eq(Map.class))).thenReturn(response);

        ResponseEntity<?> result = roundRobinService.forwardRequest(requestBody);

        // Assert the result is successful
        assertEquals(HttpStatus.OK, result.getStatusCode());
        verify(restTemplate, times(1)).postForEntity(eq("http://instance1.com"), any(), eq(Map.class));
    }

    @Test
    void testForwardRequest_FirstInstanceFails_SecondSucceeds() {
        // Simulate a failure on the first instance
        when(restTemplate.postForEntity(eq("http://instance1.com"), any(), eq(Map.class)))
                .thenThrow(new RestClientException("Instance 1 failed"));

        // Simulate a successful response from the second instance
        ResponseEntity<Map> response = new ResponseEntity<>(new HashMap<>(), HttpStatus.OK);
        when(restTemplate.postForEntity(eq("http://instance2.com"), any(), eq(Map.class))).thenReturn(response);

        ResponseEntity<?> result = roundRobinService.forwardRequest(requestBody);

        // Assert that the second instance was tried and succeeded
        assertEquals(HttpStatus.OK, result.getStatusCode());
        verify(restTemplate, times(1)).postForEntity(eq("http://instance1.com"), any(), eq(Map.class));
        verify(restTemplate, times(1)).postForEntity(eq("http://instance2.com"), any(), eq(Map.class));
    }

    @Test
    void testForwardRequest_AllInstancesFail() {
        // Simulate failure for both instances
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenThrow(new RestClientException("Instance failed"));

        RestClientException exception = assertThrows(RestClientException.class, () -> {
            roundRobinService.forwardRequest(requestBody);
        });

        // Assert that the exception was thrown and all instances were tried
        assertEquals("Instance failed", exception.getMessage());
        verify(restTemplate, times(2)).postForEntity(anyString(), any(), eq(Map.class));
    }

    @Test
    void testCircuitBreaker_SkipOpenCircuitInstance() {
        // Simulate that the first instance is in the open circuit state
        roundRobinService.circuitBreaker.put("http://instance1.com", Instant.now());

        // Mock a successful response from the second instance
        ResponseEntity<Map> response = new ResponseEntity<>(new HashMap<>(), HttpStatus.OK);
        when(restTemplate.postForEntity(eq("http://instance2.com"), any(), eq(Map.class))).thenReturn(response);

        ResponseEntity<?> result = roundRobinService.forwardRequest(requestBody);

        // Assert that the first instance was skipped due to the circuit breaker, and the second instance succeeded
        assertEquals(HttpStatus.OK, result.getStatusCode());
        verify(restTemplate, times(1)).postForEntity(eq("http://instance2.com"), any(), eq(Map.class));
        verify(restTemplate, never()).postForEntity(eq("http://instance1.com"), any(), eq(Map.class));
    }

    @Test
    void testInit_NoInstanceUrlsProvided() {
        // Simulate a missing instanceUrls configuration
        roundRobinService.instanceUrls = "";

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            roundRobinService.init();
        });

        assertEquals("Instance URLs cannot be null or empty", exception.getMessage());
    }
}