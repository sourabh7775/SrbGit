package com.angelbroking.smartapi.algo.order;

import com.angelbroking.smartapi.SmartConnect;
import com.angelbroking.smartapi.algo.Login;
import com.angelbroking.smartapi.http.exceptions.SmartAPIException;
import com.angelbroking.smartapi.models.OrderParams;
import com.angelbroking.smartapi.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class OrderProcessor {

    private static final double STOPLOSS_MARGIN = 3.0;

    private final Map<String, OrderState> activeOrders = new ConcurrentHashMap<>();

    public void trackOrder(String token, String orderId, double entryPrice,
                           double slPrice, double targetPrice, String transactionType) {
        activeOrders.put(token, new OrderState(orderId, entryPrice, slPrice, targetPrice, transactionType));
        log.info("Tracking order {} for token {}: entry={} sl={} target={}",
                orderId, token, entryPrice, slPrice, targetPrice);
    }

    public void onLtpUpdate(String token, double ltp) {
        OrderState state = activeOrders.get(token);
        if (state == null) return;

        boolean isBuy = "BUY".equals(state.transactionType);

        if (isBuy) {
            // Trail SL upward
            double newSl = ltp - STOPLOSS_MARGIN;
            if (newSl > state.slPrice) {
                state.slPrice = newSl;
                log.debug("Trailing SL updated to {} for token {}", newSl, token);
            }
            // Check exits
            if (ltp <= state.slPrice) {
                log.info("SL triggered at {} for token {}", ltp, token);
                squareOff(token, state, "SL");
            } else if (ltp >= state.targetPrice) {
                log.info("Target hit at {} for token {}", ltp, token);
                squareOff(token, state, "TARGET");
            }
        } else {
            // Short position — trail SL downward
            double newSl = ltp + STOPLOSS_MARGIN;
            if (newSl < state.slPrice) {
                state.slPrice = newSl;
            }
            if (ltp >= state.slPrice) {
                squareOff(token, state, "SL");
            } else if (ltp <= state.targetPrice) {
                squareOff(token, state, "TARGET");
            }
        }
    }

    private void squareOff(String token, OrderState state, String reason) {
        activeOrders.remove(token);
        log.info("Squaring off token {} reason={}", token, reason);
        try {
            SmartConnect sc = Login.smartConnect;
            if (sc == null) return;

            OrderParams params = new OrderParams();
            params.variety = Constants.VARIETY_NORMAL;
            params.tradingsymbol = token;
            params.symboltoken = token;
            params.transactiontype = "BUY".equals(state.transactionType) ? Constants.TRANSACTION_TYPE_SELL : Constants.TRANSACTION_TYPE_BUY;
            params.exchange = "NFO";
            params.ordertype = Constants.ORDER_TYPE_MARKET;
            params.producttype = Constants.PRODUCT_CARRYFORWARD;
            params.duration = Constants.DURATION_DAY;
            params.price = 0;
            params.quantity = 50;

            JSONObject result = sc.placeOrder(params);
            log.info("Square off order placed: {}", result);
        } catch (SmartAPIException | IOException e) {
            log.error("Square off failed for token {}", token, e);
        }
    }

    public boolean hasOpenPosition(String token) {
        return activeOrders.containsKey(token);
    }

    private static class OrderState {
        String orderId;
        double entryPrice;
        double slPrice;
        double targetPrice;
        String transactionType;

        OrderState(String orderId, double entryPrice, double slPrice, double targetPrice, String transactionType) {
            this.orderId = orderId;
            this.entryPrice = entryPrice;
            this.slPrice = slPrice;
            this.targetPrice = targetPrice;
            this.transactionType = transactionType;
        }
    }
}
