/**
 * response to GETClient
 */
public class GETClientResponse extends BaseResponse{
    private WeatherData weatherData;

    public WeatherData getWeatherData() {
        return weatherData;
    }
    public void setWeatherData(WeatherData weatherData) {
        this.weatherData = weatherData;
    }
}
