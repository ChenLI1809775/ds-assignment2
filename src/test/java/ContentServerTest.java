
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.Scanner;


import static org.junit.jupiter.api.Assertions.*;

public class ContentServerTest {
    /**
     * Test read the command args
     */
    @Test
    public void testReadCommandArgs() {
        //illegal file path
        String[] args4 = new String[]{"localhost:4567", ""};
        assertThrows(IllegalArgumentException.class, () -> {
            new ContentServer(args4[0], args4[1]);
        });
        //illegal host
        String[] args3 = new String[]{"invalid", "src/test/resources/weatherData.json"};
        assertThrows(IllegalArgumentException.class, () -> {
            new ContentServer(args3[0], args3[1]);
        });

        //simulate the commandline
        String[] cmdArgs = {"http://127.0.0.1:5555", "src/test/resources/weatherData.json"};
        System.setIn(new ByteArrayInputStream(String.join(" ", cmdArgs).getBytes()));
        String[] args = new Scanner(System.in).nextLine().split(" ");
        ContentServer contentServer = new ContentServer(args[0], args[1]);
        assertEquals(5555, contentServer.port);
        assertEquals("127.0.0.1", contentServer.host);
        assertEquals("src/test/resources/weatherData.json", contentServer.weatherDataFilePath);

        //test the case where the port is specified
        String[] args2 = new String[]{"http://servername.domain.domain:4567", "src/test/resources/weatherData.json"};
        ContentServer contentServer2 = new ContentServer(args2[0], args2[1]);
        assertEquals(4567, contentServer2.port);
        assertEquals("servername.domain.domain", contentServer2.host);
        assertEquals("src/test/resources/weatherData.json", contentServer2.weatherDataFilePath);

    }
    /**
     * Test server start
     */
    @Test
    public void testServerStart() {
        int port = 4567;
        try (ServerSocket ignored = new ServerSocket(port)) {
            ContentServer contentServer = new ContentServer("http://127.0.0.1:" + port,
                    "src/test/resources/weatherData.json");
            Thread contentServerThread = new Thread(() -> {
                try {
                    contentServer.start(500);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            contentServerThread.start();
            //Ensure the server is running
            try (Socket clientSocket = ignored.accept()) {
                assertTrue(clientSocket.isConnected());
            }

            // stop the server
            contentServer.stop();
            contentServerThread.interrupt();
        } catch (IOException e) {
            e.printStackTrace();
            fail();
        }
    }

    @Test
    public void testServerStop() {
        int port = 4568;
        try (ServerSocket ignored = new ServerSocket(port)) {
            ContentServer contentServer = new ContentServer("http://127.0.0.1:" + port,
                    "src/test/resources/weatherData.json");
            contentServer.start(1000);
            contentServer.stop();
            assertFalse(contentServer.isRunning());
        } catch (IOException e) {
            e.printStackTrace();
            fail();
        }
    }
    /**
     * Test update weather data
     */
    @Test
    public void testUpdateWeatherData() {
        ContentServer contentServer = new ContentServer("http://127.0.0.1:4567",
                "src/test/resources/weatherData.json");
        WeatherData weatherData = contentServer.getWeatherData();
        assertEquals("IDS60901", weatherData.getId());
        assertEquals(13.3, weatherData.getAir_temp());
        assertEquals(9.5, weatherData.getApparent_t());
        assertEquals("15/04:00pm", weatherData.getLocal_date_time());
        assertEquals("20230715160000", weatherData.getLocal_date_time_full());
        WeatherData.updateWeatherData(weatherData, LocalDateTime.now(), new Random());
        assertNotEquals(13.3, weatherData.getAir_temp());
        assertNotEquals(9.5, weatherData.getApparent_t());
        assertNotEquals("15/04:00pm", weatherData.getLocal_date_time());
        assertNotEquals("20230715160000", weatherData.getLocal_date_time_full());
    }



    /**
     * count the number of line in the request
     */
    public int countSendLineCount(ServerSocket serverSocket) throws IOException {
        //test weather data put
        Socket clientSocket = serverSocket.accept();
        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        String line;
        int lineCount = 0;
        while ((line = in.readLine()) != null) {
            if (line.contains("PUT") || line.contains("User-Agent") ||
                    line.contains("Content-Type") || line.contains("Content-Length")) {
                lineCount += 1;
            }
            if (!in.ready()) {
                //avoid blocking
                break;
            }
        }
        return lineCount;
    }

    /**
     * test send weather data
     */
    @Test
    public void testSendData() {
        int port = 4569;
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            ContentServer contentServer = new ContentServer("http://127.0.0.1:" + port,
                    "src/test/resources/weatherData.json");
            contentServer.start(200);
            //check the number of line to determine if data has been sent successfully
            assertFalse(countSendLineCount(serverSocket) > 10);
            contentServer.stop();
        } catch (IOException e) {
            e.printStackTrace();
            fail();
        }
    }
}
