import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Class for I/O service
 */
public class IOService {
    public static final int CACHE_FILE_CREATE_FAILED = -1;
    public static final int CACHE_FILE_CREATED = 1;
    public static final int CACHE_FILE_EXIST = 2;
    //file path to save weather data
    private String dataCachePath;
    //weather data cache
    private final LRUDataCache<String, WeatherData> dataCache;
    //0 is not created , 1 is created first time.
    private int cacheFileStatus;
    private final HashMap<String, WeatherData> syncCache = new HashMap<>();

    public IOService(String dataCachePath) {
        this.dataCachePath = dataCachePath;
        dataCache = new LRUDataCache<>(30);
        try {
            File file = new File(dataCachePath);

            // Create parent directories if they don't exist
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    System.err.println("Warning: Failed to create parent directories for " + dataCachePath);
                }
            }

            // Create file if it doesn't exist
            if (!file.exists()) {
                if (file.createNewFile()) {
                    cacheFileStatus = CACHE_FILE_CREATED;
                } else {
                    cacheFileStatus = CACHE_FILE_CREATE_FAILED;
                }
            } else {
                cacheFileStatus = CACHE_FILE_EXIST;
            }

        } catch (IOException e) {
            System.err.println("Error handling file operations: " + e.getMessage());
            cacheFileStatus = CACHE_FILE_CREATE_FAILED;
        }
        loadCacheFromFile();
    }

    /**
     * check if cache can sync
     *
     * @return true if cache can sync
     */
    public boolean canSync() {
        return syncCache.size() > 0;
    }

    /**
     * A cache that removes the least recently used entry when its size exceeds a given limit.
     */
    public static class LRUDataCache<K, V> extends LinkedHashMap<K, V> {
        private final int maxSize;

        public LRUDataCache(int maxSize) {
            super(maxSize + 1, 1.0f, true);
            this.maxSize = maxSize;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > maxSize;
        }
    }

    /**
     * get cache file status
     *
     * @return cache file status
     */
    public int getCacheFileStatus() {
        int status = cacheFileStatus;
        //if cache file exist, set status to exist
        if (status == CACHE_FILE_CREATED) {
            cacheFileStatus = CACHE_FILE_EXIST;
        }
        return status;
    }

    /**
     * get data cache path
     *
     * @return data cache path
     */
    public String getDataCachePath() {
        return dataCachePath;
    }

    /**
     * set data cache path
     *
     * @param dataCachePath data cache path
     */
    public void setDataCachePath(String dataCachePath) {
        this.dataCachePath = dataCachePath;
    }

    /**
     * read data from file
     *
     * @return weather data map
     */
    public synchronized HashMap<String, WeatherData> readDataFromFile() {
        // Load existing data from file
        HashMap<String, WeatherData> fileDataMap = new HashMap<>();
        File file = new File(dataCachePath);
        if (!file.exists()) {
            return fileDataMap;
        }

        try {
            //Check if data has been deleted correctly
            String content = Files.readString(Paths.get(file.getPath()));
            if (content.length() < 1) {
                return fileDataMap;
            }
            CustomJsonParser customJsonParser = new CustomJsonParser();
            ArrayList<WeatherData> weatherDataList = customJsonParser.parse(content, ArrayList.class, WeatherData.class);

            if (weatherDataList != null) {
                // Find the specific weather data by ID
                for (WeatherData data : weatherDataList) {
                    fileDataMap.put(data.getId(), data);
                }
            }
            return fileDataMap;
        } catch (IOException | com.google.gson.JsonSyntaxException e) {
            System.err.println("Error reading weather data from file: " + e.getMessage());
            return fileDataMap;
        }
    }

    /**
     * sync cache to file
     */
    public synchronized void syncCacheToFile() {
        if (syncCache.values().size() < 1) {
            return;
        }
        File file = new File(dataCachePath);
        if (!file.exists()) {
            return;
        }
        // Load existing data from file
        Map<String, WeatherData> fileDataMap = readDataFromFile();
        // Merge syncCache data into fileDataMap (Include add and delete)
        for (Map.Entry<String, WeatherData> entry : syncCache.entrySet()) {
            if (entry.getValue() == null) {
                //Merge delete
                fileDataMap.remove(entry.getKey());
            } else {
                //Merge add
                fileDataMap.put(entry.getKey(), entry.getValue());
            }
        }


        // Create temporary file
        File tempFile = new File(dataCachePath + ".tmp");

        // Write merged data to temporary file
        ArrayList<WeatherData> mergedDataList = new ArrayList<>(fileDataMap.values());
        CustomJsonParser customJsonParser = new CustomJsonParser();
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write(customJsonParser.stringify(mergedDataList));
        } catch (IOException e) {
            System.err.println("Error writing weather data to file: " + e.getMessage());
            throw new RuntimeException(e);
        }

        // Atomically replace the original file with the temporary file
        File backupFile = new File(dataCachePath + ".bak");
        if (file.exists()) {
            if (backupFile.exists()) {
                boolean deleted = backupFile.delete();
            }
            boolean deleted = file.renameTo(backupFile);
        }

        boolean success = tempFile.renameTo(file);
        if (success) {
            boolean deleted = backupFile.delete();
        }
        // Clear syncCache after successful sync
        syncCache.clear();

        //System.out.println("Successfully saved cache to file: " + dataCachePath);
    }


    /**
     * load cache from file
     */
    public synchronized void loadCacheFromFile() {
        File file = new File(dataCachePath);
        if (!file.exists()) {
            return;
        }
        dataCache.clear();
        HashMap<String, WeatherData> fileDataMap = readDataFromFile();
        ArrayList<WeatherData> list = new ArrayList<>(fileDataMap.values());
        if (!list.isEmpty()) {
            //update cache
            for (WeatherData weatherData : list) {
                if (weatherData != null && weatherData.getId() != null) {
                    dataCache.put(weatherData.getId(), weatherData);
                }
            }
        }
    }

    /**
     * put data cache
     */
    public void putDataToCache(String ID, WeatherData weatherData) {
        dataCache.put(ID, weatherData);
        //Save to cache
        syncCache.put(ID, weatherData);
    }

    /**
     * Find weather data by ID
     *
     * @return WeatherData object if found, null otherwise
     */
    public WeatherData findWeatherData(String weatherDataID) {
        if (dataCache.containsKey(weatherDataID)) {
            return dataCache.get(weatherDataID);
        } else {
            // If not found in cache, try to find in file
            WeatherData weatherData = findWeatherDataFromFile(weatherDataID);

            if (weatherData != null) {
                // If found in file, also put it in cache for future requests
                dataCache.put(weatherDataID, weatherData);
            }
            return weatherData;
        }
    }

    /**
     * Find weather data from file by ID
     *
     * @param weatherDataID the ID of weather data to find
     * @return WeatherData object if found, null otherwise
     */
    private synchronized WeatherData findWeatherDataFromFile(String weatherDataID) {
        try {
            File file = new File(dataCachePath);
            if (!file.exists()) {
                return null;
            }

            CustomJsonParser customJsonParser = new CustomJsonParser();
            String content = Files.readString(Paths.get(dataCachePath));
            ArrayList<WeatherData> weatherDataList = customJsonParser.parse(content, ArrayList.class, WeatherData.class);
            if (weatherDataList != null) {
                // Find the specific weather data by ID
                for (WeatherData data : weatherDataList) {
                    if (data != null && weatherDataID.equals(data.getId())) {
                        return data;
                    }
                }
            }
        } catch (IOException | com.google.gson.JsonSyntaxException e) {
            System.err.println("Error reading weather data from file: " + e.getMessage());
        }

        return null;
    }

    /**
     * Clean cache by ID
     *
     * @param weatherDataID the ID of weather data to clean
     */
    public void cleanCacheByID(String weatherDataID) {
        dataCache.remove(weatherDataID);
        //Mark as deleted in syncCache
        syncCache.put(weatherDataID, null);
    }
}
