import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class BaseRequestHandler implements Comparable<BaseRequestHandler> {
    //socket connection
    private SocketChannel socketChannel;
    //lamport clock from request
    private int lamportClock;

    //Latest system time when the data is modified
    private long modDate;

    public BaseRequestHandler(int lamportClock, SocketChannel socketChannel) {
        this.lamportClock = lamportClock;
        this.socketChannel = socketChannel;
        this.modDate = System.currentTimeMillis();
    }

    /**
     * get lamport clock from request
     *
     * @return lamport clock
     */
    public int getLamportClock() {
        return lamportClock;
    }

    /**
     * Refresh the modDate
     */
    public void refreshModDate() {
        modDate = System.currentTimeMillis();
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
        return differenceInSeconds > maxUpdateInterval && socketChannel.socket().isClosed();
    }
    /**
     * set lamport clock from request
     */
    public void setLamportClock(int lamportClock) {
        this.lamportClock = lamportClock;
    }

    /**
     * Sort by modDate in active track
     *
     * @param other the object to be compared.
     * @return int
     */
    @Override
    public int compareTo(BaseRequestHandler other) {
        return Long.compare(other.modDate, this.modDate);
    }

    /**
     * send response to client
     *
     * @param baseResponse BaseResponse
     * @throws IOException if an I/O error occurs
     */
    public void response(BaseResponse baseResponse) throws IOException {
//        Gson gson = new Gson();
//        String response = gson.toJson(baseResponse);
        CustomJsonParser jsonParser = new CustomJsonParser();
        String response = jsonParser.stringify(baseResponse);
        ByteBuffer writeBuffer = ByteBuffer.wrap(response.getBytes());
        while (writeBuffer.hasRemaining()) {
            socketChannel.write(writeBuffer);
        }
        //RESTful is stateless, so the connection is closed after the request is processed immediately
        socketChannel.close();
    }

    /**
     * set new connection for next request from same client
     *
     * @param socketChannel SocketChannel
     */
    public void setSocketChannel(SocketChannel socketChannel) {
        this.socketChannel = socketChannel;
    }

}
