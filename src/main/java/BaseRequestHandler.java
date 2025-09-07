import com.google.gson.Gson;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class BaseRequestHandler implements Comparable<BaseRequestHandler> {
    //socket connection
    private SocketChannel socketChannel;
    //lamport clock from request
    private int lamportClock;

    public BaseRequestHandler(int lamportClock, SocketChannel socketChannel) {
        this.lamportClock = lamportClock;
        this.socketChannel = socketChannel;
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
     * set lamport clock from request
     */
    public void setLamportClock(int lamportClock) {
        this.lamportClock = lamportClock;
    }

    /**
     * Sort by lamport clock in request queue
     *
     * @param other the object to be compared.
     * @return  int
     */
    @Override
    public int compareTo(BaseRequestHandler other) {
        return Integer.compare(this.lamportClock, other.getLamportClock());
    }

    /**
     * send response to client
     *
     * @param baseResponse BaseResponse
     * @throws IOException if an I/O error occurs
     */
    public void response(BaseResponse baseResponse) throws IOException {
        Gson gson = new Gson();
        String response = gson.toJson(baseResponse);
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
