package com.angelbroking.smartapi.orderupdate;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OrderUpdateServiceImpl implements OrderUpdateListner {

    private static final Logger log = LoggerFactory.getLogger(OrderUpdateServiceImpl.class);

    @Override
    public void onConnected() {
        log.info("Order update service connected");
    }

    @Override
    public void onDisconnected() {
        log.warn("Order update service disconnected");
    }

    @Override
    public void onError(Exception e) {
        log.error("Order update service error", e);
    }

    @Override
    public void onOrderUpdate(JSONObject order) {
        log.info("Order update: {}", order);
    }
}
