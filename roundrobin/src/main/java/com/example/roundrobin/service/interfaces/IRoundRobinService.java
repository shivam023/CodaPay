package com.example.roundrobin.service.interfaces;

import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

public interface IRoundRobinService {
    ResponseEntity<?> forwardRequest(Map<String, Object> requestBody);

    void updateInstances(List<String> newInstances);

    List<String> getInstances();
}
