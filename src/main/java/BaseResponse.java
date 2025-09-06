import java.io.IOException;

/**
 * Base response from AggregationServer to ContentSever or GETClient
 */
public class BaseResponse {
    public static final String MSG_NO_CONTENT = "No content.";
    public static final String MSG_METHOD_NOT_ALLOW = "Method Not Allowed. Allow: GET, POST";
    public static final String MSG_OK = "ok.";
    public static final String MSG_CREATED = "Put weather data success and file has created.";
    public static final String MSG_SERVER_ERROR = "Internal server error.";
    public static final String MSG_NOT_FOUND = "Not found.";

    public static final int HTTP_SUCCESS = 200;
    public static final int HTTP_CREATED = 201;
    public static final int HTTP_NO_CONTENT = 204;
    public static final int HTTP_FORBIDDEN = 400;
    public static final int HTTP_NOT_FOUND = 404;
    public static final int HTTP_SERVER_ERROR = 500;
    private int code;
    private String msg;
    private int lamportClock;


    public int getLamportClock() {
        return lamportClock;
    }

    public void setLamportClock(int lamportClock) {
        this.lamportClock = lamportClock;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    /**
     * class for handle server error
     */
    public static class ServerInternalException extends IOException {
        public final BaseRequestHandler requestHandler;
        public final String msg;

        public ServerInternalException(String msg, BaseRequestHandler requestHandler) {
            this.requestHandler = requestHandler;
            this.msg = msg;
        }

        /**
         * feedback error to client
         */
        public void feedbackError() {
            BaseResponse simpleResponse = new BaseResponse();
            simpleResponse.setCode(BaseResponse.HTTP_SERVER_ERROR);
            simpleResponse.setMsg(String.format("%s,%s", BaseResponse.MSG_SERVER_ERROR, msg));
            try {
                requestHandler.response(simpleResponse);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}