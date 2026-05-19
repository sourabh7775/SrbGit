package com.angelbroking.smartapi.algo;

import com.angelbroking.smartapi.SmartConnect;
import com.angelbroking.smartapi.algo.websocket.SmartStremWebSocket;
import com.angelbroking.smartapi.http.exceptions.SmartAPIException;
import com.angelbroking.smartapi.models.User;
import com.angelbroking.smartapi.orderupdate.OrderUpdateWebsocket;
import com.angelbroking.smartapi.records.ClientInfo;
import com.angelbroking.smartapi.records.OptionGreeks;
import com.neovisionaries.ws.client.WebSocketException;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
public class Login {

    private static final String API_KEY = "FS7kkQIz";

    public static SmartConnect smartConnect;
    public static User user;

    @Autowired
    private SmartStremWebSocket smartStremWebSocket;

    public String login(ClientInfo clientInfo) {
        try {
            smartConnect = new SmartConnect(API_KEY);
            user = smartConnect.generateSession(clientInfo.user(), clientInfo.accessKey(), clientInfo.totp());
            log.info("Login successful for {}", clientInfo.user());

            smartStremWebSocket.connect(clientInfo.user(), user.feedToken);

            return "Login successful. JWT: " + user.accessToken;
        } catch (Exception e) {
            log.error("Login failed", e);
            return "Login failed: " + e.getMessage();
        }
    }

    public String getLtp() {
        if (smartConnect == null) return "Not logged in";
        try {
            JSONObject data = smartConnect.getLTP("NSE", "Nifty 50", "99926000");
            return data != null ? data.toString() : "No data";
        } catch (Exception e) {
            log.error("LTP error", e);
            return "Error: " + e.getMessage();
        }
    }

    public String getOptionGreeks(OptionGreeks optionGreeks) throws SmartAPIException, IOException {
        if (smartConnect == null) return "Not logged in";
        JSONObject data = smartConnect.optionGreek(optionGreeks.name(), optionGreeks.expiryDate());
        return data != null ? data.toString() : "No data";
    }

    public void webSocketOrder() throws SmartAPIException, IOException, WebSocketException {
        if (smartConnect == null || user == null) {
            log.error("Not logged in");
            return;
        }
        OrderUpdateWebsocket orderUpdateWebsocket = new OrderUpdateWebsocket(
                user.clientCode,
                user.feedToken,
                new com.angelbroking.smartapi.orderupdate.OrderUpdateServiceImpl()
        );
        orderUpdateWebsocket.connect();
    }

    public void getCandleData() throws IOException {
        if (smartConnect == null) return;
        try {
            JSONObject params = new JSONObject();
            params.put("exchange", "NSE");
            params.put("symboltoken", "99926000");
            params.put("interval", "FIVE_MINUTE");
            params.put("fromdate", "2024-01-01 09:15");
            params.put("todate", "2024-01-01 15:30");
            org.json.JSONArray data = smartConnect.candleData(params);
            log.info("Candle data: {}", data);
        } catch (SmartAPIException e) {
            log.error("Candle data error", e);
        }
    }
}
