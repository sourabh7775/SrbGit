package com.angelbroking.smartapi.smartstream.models;

public enum SmartStreamAction {
    SUBSCRIBE(1),
    UNSUBSCRIBE(0);

    private final int val;

    SmartStreamAction(int val) {
        this.val = val;
    }

    public int getVal() {
        return val;
    }
}
