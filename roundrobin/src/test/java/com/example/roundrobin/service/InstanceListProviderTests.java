package com.example.roundrobin.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InstanceListProviderTest {

    private RoundRobinService roundRobinService;
    private InstanceListProvider instanceListProvider;
    private static final String INSTANCE_FILE_PATH = "/Users/shivamsingh/Developer/Code/CodaPay/roundrobin/src/main/java/com/example/roundrobin/instances.txt";

    @BeforeEach
    void setUp() {
        roundRobinService = mock(RoundRobinService.class);
        instanceListProvider = spy(new InstanceListProvider(roundRobinService));
    }

    @Test
    void testRefreshInstances_success() throws IOException {
        // Arrange
        String fileContent = "http://localhost:8081,http://localhost:8082";
        List<String> expectedInstances = List.of("http://localhost:8081", "http://localhost:8082");

        // Using try-with-resources to mock the static method Files.readString()
        try (MockedStatic<Files> filesMock = Mockito.mockStatic(Files.class)) {
            filesMock.when(() -> Files.readString(Paths.get(INSTANCE_FILE_PATH))).thenReturn(fileContent);

            // Act
            instanceListProvider.refreshInstances();

            // Assert
            verify(roundRobinService, times(1)).updateInstances(expectedInstances);
            assertEquals(expectedInstances, instanceListProvider.getInstances());
        }
    }

    @Test
    void testGetInstances_afterRefresh() throws IOException {
        // Arrange
        String fileContent = "http://localhost:8081,http://localhost:8082";
        List<String> expectedInstances = List.of("http://localhost:8081", "http://localhost:8082");

        // Using try-with-resources to mock the static method Files.readString()
        try (MockedStatic<Files> filesMock = Mockito.mockStatic(Files.class)) {
            filesMock.when(() -> Files.readString(Paths.get(INSTANCE_FILE_PATH))).thenReturn(fileContent);

            // Act
            instanceListProvider.refreshInstances();

            // Assert that instances are available after refresh
            assertEquals(expectedInstances, instanceListProvider.getInstances());
        }
    }
}