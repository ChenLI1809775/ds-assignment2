import java.io.IOException;
import java.nio.channels.SocketChannel;

public class ContentServerRequestHandler extends BaseRequestHandler {

    private final String serverID;
    //Latest system time when the data is modified
    private long modDate;
    private WeatherData weatherData;

    public ContentServerRequestHandler(SocketChannel socketChannel, WeatherData weatherData,
                                       String serverID, int lamportClock) {
        super(lamportClock, socketChannel);
        this.serverID = serverID;
        this.weatherData = weatherData;
        this.modDate = System.currentTimeMillis();
    }

    /**
     * Refresh the modDate
     */
    public void refreshModDate() {
        modDate = System.currentTimeMillis();
    }

    public String getServerID() {
        return serverID;
    }

    public WeatherData getWeatherData() {
        return weatherData;
    }

    public void setWeatherData(WeatherData weatherData) {
        this.weatherData = weatherData;
    }

    @Override
    public void response(BaseResponse baseResponse) throws IOException {
        super.response(baseResponse);
        //clear old data
        weatherData = null;
    }

    /**
     * Check if this handler is  outdated because the content server is not updated for a long time
     *
     * @param maxUpdateInterval the max update interval to judge when the
     *                         content server is not updated for a long time
     * @return true if this handler is outdated
     */
    public boolean isOutdated(double maxUpdateInterval) {
        double differenceInSeconds = (double) Math.abs(System.currentTimeMillis() - modDate) / 1000;
        return differenceInSeconds > maxUpdateInterval;
    }
}
