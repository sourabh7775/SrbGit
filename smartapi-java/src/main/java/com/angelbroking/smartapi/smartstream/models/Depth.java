package com.angelbroking.smartapi.smartstream.models;

import lombok.Data;
import java.util.List;

@Data
public class Depth {
    private byte subscriptionMode;
    private byte exchangeType;
    private String token;
    private long sequenceNumber;
    private long exchangeTimestamp;
    private long lastTradedPrice;
    private List<SmartApiBBSInfo> bestTwentyBuyData;
    private List<SmartApiBBSInfo> bestTwentySellData;
}
