import java.io.IOException;

/**
 * class for response to client
 */
public record ResponseService(AggregationServer server) implements Runnable {

    @Override
    public void run() {
        try {
            while (server.isRunning()) {
                server.response();
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.out.printf("ResponseThread: IOException, %s%n", e.getMessage());
            //feedback error to client if it is thrown by server
            if (e instanceof BaseResponse.ServerInternalException) {
                ((BaseResponse.ServerInternalException) e).feedbackError();
            }
        }
    }
}
