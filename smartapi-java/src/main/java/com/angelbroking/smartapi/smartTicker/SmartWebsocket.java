package com.angelbroking.smartapi.smartTicker;

import com.angelbroking.smartapi.Routes;
import com.neovisionaries.ws.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class SmartWebsocket {

    private static final Logger log = LoggerFactory.getLogger(SmartWebsocket.class);

    private final String clientCode;
    private final String feedToken;
    private WebSocket ws;
    private final Routes routes = new Routes();

    public interface OnMessage { void onMessage(String data); }
    public interface OnError { void onError(Exception e); }

    private OnMessage onMessage;
    private OnError onError;

    public SmartWebsocket(String clientCode, String feedToken) {
        this.clientCode = clientCode;
        this.feedToken = feedToken;
    }

    public void setOnMessage(OnMessage onMessage) { this.onMessage = onMessage; }
    public void setOnError(OnError onError) { this.onError = onError; }

    public void connect() {
        try {
            WebSocketFactory factory = new WebSocketFactory();
            ws = factory.createSocket(routes.getSWsuri());
            ws.addHeader("Authorization", feedToken);
            ws.addHeader("x-client-code", clientCode);
            ws.addListener(new WebSocketAdapter() {
                @Override
                public void onTextMessage(WebSocket websocket, String text) {
                    if (onMessage != null) onMessage.onMessage(text);
                }
                @Override
                public void onError(WebSocket websocket, WebSocketException ex) {
                    log.error("SmartWebsocket error", ex);
                    if (onError != null) onError.onError(ex);
                }
            });
            ws.connectAsynchronously();
        } catch (IOException e) {
            log.error("SmartWebsocket connect error", e);
            if (onError != null) onError.onError(e);
        }
    }

    public void disconnect() {
        if (ws != null) ws.disconnect();
    }
}
