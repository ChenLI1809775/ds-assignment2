import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AggregationServer {


    //indicate if server is running
    private volatile boolean isRunning;
    //port number,default is 4567
    private int port = 4567;

    //Two threads to handle request
    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    //Request queue
    private final Queue<BaseRequestHandler> requestHandlerQueue;

    //Max update interval(s) for content server update
    private double maxUpdateInterval = 30.0;
    //local lamport clock
    private final LamportClock lamportClock;

    //use a map to track if connection is active
    private final TreeMap<String, ContentServerRequestHandler> activeContentServerTrack;

    private final IOService ioService;

    public AggregationServer(int port, String dataCachePath) {
        if (port > 0) {
            //port number from command line
            this.port = port;
        }
        requestHandlerQueue = new PriorityQueue<>();
        ioService = new IOService(dataCachePath);
        activeContentServerTrack = new TreeMap<>();
        lamportClock = new LamportClock();
    }

    /**
     * get data cache path
     *
     * @return data cache path
     */
    public String getDataCachePath() {
        return ioService.getDataCachePath();
    }


    /**
     * set data cache path
     *
     * @param dataCachePath data cache path
     */
    public void setDataCachePath(String dataCachePath) {
        ioService.setDataCachePath(dataCachePath);
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
     * Put Get request from GETClient to queue
     *
     * @param weatherDataID weather data ID
     * @param socketChannel socket channel from GETClient
     */
    public void putGetRequestHandler(String weatherDataID, SocketChannel socketChannel) {
        GetClientRequestHandler requestHandler;
        // First check in memory cache
        WeatherData weatherData = ioService.findWeatherData(weatherDataID);
        // update lamport clock
        lamportClock.tick();
        requestHandler = new GetClientRequestHandler(socketChannel, lamportClock.getTime(), weatherData);
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
        // update lamport clock
        lamportClock.tick();
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
     * Put Error request from ErrorClient to queue
     *
     * @param clientID      client ID
     * @param socketChannel socket channel from ErrorClient
     * @param msg           error message
     * @param statusCode    status code
     */
    public void putErrorRequestHandler(String clientID, SocketChannel socketChannel, String msg, int statusCode) {
        ErrorRequestHandler requestHandler = new ErrorRequestHandler(lamportClock.getTime(),
                socketChannel, clientID, msg, statusCode);
        requestHandlerQueue.add(requestHandler);
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
     */
    public void cleanOutDated() {
        Iterator<Map.Entry<String, ContentServerRequestHandler>> iterator =
                activeContentServerTrack.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, ContentServerRequestHandler> entry = iterator.next();
            //Check if the entry is outdated
            if (entry.getValue().isOutdated(maxUpdateInterval)) {
                //Delete the outdated entry
                iterator.remove();
                //remove from cache
                ioService.cleanCacheByID(entry.getKey());
                System.out.println("Outdated data removed: " + entry.getKey());
            }
        }
    }

    /**
     * Response to ContentServer or GETClient
     *
     * @throws IOException if sync to file failed
     */
    public void response() throws IOException {
        cleanOutDated();
        if (ioService.canSync()) {
            ioService.syncCacheToFile();
        }
        if (isHandlerQueueEmpty()) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return;
        }
        BaseRequestHandler requestHandler = getRequestHandler();
        if (requestHandler instanceof ContentServerRequestHandler) {
            //update data and response to content server
            responseToContentServer((ContentServerRequestHandler) requestHandler);
        } else if (requestHandler instanceof GetClientRequestHandler getRequestHandler) {
            //response to get client
            BaseResponse baseResponse = new BaseResponse();
            getRequestHandler.response(baseResponse);
        } else if (requestHandler instanceof ErrorRequestHandler errorRequestHandler) {
            //response to error client
            BaseResponse baseResponse = new BaseResponse();
            baseResponse.setLamportClock(errorRequestHandler.getLamportClock());
            baseResponse.setStatusCode(errorRequestHandler.statusCode);
            baseResponse.setMsg(errorRequestHandler.errMsg);
            errorRequestHandler.response(baseResponse);
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

        if (weatherData == null) {
            // No content
            baseResponse.setStatusCode(BaseResponse.STATUS_CODE_NO_CONTENT);
            baseResponse.setMsg(BaseResponse.MSG_NO_CONTENT);
            requestHandler.response(baseResponse);
            return;
        } else if (weatherData.getId() == null || weatherData.getId().isEmpty()) {
            // Validate weather data before processing
            baseResponse.setStatusCode(BaseResponse.STATUS_CODE_FORBIDDEN);
            baseResponse.setMsg("Invalid weather data: missing ID");
            requestHandler.response(baseResponse);
            return;
        }

        // Save weather data
        ioService.putDataToCache(weatherData.getId(), weatherData);
        //response
        int cacheStatus = ioService.getCacheFileStatus();
        if (cacheStatus == IOService.CACHE_FILE_CREATED) {
            baseResponse.setStatusCode(BaseResponse.STATUS_CODE_CREATED);
            baseResponse.setMsg(BaseResponse.MSG_CREATED);
            requestHandler.response(baseResponse);
        } else if (cacheStatus == IOService.CACHE_FILE_CREATE_FAILED) {
            baseResponse.setStatusCode(BaseResponse.STATUS_CODE_SERVER_ERROR);
            baseResponse.setMsg(BaseResponse.MSG_SERVER_ERROR);
            requestHandler.response(baseResponse);
            baseResponse.setStatusCode(BaseResponse.STATUS_CODE_SERVER_ERROR);
            baseResponse.setMsg(BaseResponse.MSG_SERVER_ERROR + " (Note: File sync failed)");
            requestHandler.response(baseResponse);
        } else {
            baseResponse.setStatusCode(BaseResponse.STATUS_CODE_SUCCESS);
            baseResponse.setMsg(BaseResponse.MSG_OK);
            requestHandler.response(baseResponse);
        }
    }

    /**
     * start server
     */
    public void start() {
        //Must set to true before thread start
        isRunning = true;
        //Thread to handle response
        executor.submit(() -> new ResponseService(this).run());
        executor.submit(() -> new ConnectionService(this).run());
        //Wait for 1s to wait for thread to start
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        System.out.println("AggregationServer started at port " + port);
    }

    /**
     * stop server
     */
    public void stop() {
        executor.shutdown();
        //shut down all threads
        isRunning = false;
        //close server socket
        System.out.println("AggregationServer stop");
    }

    public static void main(String[] args) {
        String dataCachePath = "src/main/resources/weatherData.json";
        if (args.length > 0) {
            new AggregationServer(Integer.parseInt(args[0]), dataCachePath).start();
        } else {
            //no args
            new AggregationServer(-1, dataCachePath).start();
        }
    }
}
