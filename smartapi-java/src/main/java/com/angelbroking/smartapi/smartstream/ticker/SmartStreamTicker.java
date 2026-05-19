package com.angelbroking.smartapi.smartstream.ticker;

import com.angelbroking.smartapi.Routes;
import com.angelbroking.smartapi.smartstream.models.*;
import com.angelbroking.smartapi.utils.ByteUtils;
import com.neovisionaries.ws.client.*;
import org.apache.commons.lang.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SmartStreamTicker {

    private static final Logger log = LoggerFactory.getLogger(SmartStreamTicker.class);
    private static final int PING_INTERVAL_MS = 25000;

    private final String clientCode;
    private final String feedToken;
    private SmartStreamListener listener;

    private WebSocket ws;
    private final Routes routes = new Routes();
    private ScheduledExecutorService pingScheduler;
    private boolean isConnected = false;

    public SmartStreamTicker(String clientCode, String feedToken, SmartStreamListener listener) {
        this.clientCode = clientCode;
        this.feedToken = feedToken;
        this.listener = listener;
    }

    public void connect() {
        try {
            WebSocketFactory factory = new WebSocketFactory();
            ws = factory.createSocket(routes.getSmartStreamWSURI());
            ws.addHeader("Authorization", feedToken);
            ws.addHeader("x-client-code", clientCode);
            ws.addHeader("x-feed-token", feedToken);
            ws.addHeader("x-client-type", "WEB");

            ws.addListener(new WebSocketAdapter() {
                @Override
                public void onConnected(WebSocket websocket, Map<String, List<String>> headers) {
                    isConnected = true;
                    log.info("SmartStream connected");
                    startPing();
                    if (listener != null) listener.onConnected();
                }

                @Override
                public void onDisconnected(WebSocket websocket, WebSocketFrame serverCloseFrame,
                                           WebSocketFrame clientCloseFrame, boolean closedByServer) {
                    isConnected = false;
                    stopPing();
                    log.warn("SmartStream disconnected");
                    if (listener != null) listener.onDisconnected();
                }

                @Override
                public void onBinaryMessage(WebSocket websocket, byte[] binary) {
                    handleBinaryMessage(binary);
                }

                @Override
                public void onTextMessage(WebSocket websocket, String text) {
                    log.debug("SmartStream text: {}", text);
                }

                @Override
                public void onError(WebSocket websocket, WebSocketException ex) {
                    log.error("SmartStream error: {}", ex.getMessage());
                    if (listener != null) listener.onError(SmartStreamError.UNKNOWN_ERROR);
                }
            });

            ws.connectAsynchronously();
        } catch (IOException e) {
            log.error("SmartStream connect error", e);
            if (listener != null) listener.onError(SmartStreamError.CONNECTION_FAILED);
        }
    }

    private void handleBinaryMessage(byte[] data) {
        if (data == null || data.length < 2) return;
        try {
            int mode = data[0];
            switch (mode) {
                case 1 -> { if (listener != null) listener.onLTPArrival(ByteUtils.mapToLTP(data)); }
                case 2 -> { if (listener != null) listener.onQuoteArrival(ByteUtils.mapToQuote(data)); }
                case 3 -> { if (listener != null) listener.onSnapQuoteArrival(ByteUtils.mapToSnapQuote(data)); }
                default -> log.debug("Unknown SmartStream mode: {}", mode);
            }
        } catch (Exception e) {
            log.error("Error handling SmartStream binary message", e);
        }
    }

    public void subscribe(SmartStreamSubsMode mode, Set<TokenID> tokens) {
        if (!isConnected || ws == null) {
            log.warn("Cannot subscribe — not connected");
            return;
        }
        try {
            JSONObject request = new JSONObject();
            request.put("action", SmartStreamAction.SUBSCRIBE.getVal());
            request.put("params", buildParams(mode, tokens));
            ws.sendText(request.toString());
        } catch (Exception e) {
            log.error("Subscribe error", e);
            if (listener != null) listener.onError(SmartStreamError.SUBSCRIPTION_FAILED);
        }
    }

    public void unsubscribe(SmartStreamSubsMode mode, Set<TokenID> tokens) {
        if (!isConnected || ws == null) return;
        try {
            JSONObject request = new JSONObject();
            request.put("action", SmartStreamAction.UNSUBSCRIBE.getVal());
            request.put("params", buildParams(mode, tokens));
            ws.sendText(request.toString());
        } catch (Exception e) {
            log.error("Unsubscribe error", e);
        }
    }

    private JSONObject buildParams(SmartStreamSubsMode mode, Set<TokenID> tokens) {
        JSONObject params = new JSONObject();
        params.put("mode", mode.getVal());
        JSONArray tokenList = new JSONArray();
        for (TokenID tokenID : tokens) {
            JSONObject t = new JSONObject();
            t.put("exchangeType", tokenID.getExchangeType().getVal());
            JSONArray tArr = new JSONArray();
            tArr.put(tokenID.getToken());
            t.put("tokens", tArr);
            tokenList.put(t);
        }
        params.put("tokenList", tokenList);
        return params;
    }

    private void startPing() {
        pingScheduler = Executors.newSingleThreadScheduledExecutor();
        pingScheduler.scheduleAtFixedRate(() -> {
            if (isConnected && ws != null) {
                try {
                    JSONObject ping = new JSONObject();
                    ping.put("action", 0);
                    ping.put("params", new JSONObject().put("mode", 0).put("tokenList", new JSONArray()));
                    ws.sendText(ping.toString());
                } catch (Exception e) {
                    log.warn("Ping failed: {}", e.getMessage());
                }
            }
        }, PING_INTERVAL_MS, PING_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void stopPing() {
        if (pingScheduler != null && !pingScheduler.isShutdown()) {
            pingScheduler.shutdownNow();
        }
    }

    public void disconnect() {
        stopPing();
        isConnected = false;
        if (ws != null) {
            ws.disconnect();
        }
    }

    public boolean isConnected() {
        return isConnected;
    }
}
