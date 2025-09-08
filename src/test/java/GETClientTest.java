import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.lang.reflect.Type;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

public class GETClientTest {

    @Test
    public void testCommandArgs() {
        String[] cmdArgs = {"http://127.0.0.1:4567"};
        System.setIn(new ByteArrayInputStream(String.join(" ", cmdArgs).getBytes()));
        String[] args = new Scanner(System.in).nextLine().split(" ");
        GETClient getClient = new GETClient(args[0]);
        assertEquals(4567, getClient.serverPort);
        assertEquals("127.0.0.1", getClient.serverHost);

        String[] args2 = new String[]{"http://127.0.0.1:4568"};
        GETClient getClient1 = new GETClient(args2[0]);
        assertEquals(4568, getClient1.serverPort);
        assertEquals("127.0.0.1", getClient1.serverHost);

        //illegal host
        String[] args3 = new String[]{"invalidHost"};
        assertThrows(IllegalArgumentException.class, () -> {
            new GETClient(args3[0]);
        });
    }

    /**
     * test client start and connect to server
     */
    @Test
    public void testClientStart() {
        try (ServerSocket ignored = new ServerSocket(4567)) {
            new GETClient("127.0.0.1:4567");
        } catch (IOException e) {
            e.printStackTrace();
            fail();
        }
    }


    /**
     * test get data from server
     */
    @Test
    public void testGetWeatherData() throws IOException {
        WeatherData weatherData = null;
        try {
            Gson gson = new Gson();
            // read json from file
            BufferedReader br = new BufferedReader(new FileReader("src/test/resources/weatherData.json"));
            // parse json array to get first weatherData
            Type listType = new TypeToken<ArrayList<WeatherData>>() {
            }.getType();
            ArrayList<WeatherData> weatherDataList = gson.fromJson(br, listType);
            br.close();

            if (weatherDataList != null && !weatherDataList.isEmpty()) {
                // get first weatherData
                weatherData = weatherDataList.get(0);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        assertNotNull(weatherData);
        try (ServerSocket serverSocket = new ServerSocket(4567)) {
            GETClient client = new GETClient("127.0.0.1:4567");
            //simulate server feedback
            GETClientResponse response = new GETClientResponse();
            response.setStatusCode(BaseResponse.STATUS_CODE_SUCCESS);
            response.setMsg(BaseResponse.MSG_OK);
            response.setWeatherData(weatherData);
            //simulate the server
            new Thread(() -> {
                Socket socket;
                try {
                    socket = serverSocket.accept();
                    PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()),
                            true);
                    out.println(new Gson().toJson(response));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).start();
            WeatherData weatherDataResponse = client.getWeatherData(weatherData.getId());
            //check response
            assertEquals(weatherData.getId(), weatherDataResponse.getId());
        }
    }

    @Test
    public void testDisplayWeatherData() {

        String jsonStr = """
                {
                  "id": "IDS60901",
                  "name": "Adelaide (West Terrace / ngayirdapira)",
                  "state": "SA",
                  "time_zone": "CST",
                  "lat": -34.9,
                  "lon": 138.6,
                  "local_date_time": "15/04:00pm",
                  "local_date_time_full": "20230715160000",
                  "air_temp": 13.3,
                  "apparent_t": 9.5,
                  "cloud": "Partly cloudy",
                  "dewpt": 5.7,
                  "press": 1023.9,
                  "rel_hum": 60,
                  "wind_dir": "S",
                  "wind_spd_kmh": 15,
                  "wind_spd_kt": 8
                }""";
        Gson gson = new Gson();
        WeatherData weatherData = gson.fromJson(jsonStr, WeatherData.class);
        GETClient getClient = new GETClient("127.0.0.1:4567");
        String formattedJson = getClient.printWeatherData(weatherData);
        assertEquals(jsonStr, formattedJson);
    }

}
