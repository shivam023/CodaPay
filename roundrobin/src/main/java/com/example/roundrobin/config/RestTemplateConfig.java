package com.example.roundrobin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);  // 2 seconds for connection timeout
        factory.setReadTimeout(3000);     // 3 seconds for read timeout
        return new RestTemplate(factory);
    }
}