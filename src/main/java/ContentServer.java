import com.google.gson.Gson;

import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;


public class ContentServer {
    public final int port;
    //host and port of AggregationServer
    public final String host, weatherDataFilePath;
    //Lamport clock
    private final LamportClock lamportClock;
    //Timer that sends data asynchronously
    private Timer timer;

    //weather data this content server maintained
    private final WeatherData weatherData;
    private final Gson gson;


    public ContentServer(String serverURL, String weatherDataFilePath) {
        //check args
        String temp = serverURL;
        if (serverURL.contains("http://")) {
            temp = serverURL.replace("http://", "");
        } else if (serverURL.contains("https://")) {
            temp = serverURL.replace("https://", "");
        }
        String[] tempSplit = temp.split(":");
        if (tempSplit.length < 2) {
            throw new IllegalArgumentException("The first arg (serverURL) is illegal. Format should be host:port");
        }
        // Validate host and port
        String hostPart = tempSplit[0].trim();
        if (hostPart.isEmpty()) {
            throw new IllegalArgumentException("Host cannot be empty");
        }

        try {
            port = Integer.parseInt(tempSplit[1]);
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Port must be between 1 and 65535");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid port number: " + tempSplit[1]);
        }

        File file = new File(weatherDataFilePath);
        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("The second arg (weatherDataFilePath) is illegal: file does not exist or is not a file");
        }

        if (!file.canRead()) {
            throw new IllegalArgumentException("Cannot read weather data file: " + weatherDataFilePath);
        }

        // Initialize final fields
        this.host = hostPart;
        this.weatherDataFilePath = weatherDataFilePath;
        this.gson = new Gson();
        // Load weather data with proper resource management
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            this.weatherData = gson.fromJson(br, WeatherData.class);

            if (this.weatherData == null) {
                throw new RuntimeException(String.format("Data file (%s) is empty or contains invalid JSON", weatherDataFilePath));
            }

            // Validate that weather data has an ID
            if (this.weatherData.getId() == null || this.weatherData.getId().isEmpty()) {
                throw new RuntimeException(String.format("Weather data in file (%s) must have a valid ID", weatherDataFilePath));
            }
        } catch (IOException e) {
            throw new RuntimeException(String.format("Failed to read data file (%s): %s", weatherDataFilePath, e.getMessage()), e);
        } catch (com.google.gson.JsonSyntaxException e) {
            throw new RuntimeException(String.format("Data file (%s) contains invalid JSON: %s", weatherDataFilePath, e.getMessage()), e);
        }
        this.lamportClock = new LamportClock();
    }

    /**
     * TimerTask for sending data
     */
    private static class DataSentTask extends TimerTask {
        private final ContentServer contentServer;

        public DataSentTask(ContentServer contentServer) {
            this.contentServer = contentServer;
        }

        @Override
        public void run() {
            if (contentServer.isRunning()) {
                contentServer.sendWeatherData();
            }
        }
    }

    /**
     * start server
     *
     * @param interval data update interval, in unit ms, default is 1000ms
     * @throws IOException if an I/O error occurs when starting the server
     */
    public void start(int interval) throws IOException {
        if (interval < 0) {
            //By default, data is sent every 1s
            interval = 1000;
        }
        timer = new Timer();
        timer.schedule(new DataSentTask(this), 0, interval);
        System.out.printf("Content server id=%s started at port=%s%n", weatherData.getId(), port);
    }

    /**
     * send data to server
     */
    public BaseResponse sendWeatherData() {
        //update Lamport clock
        try (Socket socket = new Socket(host, port)) {
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            //update weather data before return
            WeatherData.updateWeatherData(weatherData, LocalDateTime.now(), new Random());
            String jsonWeatherData = gson.toJson(weatherData);
            String sb = String.format("PUT %s HTTP/1.1\n", weatherDataFilePath) +
                    String.format("User-Agent: ContentServer/1/0 %s %d\n",
                            weatherData.getId(), lamportClock.getTime()) +
                    "Content-Type: application/json\n" +
                    String.format("Content-Length: %d\n", jsonWeatherData.length()) +
                    jsonWeatherData + "\n";
            writer.println(sb);
            writer.flush();
            //update local Lamport clock
            lamportClock.tick();
            //read response from server
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
                if (!reader.ready()) {
                    break;
                }
            }
            BaseResponse contentResponse = gson.fromJson(response.toString(), BaseResponse.class);
            System.out.printf("ContentServer id=%s: from server, code=%d, message=%s%n", weatherData.getId(),
                    contentResponse.getStatusCode(), contentResponse.getMsg());
            //update local lamport clock with remote clock
            lamportClock.updateTime(contentResponse.getLamportClock());
            return contentResponse;
        } catch (IOException e) {
            System.out.printf("ContentServer: send data failed! cause: %s%n", e.getMessage());
            return null;
        }

    }

    /**
     * check if it is running
     *
     * @return true is running
     */
    public boolean isRunning() {
        return !Objects.isNull(timer);
    }

    /**
     * stop server
     */
    public void stop() {
        if (!Objects.equals(timer, null)) {
            //stop timer
            timer.cancel();
            timer = null;
        }
        System.out.printf("ContentServer id=%s stop%n", weatherData.getId());
    }

    /**
     * load json data from file
     *
     * @return WeatherData
     */
    public WeatherData getWeatherData() {
        return weatherData;
    }


    public static void main(String[] args) throws IOException {
        if (args.length > 0) {
            String hostPort = args[0];
            String weatherDataFilePath = args[1];
            new ContentServer(hostPort, weatherDataFilePath).start(-1);
        } else {
            //Illegal arguments, cancel start up
            throw new IllegalArgumentException("Illegal arguments," +
                    " the first is the server name and port number (as for GET) and" +
                    "the second is the location of a file in the file system local");
        }
    }
}
