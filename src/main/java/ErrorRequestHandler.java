import java.nio.channels.SocketChannel;

/**
 * ErrorRequestHandler
 */
public class ErrorRequestHandler extends BaseRequestHandler {
    public final String errMsg;
    public final int statusCode;
    public final String serverID;

    public ErrorRequestHandler(int lamportClock, SocketChannel socketChannel,
                               String serverID, String errMsg, int statusCode) {
        super(lamportClock, socketChannel);
        this.errMsg = errMsg;
        this.statusCode = statusCode;
        this.serverID = serverID;
    }
}
