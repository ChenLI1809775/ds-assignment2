# Aggregation Server Application

## Prerequisites

- Java 17 or higher, installed and added to the PATH:
```bash
sudo apt update
sudo apt install openjdk-17-jdk -y
```

## Project Structure

```
src/
├── main/
│   ├── java/
│   │   ├── AggregationServer.java          # Aggregation Server implementation
│   │   ├── ContentServer.java              # Content Server implementation
│   │   ├── GETClient.java                  # GET Client implementation
│   │   ├── CustomJsonParser.java           # Custom JSON parser
│   │   ├── WeatherData.java                # Weather data model
│   │   ├── BaseResponse.java               # Base response class
│   │   ├── GETClientResponse.java          # GET client response model
│   │   ├── BaseRequestHandler.java         # Base request handler
│   │   ├── ContentServerRequestHandler.java # Content server request handler
│   │   ├── GetClientRequestHandler.java    # GET client request handler
│   │   ├── ErrorRequestHandler.java        # Error request handler
│   │   ├── ConnectionService.java          # Connection service
│   │   ├── IOService.java                  # I/O service
│   │   ├── ResponseService.java            # Response service
│   │   ├── LamportClock.java               # Lamport clock implementation
│   │   └── ds-assignment2/
│   └── resources/
│       └── weatherData.json                # Sample weather data
└── test/
    └── java/
        ├── AggregationServerTest.java      # Aggregation server unit tests
        ├── ContentServerTest.java          # Content server unit tests
        ├── GETClientTest.java              # GET client unit tests
        └── IntegrationTest.java            # Integration tests
```

## How to Compile, Run and Test

Note: Before running the commands below, first run `cd` to switch to the project folder that contains the `src` subdirectory.

### 1. Compile All Java Files

```bash
mkdir -p target/classes
javac  -cp "lib/*" -d target/classes src/main/java/*.java
```

### 2. Launch the Aggregation Server

Start the aggregation server on a specific port (e.g., port 8080):

```bash
java -cp "target/classes:lib/*" AggregationServer 8080
```

You should see the message "Aggregation Server started on port 8080" when the server starts successfully.

### 3. Run Content Servers and GET Client

In a new terminal, run one or more content servers to provide weather data:

```bash
java -cp "target/classes:lib/*" ContentServer localhost:8080 src/test/resources/weatherData.json
```

In another terminal, run the GET client to retrieve weather data:

```bash
java -cp "target/classes:lib/*" GETClient localhost:8080 IDS60901
```

The client will perform the following operations:
```
Testing Aggregation Server application...
1. Connecting to Aggregation Server at localhost:8080
2. Sending GET request for weather data
3. Received response with weather data
4. Displaying weather information:
   id: 12345
   name: Sydney
   air_temp: 22.3
   apparent_t: 23.1
   cloud: 50
   dewpt: 10.2
   press: 1015.3
   rel_hum: 65
   wind_dir: N
   wind_spd_kmh: 15
   wind_spd_kt: 8
5. Client test completed.
```

### 4. Run Unit Tests

First, compile the test files:

```bash
mkdir -p target/classes 
mkdir -p target/test-classes
javac -cp "lib/*" \
      -d target/classes \
      $(find src/main/java -name "*.java")
javac -cp "target/classes:lib/*" \
      -d target/test-classes \
      $(find src/test/java -name "*.java")
```

Run the automated tests:

```bash
java -cp "lib/*:target/classes:target/test-classes" \
     org.junit.platform.console.ConsoleLauncher \
     --scan-classpath
```

The unit tests include single-server and multi-server simulation scenarios:

1. Single or multiple content servers pushing weather data concurrently
2. Single or multiple clients performing GET requests
3. Server data caching and timeout functionality
4. HTTP request parsing and response generation
5. Data aggregation from multiple sources
6. Lamport clock synchronization