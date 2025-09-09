import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class GETClient {
    public final String serverHost;
    public final int serverPort;
    private final LamportClock lamportClock;

    public GETClient(String serverURL) {
        String temp = serverURL;
        if (serverURL.startsWith("http://")) {
            temp = serverURL.substring(7);
        } else if (serverURL.startsWith("https://")) {
            temp = serverURL.substring(8);
        }
        String[] parts = temp.split(":", 2);
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid server URL format. Expected format: host:port");
        }
        serverHost = String.format("%s", parts[0]);
        try {
            this.serverPort = Integer.parseInt(parts[1]);
            if (this.serverPort < 1 || this.serverPort > 65535) {
                throw new IllegalArgumentException("Port must be between 1 and 65535");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid port number: " + parts[1]);
        }
        lamportClock = new LamportClock();
    }

    /**
     * get local lamport clock
     *
     * @return local lamport clock
     */
    public int getLamportClock() {
        return lamportClock.getTime();
    }

    /**
     * display weather data
     *
     * @param weatherData String, formatted json to display
     * @return formatted weather data
     */
    public String printWeatherData(WeatherData weatherData) {
//        Gson gson = new GsonBuilder().setPrettyPrinting().create();
//        String formattedJson = gson.toJson(weatherData);
        CustomJsonParser jsonParser = new CustomJsonParser();
        String formattedJson = jsonParser.stringifyPretty(weatherData);
        System.out.println(formattedJson);
        return formattedJson;
    }

    /**
     * get weather data from server
     *
     * @return weather data
     */
    public WeatherData getWeatherData(String weatherDataID) {
        try (Socket socket = new Socket(serverHost, serverPort)) {
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String sb = "GET HTTP/1.1\n" +
                    String.format("User-Agent: ContentServer/1/0 %s %d\n",
                            weatherDataID, lamportClock.getTime());
            writer.println(sb);
            writer.flush();
            //update local Lamport clock
            lamportClock.tick();
            StringBuilder jsonResponse = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonResponse.append(line);
                if (!reader.ready()) {
                    break;
                }
            }

            CustomJsonParser customJsonParser = new CustomJsonParser();
            GETClientResponse response = customJsonParser.parse(jsonResponse.toString(), GETClientResponse.class);
            if (response.getStatusCode() == BaseResponse.STATUS_CODE_SUCCESS) {
                //Display weather data
                //printWeatherData(response.getWeatherData());
                //Update local lamport clock with remote clock
                lamportClock.updateTime(response.getLamportClock());
            } else {
                throw new IOException(response.getMsg());
            }
            return response.getWeatherData();
        } catch (IOException e) {
            System.out.printf("GETClient: get data failed, cause:%s%n", e.getMessage());
            return null;
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java GETClient <server_url> <weather_data_id>");
            System.exit(1);
            return;
        }

        String serverUrl = args[0];
        String weatherDataId = args[1];

        try {
            GETClient client = new GETClient(serverUrl);
            System.out.println("GET client started, connecting to " + serverUrl);

            WeatherData weatherData = client.getWeatherData(weatherDataId);
            if (weatherData != null) {
                client.printWeatherData(weatherData);
                System.exit(0);
            } else {
                System.err.println("Failed to retrieve weather data");
                System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("Error starting GET client: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
