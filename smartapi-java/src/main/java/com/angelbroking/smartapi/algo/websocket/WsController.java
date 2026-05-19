package com.angelbroking.smartapi.algo.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WsController implements WebSocketConfigurer {

    private final AlgoWebSocketHandler algoWebSocketHandler;

    public WsController(AlgoWebSocketHandler algoWebSocketHandler) {
        this.algoWebSocketHandler = algoWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(algoWebSocketHandler, "/ws/ltp").setAllowedOrigins("*");
    }
}
