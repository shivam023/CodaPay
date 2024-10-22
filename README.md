# Round Robin Load Balancer Application

## Overview

This application implements a round-robin load balancer with circuit breaker functionality. It distributes incoming requests across multiple service instances and handles failures gracefully.

## Prerequisites

- Java 17 or higher
- Maven
- Spring Boot

## Getting Started

### Clone the Repository

```bash
git clone https://github.com/shivam023/CodaPay.git
cd CodaPay
```

## Build the Application

### Open application project in one window
```bash
cd CodaPay/application
mvn clean install
```
### Use the following commands in your startInstance.bash script to start the application instances:
```bash
java -jar target/application-api-0.0.1-SNAPSHOT.jar --spring.profiles.active=instance2 &
java -jar target/application-api-0.0.1-SNAPSHOT.jar --spring.profiles.active=instance3 &
```

Instance2 is on port 8082 and Instance3 is on 8083

### Open roundrobin project in another window
```bash
cd Codapay/roundrobin
mvn clean install
```

Run the RoundRobinApplication.class and the server will be available on port 8080

## Circuit Breaking Logic

The circuit breaker prevents continuous attempts to call an instance that has failed or is slow to respond:

	1. Failure Detection: If a request fails or times out, the instance is marked as failed and added to the circuit breaker.
	2. Circuit State: Failed instances enter a “circuit open” state for a defined period (e.g., 10 seconds) during which they won’t receive requests. After the cooldown, they can be retried.

Testing the Application

	1. Start the Application: Run the Application class of the Round Robin project.
	2. Start Service Instances: Execute the startInstance.bash script.

Test Scenarios

	1. Connection Refused: Stop one instance and send a request to the load balancer. It should retry with the next instance. (8-81 port is never up so this case will be tested automatically)
	2. Delayed Response: Use the /api/delayed endpoint to simulate a slow instance. The load balancer should mark it as failed after exceeding the timeout.
	3. Normal Operation: Test the /api/normal endpoint to observe standard behavior.

Conclusion

This application showcases effective load balancing with a circuit breaker for enhanced resilience. For questions or issues, please contact the repository owner or raise an issue on GitHub.
