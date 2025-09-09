import java.io.IOException;
import java.nio.channels.SocketChannel;

public class ContentServerRequestHandler extends BaseRequestHandler {

    private final String serverID;

    private WeatherData weatherData;

    public ContentServerRequestHandler(SocketChannel socketChannel, WeatherData weatherData,
                                       String serverID, int lamportClock) {
        super(lamportClock, socketChannel);
        this.serverID = serverID;
        this.weatherData = weatherData;

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
        baseResponse.setLamportClock(getLamportClock());
        super.response(baseResponse);
        //clear old data
        weatherData = null;
    }

}
