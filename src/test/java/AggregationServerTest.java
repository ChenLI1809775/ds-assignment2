import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.lang.reflect.Type;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Random;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

public class AggregationServerTest {
    public static final int TYPE_NOT_ALLOWED = 0;
    public static final int TYPE_PUT_NO_CONTENT = 1;
    public static final int TYPE_PUT_VALID_CONTENT = 2;
    public static final int TYPE_PUT_INVALID_CONTENT = 3;
    public static final int TYPE_GET_VALID_CONTENT = 4;
    public static final int TYPE_GET_INVALID_CONTENT = 5;

    private static WeatherData weatherData;
    public static String defaultTestDataCachePath = "src/test/resources/weatherData.json";

    @BeforeAll
    public static void setUp() {
        try {
            // read json from file
            String content = Files.readString(Paths.get(defaultTestDataCachePath));
            CustomJsonParser jsonParser = new CustomJsonParser();
            ArrayList<WeatherData> weatherDataList = jsonParser.parse(content, ArrayList.class, WeatherData.class);
            if (weatherDataList != null && !weatherDataList.isEmpty()) {
                // get first weatherData
                weatherData = weatherDataList.get(0);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * create request data
     *
     * @param type        test type
     * @param weatherData weatherData
     * @return request data
     */
    private String createRequestData(int type, WeatherData weatherData) {
        final String endpoint = "demo.json";
        final String protocol = "STATUS_CODE/1.1";

        switch (type) {
            case TYPE_NOT_ALLOWED:
                //unknown header
                return String.format("Unknown %s %s\n", endpoint, protocol);

            case TYPE_PUT_NO_CONTENT:
                // Valid PUT header without data
                return String.format("PUT %s %s\n", endpoint, protocol) +
                        String.format("User-Agent: ContentServer/1/0 %s %d\n", weatherData.getId(), 1) +
                        "Content-Type: application/json\n" +
                        String.format("Content-Length: %d\n", 100);

            case TYPE_PUT_VALID_CONTENT: {
                //Valid PUT header with data
                CustomJsonParser customJsonParser = new CustomJsonParser();
                String jsonWeatherData = customJsonParser.stringify(weatherData);
                return String.format("PUT %s %s\n", endpoint, protocol) +
                        String.format("User-Agent: ContentServer/1/0 %s %d\n", weatherData.getId(), 1) +
                        "Content-Type: application/json\n" +
                        String.format("Content-Length: %d\n", jsonWeatherData.length()) +
                        jsonWeatherData;
            }

            case TYPE_PUT_INVALID_CONTENT: {
                //Valid PUT header with invalid data
                String invalidData = "{1,2,3}";
                return String.format("PUT %s %s\n", endpoint, protocol) +
                        String.format("User-Agent: ContentServer/1/0 %s %d\n", "invalid", 1) +
                        "Content-Type: application/json\n" +
                        String.format("Content-Length: %d\n", invalidData.length()) +
                        invalidData;
            }

            case TYPE_GET_VALID_CONTENT:
                //Valid GET header
                return String.format("GET %s\n", protocol) +
                        String.format("User-Agent: ContentServer/1/0 %s %d\n", weatherData.getId(), 1);

            case TYPE_GET_INVALID_CONTENT:
                //Invalid GET header
                return String.format("GET %s\n", protocol) +
                        String.format("User-Agent: ContentServer/1/0 %s %d\n", "11111", 1);

            default:
                throw new IllegalArgumentException("Unknown type: " + type);
        }
    }


    /**
     * Mock request for testing AggregationServer
     *
     * @param requestData request data
     * @param port        server port
     * @return GETClientResponse
     */
    public GETClientResponse mockRequest(String requestData, int port) {
        try (Socket clientSocket = new Socket("127.0.0.1", port)) {
            PrintWriter clientWriter = new PrintWriter(clientSocket.getOutputStream(), true);
            BufferedReader clientReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            //send data
            clientWriter.println(requestData);
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = clientReader.readLine()) != null) {
                response.append(line);
                if (!clientReader.ready()) {
                    break;
                }
            }
            Gson gson = new Gson();
            //Must close socket immediately
            clientSocket.close();
            return gson.fromJson(response.toString(), GETClientResponse.class);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * test command args
     */
    @Test
    public void testCommandArgs() {
        //simulate the commandline
        String[] cmdArgs = {String.valueOf(7777)};
        System.setIn(new ByteArrayInputStream(String.join(" ", cmdArgs).getBytes()));
        String[] args = new Scanner(System.in).nextLine().split(" ");
        AggregationServer aggregationServer = new AggregationServer(Integer.parseInt(args[0]),
                defaultTestDataCachePath);
        assertEquals(7777, aggregationServer.getPort());
    }

    /**
     * test start
     */
    @Test
    public void testStart() {
        AggregationServer aggregationServer = new AggregationServer(7778, defaultTestDataCachePath);
        aggregationServer.start();
        aggregationServer.stop();
    }

    /**
     * test not allow http method
     */
    @Test
    public void testNotAllowMethod() {
        int port = 7777;
        AggregationServer server = new AggregationServer(port, defaultTestDataCachePath);
        server.start();
        String requestData = createRequestData(TYPE_NOT_ALLOWED, weatherData);
        GETClientResponse getClientResponse = mockRequest(requestData, port);
        assertEquals(BaseResponse.STATUS_CODE_FORBIDDEN, getClientResponse.getStatusCode());
        server.stop();
    }

    /**
     * test PUT but no content
     */
    @Test
    public void testPutNoContent() {
        int port = 7778;
        AggregationServer server = new AggregationServer(port, defaultTestDataCachePath);
        server.start();
        String requestData = createRequestData(TYPE_PUT_NO_CONTENT, weatherData);
        BaseResponse baseResponse = mockRequest(requestData, port);
        assertEquals(BaseResponse.STATUS_CODE_NO_CONTENT, baseResponse.getStatusCode());
        server.stop();
    }

    /**
     * Test Multiple PUT
     */
    @Test
    public void testMultiplePut() throws IOException, InterruptedException {
        int port = 7779;
        String cacheFilePath = "src/test/resources/testMultiplePut.json";
        //remove old weather data cache file
        File file = new File(cacheFilePath);
        if (file.exists()) {
            boolean delete = file.delete();
        }
        AggregationServer server = new AggregationServer(port, cacheFilePath);
        server.start();

        //Change AggregationServer cache file path
        String requestData = createRequestData(TYPE_PUT_VALID_CONTENT, weatherData);
        //First data put
        BaseResponse baseResponse = mockRequest(requestData, port);
        assertEquals(BaseResponse.STATUS_CODE_CREATED, baseResponse.getStatusCode());

        Gson gson = new Gson();
        //Second data put
        WeatherData weatherData1 = gson.fromJson(gson.toJson(weatherData), WeatherData.class);
        weatherData1.setId("IDS60903");
        WeatherData.updateWeatherData(weatherData1, LocalDateTime.now(), new Random());
        String requestData1 = createRequestData(TYPE_PUT_VALID_CONTENT, weatherData1);
        BaseResponse baseResponse1 = mockRequest(requestData1, port);
        assertEquals(BaseResponse.STATUS_CODE_SUCCESS, baseResponse1.getStatusCode());

        Thread.sleep(5);
        //check if data has been written correctly
        String content = new String(Files.readAllBytes(Paths.get(file.getPath())));
        Type listType = new TypeToken<ArrayList<WeatherData>>() {
        }.getType();
        ArrayList<WeatherData> weatherDataList = gson.fromJson(content, listType);
        assertNotNull(weatherDataList);
        assertEquals(2, weatherDataList.size());

        server.stop();
    }

    /**
     * test put request with invalid json
     */
    @Test
    public void testPutWithInvalidData() {
        int port = 7780;
        AggregationServer server = new AggregationServer(port, defaultTestDataCachePath);
        server.start();
        String requestData = createRequestData(TYPE_PUT_INVALID_CONTENT, weatherData);
        BaseResponse baseResponse = mockRequest(requestData, port);
        assertEquals(BaseResponse.STATUS_CODE_FORBIDDEN, baseResponse.getStatusCode());
        server.stop();
    }

    /**
     * Test get data with valid request data from GETClient
     */
    @Test
    public void testGetWithValidRequestData() {
        int port = 7781;
        AggregationServer server = new AggregationServer(port, defaultTestDataCachePath);
        server.start();
        String putRequestData = createRequestData(TYPE_PUT_VALID_CONTENT, weatherData);
        //put data to server
        GETClientResponse putResponse = mockRequest(putRequestData, port);
        assertTrue(200 <= putResponse.getStatusCode() && putResponse.getStatusCode() <= 201);
        //get data from server
        String getRequestData = createRequestData(TYPE_GET_VALID_CONTENT, weatherData);
        GETClientResponse getClientResponse = mockRequest(getRequestData, port);
        assertEquals(BaseResponse.STATUS_CODE_SUCCESS, getClientResponse.getStatusCode());
        assertNotNull(getClientResponse.getWeatherData());
        assertEquals(getClientResponse.getWeatherData().getId(), weatherData.getId());
        server.stop();
    }

    /**
     * Test get data with invalid request data from GETClient
     */
    @Test
    public void testGetWithInValidRequestData() {
        int port = 7782;
        AggregationServer server = new AggregationServer(port, defaultTestDataCachePath);
        server.start();
        //get data from server
        String getRequestData = createRequestData(TYPE_GET_INVALID_CONTENT, weatherData);
        GETClientResponse getResponse = mockRequest(getRequestData, port);
        assertEquals(BaseResponse.STATUS_CODE_NOT_FOUND, getResponse.getStatusCode());

        server.stop();
    }


    @Test
    public void testCleanOutdated() throws IOException, InterruptedException {
        String cacheFilePath = "src/test/resources/testCleanOutdated.json";
        int port = 7783;
        File file = new File(cacheFilePath);
        if (file.exists()) {
            boolean delete = file.delete();
        }
        AggregationServer server = new AggregationServer(port, cacheFilePath);
        server.setDataCachePath(file.getPath());
        server.start();

        double delayMs = 4000;
        // change max update interval for content server to 5s to speed up this test
        server.setMaxUpdateInterval(delayMs / 1000);

        //create and put new data 1
        Gson gson = new Gson();
        WeatherData weatherData1 = gson.fromJson(gson.toJson(weatherData), WeatherData.class);
        weatherData1.setId("IDS60903");
        String requestData1 = createRequestData(TYPE_PUT_VALID_CONTENT, weatherData1);
        BaseResponse baseResponse1 = mockRequest(requestData1, port);
        assertEquals(BaseResponse.STATUS_CODE_CREATED, baseResponse1.getStatusCode());

        //put new data2
        WeatherData weatherData2 = gson.fromJson(gson.toJson(weatherData), WeatherData.class);
        weatherData2.setId("IDS60904");
        String requestData2 = createRequestData(TYPE_PUT_VALID_CONTENT, weatherData2);
        BaseResponse baseResponse2 = mockRequest(requestData2, port);
        assertEquals(BaseResponse.STATUS_CODE_SUCCESS, baseResponse2.getStatusCode());

        Thread.sleep(1000);
        //Check if data has been written correctly
        String content = new String(Files.readAllBytes(Paths.get(file.getPath())));
        Type listType = new TypeToken<ArrayList<WeatherData>>() {
        }.getType();
        ArrayList<WeatherData> weatherDataList = gson.fromJson(content, listType);
        assertEquals(2, weatherDataList.size());

        //Ensure data can be  retrieved
        String getRequestData1 = createRequestData(TYPE_GET_VALID_CONTENT, weatherData1);
        GETClientResponse getResponse1 = mockRequest(getRequestData1, port);
        assertEquals(weatherData1.getId(), getResponse1.getWeatherData().getId());
        assertEquals(BaseResponse.STATUS_CODE_SUCCESS, getResponse1.getStatusCode());

        String getRequestData2 = createRequestData(TYPE_GET_VALID_CONTENT, weatherData2);
        GETClientResponse getResponse2 = mockRequest(getRequestData2, port);
        assertEquals(weatherData2.getId(), getResponse2.getWeatherData().getId());
        assertEquals(BaseResponse.STATUS_CODE_SUCCESS, getResponse2.getStatusCode());

        //wait it outdated
        Thread.sleep(10000);
        //expect the data 1 has been removed
        GETClientResponse getResponse = mockRequest(getRequestData1, port);
        assertEquals(BaseResponse.STATUS_CODE_NOT_FOUND, getResponse.getStatusCode());

        //expect the data 2 has been removed
        getRequestData2 = createRequestData(TYPE_GET_VALID_CONTENT, weatherData2);
        getResponse2 = mockRequest(getRequestData2, port);
        assertEquals(BaseResponse.STATUS_CODE_NOT_FOUND, getResponse2.getStatusCode());

        //Check if data has been deleted correctly
        String content2 = new String(Files.readAllBytes(Paths.get(file.getPath())));
        Type listType2 = new TypeToken<ArrayList<WeatherData>>() {
        }.getType();
        ArrayList<WeatherData> weatherDataList2 = gson.fromJson(content2, listType2);
        assertEquals(0, weatherDataList2.size());

        server.stop();
    }

    /**
     * test the update of the lamport clock
     */
    @Test
    public void testDataUpdateAndLamportClock() {
        String cacheFilePath = "src/test/resources/testDataUpdateAndLamportClock.json";
        int port = 7784;
        File file = new File(cacheFilePath);
        if (file.exists()) {
            file.delete();
        }
        AggregationServer server = new AggregationServer(port, cacheFilePath);
        server.start();
        //Send the first PUT
        Gson gson = new Gson();
        WeatherData weatherData1 = gson.fromJson(gson.toJson(weatherData), WeatherData.class);
        weatherData1.setId("IDS60903");
        String requestData1 = createRequestData(TYPE_PUT_VALID_CONTENT, weatherData1);
        BaseResponse simpleResponse1 = mockRequest(requestData1, port);
        assertEquals(BaseResponse.STATUS_CODE_CREATED, simpleResponse1.getStatusCode());

        //send a GET to check if data has been updated
        String getRequestData = createRequestData(TYPE_GET_VALID_CONTENT, weatherData1);
        GETClientResponse getResponse = mockRequest(getRequestData, port);
        assertNotNull(getResponse.getWeatherData());
        assertEquals(weatherData1.getId(), getResponse.getWeatherData().getId());
        assertEquals(weatherData1.getName(), getResponse.getWeatherData().getName());

        //mock data update
        WeatherData.updateWeatherData(weatherData1, LocalDateTime.now(), new Random());
        //send the second PUT to update data
        String requestData2 = createRequestData(TYPE_PUT_VALID_CONTENT, weatherData1);
        BaseResponse simpleResponse2 = mockRequest(requestData2, port);
        assertEquals(BaseResponse.STATUS_CODE_SUCCESS, simpleResponse2.getStatusCode());

        //send the last GET to check if data has been updated
        String getRequestData2 = createRequestData(TYPE_GET_VALID_CONTENT, weatherData1);
        GETClientResponse getResponse2 = mockRequest(getRequestData2, port);
        assertNotNull(getResponse2.getWeatherData());
        assertEquals(weatherData1.getId(), getResponse2.getWeatherData().getId());
        assertEquals(weatherData1.getLat(), getResponse2.getWeatherData().getLat());

        server.stop();
    }
}
