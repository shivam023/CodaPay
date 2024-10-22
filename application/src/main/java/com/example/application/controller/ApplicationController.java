package com.example.application.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/app")
public class ApplicationController {
    // API 1: Handles normal requests without delay
    @PostMapping("/normal")
    public Map<String, Object> handleNormalPost(@RequestBody Map<String, Object> payload) {
        return payload;
    }

    // API 2: Introduces an artificial delay to simulate circuit breaker opening
    @PostMapping("/delayed")
    public Map<String, Object> handleDelayedPost(@RequestBody Map<String, Object> payload) throws InterruptedException {
        Thread.sleep(5000);
        return payload;
    }
}
