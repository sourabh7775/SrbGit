package com.angelbroking.smartapi.smartstream.models;

public enum SmartStreamError {
    CONNECTION_FAILED("Connection to SmartStream failed"),
    SUBSCRIPTION_FAILED("Subscription to SmartStream failed"),
    UNSUBSCRIPTION_FAILED("Unsubscription from SmartStream failed"),
    AUTHENTICATION_FAILED("Authentication to SmartStream failed"),
    PING_FAILED("Ping to SmartStream failed"),
    UNKNOWN_ERROR("Unknown SmartStream error");

    private final String message;

    SmartStreamError(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
