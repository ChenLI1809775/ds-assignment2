import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class IntegrationTest {
    /**
     * Test if server will be blocked when put data
     */
    @Test
    public void testPutWithMultipleContentServer() {
        String dataCachePath = "src/test/resources/testPutWithMultipleContentServer.json";
        int port = 4567;
        AggregationServer aggregationServer = new AggregationServer(port, dataCachePath);
        aggregationServer.start();
        ArrayList<ContentServer> contentServers = new ArrayList<>();
        String serverURL = String.format("127.0.0.1:%d", aggregationServer.getPort());

        // Create 5 content servers with different data files
        for (int i = 0; i < 5; i++) {
            ContentServer contentServer = new ContentServer(serverURL,
                    String.format("src/test/resources/integration_test/%d.json", i + 1));
            contentServers.add(contentServer);
        }

        // Use ExecutorService to run 5 content servers simultaneously
        ExecutorService executor = Executors.newFixedThreadPool(5);
        for (ContentServer contentServer : contentServers) {
            executor.submit(() -> {
                BaseResponse baseResponse = contentServer.sendWeatherData();
                assertNotNull(baseResponse);
                assertTrue(BaseResponse.STATUS_CODE_SUCCESS <= baseResponse.getStatusCode()
                        && baseResponse.getStatusCode() <= BaseResponse.STATUS_CODE_CREATED);
            });
        }
        executor.shutdown();

        try {
            // Wait for all tasks to complete
            executor.awaitTermination(30, TimeUnit.SECONDS);
            //Check if data has been written correctly
            File file = new File(dataCachePath);
            String content = new String(Files.readAllBytes(Paths.get(file.getPath())));
            Type listType = new TypeToken<ArrayList<WeatherData>>() {
            }.getType();
            Gson gson = new Gson();
            ArrayList<WeatherData> weatherDataList = gson.fromJson(content, listType);
            assertEquals(5, weatherDataList.size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        aggregationServer.stop();
    }

    /**
     * Test if server will be blocked when get data
     */
    @Test
    public void testGetWithMultipleClient() {
        String dataCachePath = "src/test/resources/testGetWithMultipleClient.json";
        int port = 4568;
        AggregationServer aggregationServer = new AggregationServer(port, dataCachePath);
        aggregationServer.start();
        String serverURL = String.format("localhost:%d", aggregationServer.getPort());
        for (int i = 0; i < 5; i++) {
            ContentServer contentServer = new ContentServer(serverURL,
                    String.format("src/test/resources/integration_test/%d.json", i + 1));
            //put data automatically
            contentServer.sendWeatherData();
        }

        String idBase = "IDS6090";
        // Create a list to hold 10 GET clients
        ArrayList<GETClient> clients = new ArrayList<>();

        // Initialize 10 GET clients with the same server URL
        for (int i = 0; i < 10; i++) {
            GETClient getClient = new GETClient(serverURL);
            clients.add(getClient);
        }

        // Use ExecutorService to simulate concurrent GET requests
        ExecutorService executor = Executors.newFixedThreadPool(5);
        for (int i = 0; i < 5; i++) {
            final int index = i;
            executor.submit(() -> {
                // Each client sends a GET request with a unique ID
                WeatherData weatherData = clients.get(index)
                        .getWeatherData(String.format("%s%d", idBase, index + 1));
                assertNotNull(weatherData);
                // Add additional assertions as needed
            });
        }
        executor.shutdown();

        try {
            // Wait for all requests to complete (with timeout)
            executor.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Test interrupted");
        }
    }

    /**
     * Test Lamport Clock Sequence
     */
    @Test
    public void testLamportClockSequence() {
        String dataCachePath = "src/test/resources/testLamportClockSequence.json";
        File file = new File(dataCachePath);
        if (file.exists()){
            file.delete();
        }
        int port = 4569;
        AggregationServer aggregationServer = new AggregationServer(port, dataCachePath);
        aggregationServer.start();

        String serverURL = String.format("localhost:%d", aggregationServer.getPort());

        // Create two ContentServers
        ContentServer contentServer1 = new ContentServer(serverURL,
                "src/test/resources/integration_test/1.json");
        ContentServer contentServer2 = new ContentServer(serverURL,
                "src/test/resources/integration_test/2.json");

        // Create one GETClient
        GETClient getClient = new GETClient(serverURL);

        // First PUT request from ContentServer1
        BaseResponse response1 = contentServer1.sendWeatherData();
        assertEquals(BaseResponse.STATUS_CODE_CREATED, response1.getStatusCode());
        int clockAfterFirstPut = contentServer1.getLamportClock();

        // GET request from GETClient
        WeatherData weatherData = getClient.getWeatherData("IDS60901");
        assertNotNull(weatherData);
        assertEquals(weatherData.getId(), "IDS60901");
        int clockAfterGet = getClient.getLamportClock(); // Assuming there's a method to get last clock value

        // Second PUT request from ContentServer2
        BaseResponse response2 = contentServer2.sendWeatherData();
        assertEquals(BaseResponse.STATUS_CODE_SUCCESS, response2.getStatusCode());
        int clockAfterSecondPut = contentServer2.getLamportClock();
        // Verify that Lamport clocks are incrementing
        assertTrue(clockAfterGet > clockAfterFirstPut,
                "Lamport clock after GET should be greater than after first PUT");
        assertTrue(clockAfterSecondPut > clockAfterGet,
                "Lamport clock after second PUT should be greater than after GET");

        aggregationServer.stop();


    }
}
