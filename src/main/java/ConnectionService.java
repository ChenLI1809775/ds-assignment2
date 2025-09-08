import com.google.gson.Gson;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

/**
 * class for handle client connection
 */
public record ConnectionService(AggregationServer server) implements Runnable {
    public static final String KEY_UA = "User-Agent";
    public static final String METHOD_PUT = "PUT";
    public static final String METHOD_GET = "GET";


    /**
     * handle client request
     *
     * @param key      selection key
     * @param selector selector
     * @throws IOException when failed to handle request
     */

    private void handleAccept(SelectionKey key, Selector selector) throws IOException {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverSocketChannel.accept();

        if (clientChannel != null) {
            clientChannel.configureBlocking(false);
            clientChannel.register(selector, SelectionKey.OP_READ);
            System.out.println("New client connected: " + clientChannel.getRemoteAddress());
        }
    }

    /**
     * read request data
     *
     * @param socketChannel socket channel
     * @param selectionKey  selection key
     * @return request data
     * @throws IOException when failed to read request data
     */
    public String readRequestData(SocketChannel socketChannel, SelectionKey selectionKey) throws IOException {
        ByteBuffer buff = ByteBuffer.allocate(2048);
        StringBuilder content = new StringBuilder();
        try {
            while (socketChannel.read(buff) > 0) {
                buff.flip();
                content.append(StandardCharsets.UTF_8.decode(buff));
            }
            selectionKey.interestOps(SelectionKey.OP_READ);
            return content.toString();
        } catch (IOException e) {
            selectionKey.cancel();
            if (selectionKey.channel() != null) {
                selectionKey.channel().close();
            }
            return "";
        }
    }

    /**
     * Handle client data reading
     */
    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        String requestData = readRequestData(clientChannel, key);

        if (!requestData.isEmpty()) {
            String method = requestData.substring(0, Math.min(requestData.length(), 3));
            if (method.equals(METHOD_PUT)) {
                //this is a content server
                handleContentServerRequest(clientChannel, requestData);
            } else if (method.equals(METHOD_GET)) {
                //this is a get client
                handleGETClientRequest(clientChannel, requestData);
            } else {
                //invalid method
                server.putErrorRequestHandler("Client", clientChannel,
                        "Method not allowed. Allow: PUT, GET", BaseResponse.STATUS_CODE_FORBIDDEN);
            }
        }
    }

    /**
     * Safely close a channel
     */
    private void closeChannel(java.nio.channels.Channel channel) {
        if (channel != null && channel.isOpen()) {
            try {
                channel.close();
            } catch (IOException e) {
                System.err.println("Error closing channel: " + e.getMessage());
            }
        }
    }

    /**
     * Clean up server resources
     */
    private void closeResources(Selector selector, ServerSocketChannel serverChannel) {
        try {
            if (selector != null && selector.isOpen()) {
                selector.close();
            }
            if (serverChannel != null && serverChannel.isOpen()) {
                serverChannel.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing server resources: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        Selector selector = null;
        ServerSocketChannel serverChannel = null;
        try {
            // Initialize NIO components
            selector = Selector.open();
            serverChannel = ServerSocketChannel.open();
            serverChannel.socket().bind(new InetSocketAddress(server.getPort()));
            serverChannel.configureBlocking(false);
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);

            //System.out.println("Server started and listening on port " + server.getPort());
            // Main event loop
            while (server.isRunning()) {
                try {
                    // Wait for events with timeout to allow periodic checking of server status
                    if (selector.select(1000) == 0) {
                        continue;
                    }

                    Set<SelectionKey> selectedKeys = selector.selectedKeys();
                    Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

                    // Process all selected keys
                    while (keyIterator.hasNext()) {
                        SelectionKey key = keyIterator.next();
                        keyIterator.remove(); // Remove key immediately to prevent duplicate processing

                        try {
                            if (key.isAcceptable()) {
                                handleAccept(key, selector);
                            } else if (key.isReadable()) {
                                handleRead(key);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            System.err.println("Error handling client connection: " + e.getMessage());
                            key.cancel();
                            closeChannel(key.channel());
                        }
                    }
                } catch (IOException e) {
                    if (server.isRunning()) {
                        System.err.println("Selector error in main loop: " + e.getMessage());
                    }
                    break; // Exit loop on selector error
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to start server: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Clean up resources
            closeResources(selector, serverChannel);
        }
    }


    /**
     * handle  request from GETClient
     *
     * @param socketChannel SocketChannel
     * @param requestData   String
     * @throws IOException IOException when request data is invalid
     */
    private void handleGETClientRequest(SocketChannel socketChannel, String requestData) throws IOException {
        String weatherDataID = "";
        String[] lines = requestData.split("\n");
        for (String line : lines) {
            if (line.contains(KEY_UA)) {
                //get id and Lamport clock
                String[] UA = line.split(" ");
                if (UA.length < 4) {
                    server.putErrorRequestHandler("GETClient", socketChannel,
                            "Invalid user-agent in header!",
                            BaseResponse.STATUS_CODE_FORBIDDEN);
                    throw new IOException("Invalid user-agent in header!");
                }
                weatherDataID = UA[2];
                //update lamport clock
                int lamportTime = Integer.parseInt(UA[3]);
                server.updateLamportClock(lamportTime);
            }
        }
        server.putGetRequestHandler(weatherDataID, socketChannel);
    }


    /**
     * handle request from content server
     *
     * @param socketChannel SocketChannel
     * @param requestData   String request content
     * @throws IOException IOException when request data is invalid
     */
    private void handleContentServerRequest(SocketChannel socketChannel, String requestData) throws IOException {
        String contentServerID = "";
        StringBuilder jsonWeatherData = new StringBuilder();
        boolean readJson = false;
        //parse request data
        String[] lines = requestData.split("\n");
        for (String requestLine : lines) {
            if (requestLine.contains(KEY_UA)) {
                //get id and Lamport clock
                String[] UA = requestLine.split(" ");
                if (UA.length < 4) {
                    server.putErrorRequestHandler(contentServerID, socketChannel,
                            "Invalid user-agent in header!",
                            BaseResponse.STATUS_CODE_FORBIDDEN);
                    throw new IOException("Invalid user-agent in header!");
                }
                contentServerID = UA[2];
                //update lamport clock
                int lamportTime = Integer.parseInt(UA[3]);
                //update lamport clock on server
                server.updateLamportClock(lamportTime);
            } else if (requestLine.length() > 0 && requestLine.charAt(0) == '{') {
                //find the start line of json data
                jsonWeatherData.append(requestLine);
                readJson = true;
            } else if (readJson) {
                jsonWeatherData.append(requestLine);
            }
        }
        if (!Objects.equals(jsonWeatherData.toString(), "")) {
            Gson gson = new Gson();
            try {
                WeatherData weatherData = gson.fromJson(jsonWeatherData.toString(), WeatherData.class);
                //put request  handler to queue
                server.putContentPutRequestHandler(contentServerID, socketChannel, weatherData);
            } catch (Exception e) {
                //throw incorrect json error
                server.putErrorRequestHandler(contentServerID, socketChannel, "Invalid json data!",
                        BaseResponse.STATUS_CODE_FORBIDDEN);
            }
        } else {
            //request without any data
            server.putErrorRequestHandler(contentServerID, socketChannel, "No json data to process!",
                    BaseResponse.STATUS_CODE_NO_CONTENT);
        }

    }
}
