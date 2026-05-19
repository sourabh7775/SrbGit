package com.angelbroking.smartapi.ticker;

import com.angelbroking.smartapi.Routes;
import com.neovisionaries.ws.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class SmartAPITicker {

    private static final Logger log = LoggerFactory.getLogger(SmartAPITicker.class);

    private final String clientCode;
    private final String feedToken;
    private WebSocket ws;
    private final Routes routes = new Routes();

    private OnTick onTick;
    private OnConnect onConnect;
    private OnDisconnect onDisconnect;
    private OnError onError;

    public interface OnTick { void onTick(String data); }
    public interface OnConnect { void onConnect(); }
    public interface OnDisconnect { void onDisconnect(); }
    public interface OnError { void onError(Exception e); }

    public SmartAPITicker(String clientCode, String feedToken) {
        this.clientCode = clientCode;
        this.feedToken = feedToken;
    }

    public void setOnTick(OnTick onTick) { this.onTick = onTick; }
    public void setOnConnect(OnConnect onConnect) { this.onConnect = onConnect; }
    public void setOnDisconnect(OnDisconnect onDisconnect) { this.onDisconnect = onDisconnect; }
    public void setOnError(OnError onError) { this.onError = onError; }

    public void connect() {
        try {
            WebSocketFactory factory = new WebSocketFactory();
            ws = factory.createSocket(routes.getWsuri());
            ws.addHeader("Authorization", feedToken);
            ws.addListener(new WebSocketAdapter() {
                @Override
                public void onConnected(WebSocket websocket, Map<String, List<String>> headers) {
                    if (onConnect != null) onConnect.onConnect();
                }
                @Override
                public void onDisconnected(WebSocket websocket, WebSocketFrame serverCloseFrame,
                                           WebSocketFrame clientCloseFrame, boolean closedByServer) {
                    if (onDisconnect != null) onDisconnect.onDisconnect();
                }
                @Override
                public void onTextMessage(WebSocket websocket, String text) {
                    if (onTick != null) onTick.onTick(text);
                }
                @Override
                public void onError(WebSocket websocket, WebSocketException ex) {
                    if (onError != null) onError.onError(ex);
                }
            });
            ws.connectAsynchronously();
        } catch (IOException e) {
            if (onError != null) onError.onError(e);
        }
    }

    public void disconnect() {
        if (ws != null) ws.disconnect();
    }
}
