import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Random;

/**
 * data class for weather data
 */
public class WeatherData {
    private String id;
    private String name;
    private String state;
    private String time_zone;

    private double lat;
    private double lon;

    private String local_date_time;
    private String local_date_time_full;
    private double air_temp;
    private double apparent_t;
    private String cloud;

    private double dewpt;
    private double press;
    private int rel_hum;
    private String wind_dir;

    private int wind_spd_kmh;
    private int wind_spd_kt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getTime_zone() {
        return time_zone;
    }

    public void setTime_zone(String time_zone) {
        this.time_zone = time_zone;
    }

    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLon() {
        return lon;
    }

    public void setLon(double lon) {
        this.lon = lon;
    }

    public String getLocal_date_time() {
        return local_date_time;
    }

    public void setLocal_date_time(String local_date_time) {
        this.local_date_time = local_date_time;
    }

    public String getLocal_date_time_full() {
        return local_date_time_full;
    }


    public double getAir_temp() {
        return air_temp;
    }

    public void setAir_temp(double air_temp) {
        this.air_temp = air_temp;
    }

    public double getApparent_t() {
        return apparent_t;
    }

    public void setApparent_t(double apparent_t) {
        this.apparent_t = apparent_t;
    }

    public String getCloud() {
        return cloud;
    }

    public void setCloud(String cloud) {
        this.cloud = cloud;
    }

    public double getDewpt() {
        return dewpt;
    }

    public void setDewpt(double dewpt) {
        this.dewpt = dewpt;
    }

    public double getPress() {
        return press;
    }

    public void setPress(double press) {
        this.press = press;
    }

    public int getRel_hum() {
        return rel_hum;
    }

    public void setRel_hum(int rel_hum) {
        this.rel_hum = rel_hum;
    }

    public String getWind_dir() {
        return wind_dir;
    }

    public void setWind_dir(String wind_dir) {
        this.wind_dir = wind_dir;
    }

    public int getWind_spd_kmh() {
        return wind_spd_kmh;
    }

    public void setWind_spd_kmh(int wind_spd_kmh) {
        this.wind_spd_kmh = wind_spd_kmh;
    }

    public int getWind_spd_kt() {
        return wind_spd_kt;
    }

    public void setWind_spd_kt(int wind_spd_kt) {
        this.wind_spd_kt = wind_spd_kt;
    }

    public void setLocal_date_time_full(String local_date_time_full) {
        this.local_date_time_full = local_date_time_full;
    }

    /**
     * double format
     *
     * @param value double
     * @return double
     */
    private static double decimalFormat(double value) {
        //decimal format to 3 decimal places
        DecimalFormat decimalFormat = new DecimalFormat("#.###");
        return Double.parseDouble(decimalFormat.format(value));
    }

    /**
     * update weather data randomly for mock testing
     *
     * @param weatherData     weather data
     * @param currentDateTime current datetime
     * @param random          random
     */
    public static void updateWeatherData(WeatherData weatherData, LocalDateTime currentDateTime, Random random) {
        if (Objects.equals(weatherData, null)) {
            return;
        }
        //update apparent_t randomly
        double apparentT = 1 + random.nextDouble() * 20;
        weatherData.setApparent_t(decimalFormat(apparentT));

        //update air_temp randomly
        double airTemp = 5 + random.nextDouble() * 30;
        weatherData.setAir_temp(decimalFormat(airTemp));

        //local datetime full
        DateTimeFormatter formatterFull = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        weatherData.setLocal_date_time_full(currentDateTime.format(formatterFull));

        //local datetime
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM:HH:mma");
        weatherData.setLocal_date_time(currentDateTime.format(formatter));

    }


}
