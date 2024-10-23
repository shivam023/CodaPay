package com.example.roundrobin.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

@Service
public class InstanceListProvider {

    private final RoundRobinService roundRobinService; // Add RoundRobinService reference
    private volatile List<String> instances;
    private static final Logger logger = LoggerFactory.getLogger(InstanceListProvider.class);

    // Path to the file containing instance URLs
    private static final String INSTANCE_FILE_PATH = "/Users/shivamsingh/Developer/Code/CodaPay/roundrobin/src/main/java/com/example/roundrobin/instances.txt";

    // Constructor to inject RoundRobinService
    public InstanceListProvider(RoundRobinService roundRobinService) {
        this.roundRobinService = roundRobinService; // Assign the injected service
        refreshInstances();
    }

    @Scheduled(fixedRate = 10000) // Adjust the interval as needed
    public void refreshInstances() {
        try {
            String fileContent = Files.readString(Paths.get(INSTANCE_FILE_PATH));

            if (fileContent != null && !fileContent.isEmpty()) {
                this.instances = List.of(fileContent.split(","));
            } else {
                logger.warn("No instance URLs found in the file.");
                this.instances = List.of();
            }

            roundRobinService.updateInstances(this.instances);
        } catch (IOException e) {
            logger.error("Error reading instance file: {}", e.getMessage());
        }
    }

    public List<String> getInstances() {
        return instances;
    }
}