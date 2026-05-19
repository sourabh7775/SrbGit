package com.angelbroking.smartapi.http;

import com.angelbroking.smartapi.http.exceptions.SmartAPIException;
import okhttp3.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;
import java.util.Enumeration;

public class SmartAPIRequestHandler {

    private static final Logger log = LoggerFactory.getLogger(SmartAPIRequestHandler.class);
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final SmartAPIResponseHandler responseHandler = new SmartAPIResponseHandler();

    private String apiKey;
    private String accessToken;
    private String localIp;
    private String publicIp;
    private String mac;

    public SmartAPIRequestHandler(OkHttpClient client) {
        this.client = client;
        initNetworkInfo();
    }

    private void initNetworkInfo() {
        try {
            localIp = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            localIp = "127.0.0.1";
        }
        try {
            URL url = new URL("https://api.ipify.org");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            publicIp = new String(conn.getInputStream().readAllBytes()).trim();
        } catch (Exception e) {
            publicIp = localIp;
        }
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            mac = "00:00:00:00:00:00";
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                byte[] hwAddr = ni.getHardwareAddress();
                if (hwAddr != null && hwAddr.length > 0) {
                    StringBuilder sb = new StringBuilder();
                    for (byte b : hwAddr) sb.append(String.format("%02X:", b));
                    mac = sb.substring(0, sb.length() - 1);
                    break;
                }
            }
        } catch (Exception e) {
            mac = "00:00:00:00:00:00";
        }
    }

    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    private Request.Builder baseHeaders(String apiKey, String accessToken) {
        return new Request.Builder()
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-UserType", "USER")
                .header("X-SourceID", "WEB")
                .header("X-ClientLocalIP", localIp)
                .header("X-ClientPublicIP", publicIp)
                .header("X-MACAddress", mac)
                .header("X-PrivateKey", apiKey != null ? apiKey : "")
                .header("Authorization", "Bearer " + (accessToken != null ? accessToken : ""));
    }

    public JSONObject postRequest(String url, JSONObject params, String apiKey, String accessToken)
            throws SmartAPIException, IOException {
        RequestBody body = RequestBody.create(params.toString(), JSON);
        Request request = baseHeaders(apiKey, accessToken)
                .url(url).post(body).build();
        try (Response response = client.newCall(request).execute()) {
            String respBody = response.body() != null ? response.body().string() : "";
            return responseHandler.handle(response, respBody);
        }
    }

    public JSONObject getRequest(String url, String apiKey, String accessToken)
            throws SmartAPIException, IOException {
        Request request = baseHeaders(apiKey, accessToken)
                .url(url).get().build();
        try (Response response = client.newCall(request).execute()) {
            String respBody = response.body() != null ? response.body().string() : "";
            return responseHandler.handle(response, respBody);
        }
    }

    public JSONObject postLoginRequest(String url, JSONObject params)
            throws SmartAPIException, IOException {
        RequestBody body = RequestBody.create(params.toString(), JSON);
        Request request = new Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-UserType", "USER")
                .header("X-SourceID", "WEB")
                .header("X-ClientLocalIP", localIp)
                .header("X-ClientPublicIP", publicIp)
                .header("X-MACAddress", mac)
                .header("X-PrivateKey", apiKey != null ? apiKey : "")
                .post(body).build();
        try (Response response = client.newCall(request).execute()) {
            String respBody = response.body() != null ? response.body().string() : "";
            return responseHandler.handle(response, respBody);
        }
    }
}
