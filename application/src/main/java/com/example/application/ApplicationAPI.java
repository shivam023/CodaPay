package com.example.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@SpringBootApplication
@RestController
@RequestMapping("/api")
public class ApplicationAPI {

	public static void main(String[] args) {
		SpringApplication.run(ApplicationAPI.class, args);
	}

	@PostMapping
	public Map<String, Object> handlePost(@RequestBody Map<String, Object> payload) {
		return payload;
	}
}
