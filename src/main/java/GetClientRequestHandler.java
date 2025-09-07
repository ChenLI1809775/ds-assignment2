import java.io.IOException;
import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.util.Objects;

public class GetClientRequestHandler extends BaseRequestHandler {
    public final WeatherData weatherData;

    public GetClientRequestHandler(SocketChannel socketChannel, int lamportClock, WeatherData weatherData) {
        super(lamportClock, socketChannel);
        this.weatherData = weatherData;
    }

    @Override
    public void response(BaseResponse baseResponse) throws IOException {
        GETClientResponse response = new GETClientResponse();
        if (Objects.nonNull(weatherData)) {
            // add addition weather data to response
            response.setWeatherData(weatherData);
            response.setMsg(BaseResponse.MSG_OK);
            response.setStatusCode(BaseResponse.STATUS_CODE_SUCCESS);
        } else {
            //Data not found
            response.setMsg(BaseResponse.MSG_NOT_FOUND);
            response.setStatusCode(BaseResponse.STATUS_CODE_NOT_FOUND);
        }
        super.response(response);
    }
}
