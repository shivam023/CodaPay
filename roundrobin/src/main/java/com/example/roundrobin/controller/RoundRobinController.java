package com.example.roundrobin.controller;

import com.example.roundrobin.service.RoundRobinService;
import com.example.roundrobin.service.interfaces.IRoundRobinService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/roundrobin")
public class RoundRobinController {

    @Autowired
    private IRoundRobinService roundRobinService;

    @PostMapping
    public ResponseEntity<?> forwardRequest(@RequestBody Map<String, Object> payload) {
        return roundRobinService.forwardRequest(payload);
    }
}
