package com.angelbroking.smartapi.orderupdate;

import org.json.JSONObject;

public interface OrderUpdateListner {
    void onConnected();
    void onDisconnected();
    void onError(Exception e);
    void onOrderUpdate(JSONObject order);
}
