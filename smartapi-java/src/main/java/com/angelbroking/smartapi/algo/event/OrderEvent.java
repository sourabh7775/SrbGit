package com.angelbroking.smartapi.algo.event;

import org.json.JSONObject;
import org.springframework.context.ApplicationEvent;

public class OrderEvent extends ApplicationEvent {
    private final JSONObject orderData;

    public OrderEvent(Object source, JSONObject orderData) {
        super(source);
        this.orderData = orderData;
    }

    public JSONObject getOrderData() { return orderData; }
}
