package com.angelbroking.smartapi.smartstream.models;

import lombok.Data;

@Data
public class LTP {
    private byte subscriptionMode;
    private byte exchangeType;
    private String token;
    private long sequenceNumber;
    private long exchangeTimestamp;
    private long lastTradedPrice;
}
