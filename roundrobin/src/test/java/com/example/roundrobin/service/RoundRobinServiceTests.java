package com.example.roundrobin.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RoundRobinServiceTest {

    @InjectMocks
    private RoundRobinService roundRobinService;

    @Mock
    private RestTemplate restTemplate;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testForwardRequestToFirstInstance() {
        // Given
        Map<String, Object> requestBody = new HashMap<>();
        ResponseEntity<Map> mockResponse = new ResponseEntity<>(new HashMap<>(), HttpStatus.OK);

        when(restTemplate.postForEntity(eq("http://localhost:8081/api"), any(), eq(Map.class)))
                .thenReturn(mockResponse);

        // When
        ResponseEntity<?> response = roundRobinService.forwardRequest(requestBody);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());

        // Capture the URL and verify it was the first instance
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate, times(1)).postForEntity(urlCaptor.capture(), any(), eq(Map.class));
        assertEquals("http://localhost:8081/api", urlCaptor.getValue());
    }

    @Test
    void testRoundRobinBehaviorAcrossInstances() {
        // Given
        Map<String, Object> requestBody = new HashMap<>();
        ResponseEntity<Map> mockResponse = new ResponseEntity<>(new HashMap<>(), HttpStatus.OK);

        // Simulate the first instance failing
        when(restTemplate.postForEntity(eq("http://localhost:8081/api"), any(), eq(Map.class)))
                .thenThrow(new RestClientException("Instance 8081 down"));

        // Simulate the second instance succeeding
        when(restTemplate.postForEntity(eq("http://localhost:8082/api"), any(), eq(Map.class)))
                .thenReturn(mockResponse);

        // When
        ResponseEntity<?> response = roundRobinService.forwardRequest(requestBody);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());

        // Verify first call was to the first instance, and it failed
        verify(restTemplate, times(1)).postForEntity(eq("http://localhost:8081/api"), any(), eq(Map.class));

        // Verify the second call was to the second instance and succeeded
        verify(restTemplate, times(1)).postForEntity(eq("http://localhost:8082/api"), any(), eq(Map.class));
    }

    @Test
    void testAllInstancesDownFailure() {
        // Given
        Map<String, Object> requestBody = new HashMap<>();

        // Simulate all instances failing
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenThrow(new RestClientException("Instance down"));

        // When / Then
        assertThrows(RestClientException.class, () -> roundRobinService.forwardRequest(requestBody));

        // Verify that all instances were tried
        verify(restTemplate, times(1)).postForEntity(eq("http://localhost:8081/api"), any(), eq(Map.class));
        verify(restTemplate, times(1)).postForEntity(eq("http://localhost:8082/api"), any(), eq(Map.class));
        verify(restTemplate, times(1)).postForEntity(eq("http://localhost:8083/api"), any(), eq(Map.class));
    }

    @Test
    void testInstanceTakingTooLong() {
        // Given
        Map<String, Object> requestBody = new HashMap<>();
        ResponseEntity<Map> mockResponse = new ResponseEntity<>(new HashMap<>(), HttpStatus.OK);

        // Simulate first instance taking too long (timeout)
        doAnswer(invocation -> {
            Thread.sleep(4000);
            throw new RestClientException("Timeout");
        }).when(restTemplate).postForEntity(eq("http://localhost:8081/api"), any(), eq(Map.class));

        // Simulate the second instance responding successfully
        when(restTemplate.postForEntity(eq("http://localhost:8082/api"), any(), eq(Map.class)))
                .thenReturn(mockResponse);

        // When
        ResponseEntity<?> response = roundRobinService.forwardRequest(requestBody);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());

        // Verify that the first instance was tried and timed out
        verify(restTemplate, times(1)).postForEntity(eq("http://localhost:8081/api"), any(), eq(Map.class));

        // Verify that the second instance was used after the timeout of the first instance
        verify(restTemplate, times(1)).postForEntity(eq("http://localhost:8082/api"), any(), eq(Map.class));
    }

    @Test
    void testAllInstancesFailIncludingTimeout() {
        Map<String, Object> requestBody = new HashMap<>();

        // Simulate all instances failing (timeouts or errors)
        doAnswer(invocation -> {
            Thread.sleep(4000);
            throw new RestClientException("Timeout");
        }).when(restTemplate).postForEntity(anyString(), any(), eq(Map.class));

        // When / Then
        assertThrows(RestClientException.class, () -> roundRobinService.forwardRequest(requestBody));

        // Verify that all instances were tried and each timed out
        verify(restTemplate, times(1)).postForEntity(eq("http://localhost:8081/api"), any(), eq(Map.class));
        verify(restTemplate, times(1)).postForEntity(eq("http://localhost:8082/api"), any(), eq(Map.class));
        verify(restTemplate, times(1)).postForEntity(eq("http://localhost:8083/api"), any(), eq(Map.class));
    }
}