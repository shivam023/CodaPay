package com.example.roundrobin.service.interfaces;

import org.springframework.http.ResponseEntity;

import java.util.Map;

public interface IRoundRobinService {
    ResponseEntity<?> forwardRequest(Map<String, Object> requestBody);
}
