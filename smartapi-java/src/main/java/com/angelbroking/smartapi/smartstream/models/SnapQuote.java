package com.angelbroking.smartapi.smartstream.models;

import lombok.Data;
import java.util.List;

@Data
public class SnapQuote {
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
    private long lastTradedTimestamp;
    private long openInterest;
    private long openInterestDayHigh;
    private long openInterestDayLow;
    private double netChange;
    private double upperCircuitLimit;
    private double lowerCircuitLimit;
    private double week52HighPrice;
    private double week52LowPrice;
    private List<SmartApiBBSInfo> bestFiveBuyData;
    private List<SmartApiBBSInfo> bestFiveSellData;
}
