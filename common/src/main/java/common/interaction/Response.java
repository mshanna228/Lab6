package common.interaction;

import java.io.Serializable;

/**
 * Класс ответа от сервера к клиенту.
 * Содержит код ответа и тело ответа (текст).
 */
public class Response implements Serializable {
    private static final long serialVersionUID = 1L;

    private ResponseCode responseCode;
    private String responseBody;

    public Response(ResponseCode responseCode, String responseBody) {
        this.responseCode = responseCode;
        this.responseBody = responseBody;
    }

    public ResponseCode getResponseCode() {
        return responseCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    @Override
    public String toString() {
        return "Response[" + responseCode + ", " + responseBody + "]";
    }
}
