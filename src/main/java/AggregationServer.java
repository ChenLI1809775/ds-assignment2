import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AggregationServer {

    //file path to save weather data
    private String dataCachePath = "aggregationWeatherData.json";
    //indicate if server is running
    private volatile boolean isRunning;
    //port number,default is 4567
    private int port = 4567;

    //weather data cache
    private final LRUDataCache<String, WeatherData> dataCache;

    //Two threads to handle request
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    //Request queue
    private final Queue<BaseRequestHandler> requestHandlerQueue;

    //Max update interval for content server
    private double maxUpdateInterval = 30.0;
    //local lamport clock
    private final LamportClock lamportClock;

    //use a map to track if connection is active
    private final TreeMap<String, ContentServerRequestHandler> activeContentServerTrack;


    public AggregationServer(int port) {
        if (port > 0) {
            //port number from command line
            this.port = port;
        }
        requestHandlerQueue = new PriorityQueue<>();
        dataCache = new LRUDataCache<>(30);
        activeContentServerTrack = new TreeMap<>();
        lamportClock = new LamportClock();
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
     * update data cache
     */
    public void updateDataCache(String ID, WeatherData weatherData) {
        dataCache.put(ID, weatherData);
    }

    /**
     * get if server is running
     *
     * @return if server is running
     */
    public boolean isRunning() {
        return isRunning;
    }


    /**
     * get max update interval
     *
     * @return max update interval
     */
    public double getMaxUpdateInterval() {
        return maxUpdateInterval;
    }

    /**
     * set max update interval
     *
     * @param maxUpdateInterval max update interval
     */
    public void setMaxUpdateInterval(double maxUpdateInterval) {
        this.maxUpdateInterval = maxUpdateInterval;
    }


    /**
     * get port number
     *
     * @return port number
     */
    public int getPort() {
        return port;
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
     * update local lamport clock
     *
     * @param lamportClock lamport clock
     */
    public void updateLamportClock(int lamportClock) {
        this.lamportClock.updateTime(lamportClock);
    }


    /**
     * sync data cache to file
     */
    public synchronized void syncCacheToFile() throws IOException {
        if (dataCache.values().size() < 1) {
            return;
        }
        ArrayList<WeatherData> weatherDataList = new ArrayList<>(dataCache.values());
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(dataCachePath)) {
            writer.write(gson.toJson(weatherDataList));
        }
    }

    /**
     * start server
     */
    public void start() {
        System.out.println("AggregationServer started at port " + port);
        //Thread to handle response
        new Thread(new ResponseService(this)).start();
        //Thread to handle connection
        new Thread(new ConnectionService(this)).start();
        isRunning = true;
    }

    /**
     * Put Get request from GETClient to queue
     *
     * @param weatherDataID weather data ID
     * @param socketChannel socket channel from GETClient
     */
    public void putGetRequestHandler(String weatherDataID, SocketChannel socketChannel) {
        GetClientRequestHandler requestHandler;
        if (dataCache.containsKey(weatherDataID)) {
            //Client connected before,reuse request handler
            WeatherData weatherData = dataCache.get(weatherDataID);
            requestHandler = new GetClientRequestHandler(socketChannel, lamportClock.getTime(), weatherData);
        } else {
            //New connection
            requestHandler = new GetClientRequestHandler(socketChannel, lamportClock.getTime(), null);
        }
        requestHandlerQueue.add(requestHandler);
    }

    /**
     * Put ContentPut request from ContentServer to queue
     *
     * @param contentServerID content server ID
     * @param socketChannel   socket channel from ContentServer
     * @param weatherData     weather data
     */
    public void putContentPutRequestHandler(String contentServerID, SocketChannel socketChannel, WeatherData weatherData) {
        //request handler
        ContentServerRequestHandler handler;
        if (activeContentServerTrack.containsKey(weatherData.getId())) {
            //connected before
            handler = activeContentServerTrack.get(weatherData.getId());
            //update modified date so that it will not be removed
            handler.refreshModDate();
            //replace old weather data
            handler.setWeatherData(weatherData);
            //update connection
            handler.setSocketChannel(socketChannel);
            //sort by lamport clock
            handler.setLamportClock(lamportClock.getTime());
        } else {
            //create new request handler
            handler = new ContentServerRequestHandler(socketChannel,
                    weatherData, contentServerID, lamportClock.getTime());
            //register in active map
            activeContentServerTrack.put(handler.getServerID(), handler);
        }
        //add to queue, it will handle by another thread
        requestHandlerQueue.add(handler);
    }

    /**
     * Check if request handler queue is empty
     *
     * @return if request handler queue is empty
     */
    public boolean isHandlerQueueEmpty() {
        return requestHandlerQueue.isEmpty();
    }

    public BaseRequestHandler getRequestHandler() {
        return requestHandlerQueue.poll();
    }

    /**
     * clean outdated data from content server
     *
     * @throws IOException if sync to file failed
     */
    public void cleanOutDated() throws IOException {
        Map.Entry<String, ContentServerRequestHandler> lastEntry = activeContentServerTrack.lastEntry();
        if (!Objects.isNull(lastEntry)) {
            ContentServerRequestHandler requestHandler = lastEntry.getValue();
            if (requestHandler.isOutdated(maxUpdateInterval)) {
                activeContentServerTrack.remove(lastEntry.getKey());
                dataCache.remove(lastEntry.getKey());
                //sync data changes to file
                syncCacheToFile();
            }
        }
    }

    /**
     * Response to ContentServer or GETClient
     *
     * @throws IOException if sync to file failed
     */
    public void response() throws IOException {
        //clean json outdated of 30s
        cleanOutDated();
        if (isHandlerQueueEmpty()) {
            return;
        }
        BaseRequestHandler requestHandler = getRequestHandler();
        if (requestHandler instanceof ContentServerRequestHandler) {
            //update data and response to content server
            responseToContentServer((ContentServerRequestHandler) requestHandler);
        } else if (requestHandler instanceof GetClientRequestHandler getRequestHandler) {
            //response to get client
            BaseResponse baseResponse = new BaseResponse();
            baseResponse.setLamportClock(getLamportClock());
            getRequestHandler.response(baseResponse);
        }
    }

    /**
     * request to content server
     *
     * @param requestHandler ContentServerRequestHandler
     */
    void responseToContentServer(ContentServerRequestHandler requestHandler) throws IOException {
        WeatherData weatherData = requestHandler.getWeatherData();
        BaseResponse baseResponse = new BaseResponse();
        // Send local lamport clock to content server
        baseResponse.setLamportClock(lamportClock.getTime());

        if (weatherData == null) {
            // No content
            baseResponse.setStatusCode(BaseResponse.STATUS_CODE_NO_CONTENT);
            baseResponse.setMsg(BaseResponse.MSG_NO_CONTENT);
            requestHandler.response(baseResponse);
            return;
        }

        // Validate weather data before processing
        if (weatherData.getId() == null || weatherData.getId().isEmpty()) {
            baseResponse.setStatusCode(BaseResponse.STATUS_CODE_FORBIDDEN);
            baseResponse.setMsg("Invalid weather data: missing ID");
            requestHandler.response(baseResponse);
            return;
        }

        // Save weather data in cache
        dataCache.put(weatherData.getId(), weatherData);

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
                    baseResponse.setStatusCode(BaseResponse.STATUS_CODE_CREATED);
                    baseResponse.setMsg(BaseResponse.MSG_CREATED);
                } else {
                    // Log warning but don't fail the request
                    //System.err.println("Warning: Could not create file " + dataCachePath);
                    baseResponse.setStatusCode(BaseResponse.STATUS_CODE_SUCCESS);
                    baseResponse.setMsg(BaseResponse.MSG_OK);
                }
            } else {
                baseResponse.setStatusCode(BaseResponse.STATUS_CODE_SUCCESS);
                baseResponse.setMsg(BaseResponse.MSG_OK);
            }

            // Send response before file I/O to reduce latency
            requestHandler.response(baseResponse);

            // Write to file asynchronously or in a separate thread if possible
            syncCacheToFile();

        } catch (IOException e) {
            System.err.println("Error handling file operations: " + e.getMessage());
            // Even if file operations fail, we've already processed the data successfully
            // So we still send a success response but log the file error
            baseResponse.setStatusCode(BaseResponse.STATUS_CODE_SUCCESS);
            baseResponse.setMsg(BaseResponse.MSG_OK + " (Note: File sync failed)");
            requestHandler.response(baseResponse);
        }

    }

    /**
     * stop server
     */
    public void stop() throws IOException {
        executor.shutdown();
        //shut down all threads
        isRunning = false;
        //write data cache to file
        syncCacheToFile();
        //close server socket
        System.out.println("AggregationServer stop");
    }

    public static void main(String[] args) {
        if (args.length > 0) {
            new AggregationServer(Integer.parseInt(args[0])).start();
        } else {
            //no args
            new AggregationServer(-1).start();
        }
    }
}
