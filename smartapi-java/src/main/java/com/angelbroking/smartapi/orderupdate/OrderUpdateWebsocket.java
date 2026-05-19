package com.angelbroking.smartapi.orderupdate;

import com.angelbroking.smartapi.Routes;
import com.neovisionaries.ws.client.*;
import org.apache.commons.lang.StringUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class OrderUpdateWebsocket {

    private static final Logger log = LoggerFactory.getLogger(OrderUpdateWebsocket.class);

    private final String clientCode;
    private final String feedToken;
    private final OrderUpdateListner listener;

    private WebSocket ws;
    private final Routes routes = new Routes();

    public OrderUpdateWebsocket(String clientCode, String feedToken, OrderUpdateListner listener) {
        this.clientCode = clientCode;
        this.feedToken = feedToken;
        this.listener = listener;
    }

    public void connect() {
        if (StringUtils.isEmpty(clientCode) || StringUtils.isEmpty(feedToken)) {
            log.error("clientCode or feedToken is empty");
            return;
        }
        try {
            WebSocketFactory factory = new WebSocketFactory();
            ws = factory.createSocket(routes.getOrderUpdateUri());
            ws.addHeader("Authorization", feedToken);
            ws.addHeader("x-client-code", clientCode);
            ws.addHeader("x-feed-token", feedToken);

            ws.addListener(new WebSocketAdapter() {
                @Override
                public void onConnected(WebSocket websocket, Map<String, List<String>> headers) {
                    log.info("Order update WS connected");
                    if (listener != null) listener.onConnected();
                }

                @Override
                public void onDisconnected(WebSocket websocket, WebSocketFrame serverCloseFrame,
                                           WebSocketFrame clientCloseFrame, boolean closedByServer) {
                    log.warn("Order update WS disconnected");
                    if (listener != null) listener.onDisconnected();
                }

                @Override
                public void onTextMessage(WebSocket websocket, String text) {
                    try {
                        JSONObject order = new JSONObject(text);
                        if (listener != null) listener.onOrderUpdate(order);
                    } catch (Exception e) {
                        log.error("Error parsing order update: {}", text, e);
                    }
                }

                @Override
                public void onError(WebSocket websocket, WebSocketException ex) {
                    log.error("Order update WS error", ex);
                    if (listener != null) listener.onError(ex);
                }
            });

            ws.connectAsynchronously();
        } catch (IOException e) {
            log.error("Order update WS connect error", e);
            if (listener != null) listener.onError(e);
        }
    }

    public void disconnect() {
        if (ws != null) ws.disconnect();
    }
}
