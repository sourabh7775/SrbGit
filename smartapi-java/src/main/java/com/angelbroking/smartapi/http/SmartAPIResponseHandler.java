package com.angelbroking.smartapi.http;

import com.angelbroking.smartapi.http.exceptions.*;
import okhttp3.Response;
import org.json.JSONObject;

import java.io.IOException;

public class SmartAPIResponseHandler {

    public JSONObject handle(Response response, String body) throws SmartAPIException, IOException {
        if (body == null || body.isEmpty()) {
            throw new DataException("Empty response received");
        }

        JSONObject jsonResponse;
        try {
            jsonResponse = new JSONObject(body);
        } catch (Exception e) {
            throw new DataException("Invalid JSON response: " + body);
        }

        if (!jsonResponse.optBoolean("status", false)) {
            String errorCode = jsonResponse.optString("errorcode", "");
            String message = jsonResponse.optString("message", "Unknown error");

            switch (errorCode) {
                case "AG8001": throw new TokenException(message, errorCode);
                case "AG8002": throw new TokenException(message, errorCode);
                case "AG8003": throw new ApiKeyException(message, errorCode);
                case "AB1010": throw new PermissionException(message, errorCode);
                case "AB2000": throw new OrderException(message, errorCode);
                default:
                    if (response.code() == 401) throw new TokenException(message, errorCode);
                    if (response.code() == 403) throw new PermissionException(message, errorCode);
                    if (response.code() == 429) throw new NetworkException(message, errorCode);
                    if (response.code() >= 500) throw new GeneralException(message, errorCode);
                    if (!errorCode.isEmpty()) throw new GeneralException(message, errorCode);
            }
        }

        return jsonResponse;
    }
}
