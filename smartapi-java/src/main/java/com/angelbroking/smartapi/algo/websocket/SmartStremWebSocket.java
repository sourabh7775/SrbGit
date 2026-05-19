package com.angelbroking.smartapi.algo.websocket;

import com.angelbroking.smartapi.algo.Login;
import com.angelbroking.smartapi.algo.event.LtpEvent;
import com.angelbroking.smartapi.smartstream.models.ExchangeType;
import com.angelbroking.smartapi.smartstream.models.SmartStreamError;
import com.angelbroking.smartapi.smartstream.models.SmartStreamSubsMode;
import com.angelbroking.smartapi.smartstream.models.TokenID;
import com.angelbroking.smartapi.smartstream.ticker.SmartStreamListener;
import com.angelbroking.smartapi.smartstream.ticker.SmartStreamTicker;
import com.angelbroking.smartapi.smartstream.models.*;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
public class SmartStremWebSocket implements SmartStreamListener {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private AlgoWebSocketHandler algoWebSocketHandler;

    private SmartStreamTicker ticker;

    public void connect(String clientCode, String feedToken) {
        ticker = new SmartStreamTicker(clientCode, feedToken, this);
        ticker.connect();
    }

    public void subscribe(Set<TokenID> tokens) {
        if (ticker != null) {
            ticker.subscribe(SmartStreamSubsMode.LTP, tokens);
        }
    }

    public void unsubscribe(Set<TokenID> tokens) {
        if (ticker != null) {
            ticker.unsubscribe(SmartStreamSubsMode.LTP, tokens);
        }
    }

    @Override
    public void onConnected() {
        log.info("SmartStream connected");
    }

    @Override
    public void onDisconnected() {
        log.warn("SmartStream disconnected");
    }

    @Override
    public void onError(SmartStreamError error) {
        log.error("SmartStream error: {}", error.getMessage());
    }

    @Override
    public void onLTPArrival(LTP ltp) {
        double price = ltp.getLastTradedPrice() / 100.0;
        String token = ltp.getToken();
        eventPublisher.publishEvent(new LtpEvent(this, token, price));

        JSONObject msg = new JSONObject();
        msg.put("token", token);
        msg.put("ltp", price);
        algoWebSocketHandler.broadcast(msg.toString());
    }

    @Override
    public void onQuoteArrival(Quote quote) {
        log.debug("Quote: {} @ {}", quote.getToken(), quote.getLastTradedPrice() / 100.0);
    }

    @Override
    public void onSnapQuoteArrival(SnapQuote snapQuote) {
        log.debug("SnapQuote: {}", snapQuote.getToken());
    }
}
