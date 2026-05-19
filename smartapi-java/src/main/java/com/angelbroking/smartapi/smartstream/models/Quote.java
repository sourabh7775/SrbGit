package com.angelbroking.smartapi.smartstream.models;

import lombok.Data;

@Data
public class Quote {
    private byte subscriptionMode;
    private byte exchangeType;
    private String token;
    private long sequenceNumber;
    private long exchangeTimestamp;
    private long lastTradedPrice;
    private long lastTradedQuantity;
    private long averageTradedPrice;
    private long volumeTradeForTheDay;
    private double totalBuyQuantity;
    private double totalSellQuantity;
    private long openPriceOfTheDay;
    private long highPriceOfTheDay;
    private long lowPriceOfTheDay;
    private long closedPrice;
}
