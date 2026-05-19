package com.angelbroking.smartapi.utils;

import com.angelbroking.smartapi.smartstream.models.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class ByteUtils {

    public static LTP mapToLTP(byte[] data) {
        LTP ltp = new LTP();
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        ltp.setSubscriptionMode(buffer.get(Constants.SUBSCRIPTION_MODE_OFFSET));
        ltp.setExchangeType(buffer.get(Constants.EXCHANGE_TYPE_OFFSET));
        ltp.setToken(getTokenID(data));
        ltp.setSequenceNumber(buffer.getLong(Constants.SEQUENCE_NUMBER_OFFSET));
        ltp.setExchangeTimestamp(buffer.getLong(Constants.EXCHANGE_TIMESTAMP_OFFSET));
        ltp.setLastTradedPrice(buffer.getLong(Constants.LAST_TRADED_PRICE_OFFSET));
        return ltp;
    }

    public static Quote mapToQuote(byte[] data) {
        Quote quote = new Quote();
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        quote.setSubscriptionMode(buffer.get(Constants.SUBSCRIPTION_MODE_OFFSET));
        quote.setExchangeType(buffer.get(Constants.EXCHANGE_TYPE_OFFSET));
        quote.setToken(getTokenID(data));
        quote.setSequenceNumber(buffer.getLong(Constants.SEQUENCE_NUMBER_OFFSET));
        quote.setExchangeTimestamp(buffer.getLong(Constants.EXCHANGE_TIMESTAMP_OFFSET));
        quote.setLastTradedPrice(buffer.getLong(Constants.LAST_TRADED_PRICE_OFFSET));
        quote.setLastTradedQuantity(buffer.getLong(Constants.LAST_TRADED_QUANTITY_OFFSET));
        quote.setAverageTradedPrice(buffer.getLong(Constants.AVERAGE_TRADED_PRICE_OFFSET));
        quote.setVolumeTradeForTheDay(buffer.getLong(Constants.VOLUME_TRADE_FOR_THE_DAY_OFFSET));
        quote.setTotalBuyQuantity(buffer.getDouble(Constants.TOTAL_BUY_QUANTITY_OFFSET));
        quote.setTotalSellQuantity(buffer.getDouble(Constants.TOTAL_SELL_QUANTITY_OFFSET));
        quote.setOpenPriceOfTheDay(buffer.getLong(Constants.OPEN_PRICE_OF_THE_DAY_OFFSET));
        quote.setHighPriceOfTheDay(buffer.getLong(Constants.HIGH_PRICE_OF_THE_DAY_OFFSET));
        quote.setLowPriceOfTheDay(buffer.getLong(Constants.LOW_PRICE_OF_THE_DAY_OFFSET));
        quote.setClosedPrice(buffer.getLong(Constants.CLOSED_PRICE_OFFSET));
        return quote;
    }

    public static SnapQuote mapToSnapQuote(byte[] data) {
        SnapQuote snapQuote = new SnapQuote();
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        snapQuote.setSubscriptionMode(buffer.get(Constants.SUBSCRIPTION_MODE_OFFSET));
        snapQuote.setExchangeType(buffer.get(Constants.EXCHANGE_TYPE_OFFSET));
        snapQuote.setToken(getTokenID(data));
        snapQuote.setSequenceNumber(buffer.getLong(Constants.SEQUENCE_NUMBER_OFFSET));
        snapQuote.setExchangeTimestamp(buffer.getLong(Constants.EXCHANGE_TIMESTAMP_OFFSET));
        snapQuote.setLastTradedPrice(buffer.getLong(Constants.LAST_TRADED_PRICE_OFFSET));
        snapQuote.setLastTradedQuantity(buffer.getLong(Constants.LAST_TRADED_QUANTITY_OFFSET));
        snapQuote.setAverageTradedPrice(buffer.getLong(Constants.AVERAGE_TRADED_PRICE_OFFSET));
        snapQuote.setVolumeTradeForTheDay(buffer.getLong(Constants.VOLUME_TRADE_FOR_THE_DAY_OFFSET));
        snapQuote.setTotalBuyQuantity(buffer.getDouble(Constants.TOTAL_BUY_QUANTITY_OFFSET));
        snapQuote.setTotalSellQuantity(buffer.getDouble(Constants.TOTAL_SELL_QUANTITY_OFFSET));
        snapQuote.setOpenPriceOfTheDay(buffer.getLong(Constants.OPEN_PRICE_OF_THE_DAY_OFFSET));
        snapQuote.setHighPriceOfTheDay(buffer.getLong(Constants.HIGH_PRICE_OF_THE_DAY_OFFSET));
        snapQuote.setLowPriceOfTheDay(buffer.getLong(Constants.LOW_PRICE_OF_THE_DAY_OFFSET));
        snapQuote.setClosedPrice(buffer.getLong(Constants.CLOSED_PRICE_OFFSET));
        snapQuote.setLastTradedTimestamp(buffer.getLong(Constants.LAST_TRADED_TIMESTAMP_OFFSET));
        snapQuote.setOpenInterest(buffer.getLong(Constants.OPEN_INTEREST_OFFSET));
        snapQuote.setOpenInterestDayHigh(buffer.getLong(Constants.OI_DAY_HIGH_OFFSET));
        snapQuote.setOpenInterestDayLow(buffer.getLong(Constants.OI_DAY_LOW_OFFSET));
        snapQuote.setNetChange(buffer.getDouble(Constants.NET_CHANGE_OFFSET));
        snapQuote.setUpperCircuitLimit(buffer.getDouble(Constants.UPPER_CIRCUIT_LIMIT_OFFSET));
        snapQuote.setLowerCircuitLimit(buffer.getDouble(Constants.LOWER_CIRCUIT_LIMIT_OFFSET));
        snapQuote.setWeek52HighPrice(buffer.getDouble(Constants.WEEK_52_HIGH_PRICE_OFFSET));
        snapQuote.setWeek52LowPrice(buffer.getDouble(Constants.WEEK_52_LOW_PRICE_OFFSET));
        snapQuote.setBestFiveBuyData(getBestFiveBuyData(data));
        snapQuote.setBestFiveSellData(getBestFiveSellData(data));
        return snapQuote;
    }

    public static String getTokenID(byte[] data) {
        byte[] tokenBytes = new byte[25];
        System.arraycopy(data, Constants.TOKEN_OFFSET, tokenBytes, 0, 25);
        return new String(tokenBytes).trim().replace("\0", "");
    }

    public static List<SmartApiBBSInfo> getBestFiveBuyData(byte[] data) {
        return parseBestFiveData(data, Constants.BEST_5_BUY_DATA_OFFSET);
    }

    public static List<SmartApiBBSInfo> getBestFiveSellData(byte[] data) {
        return parseBestFiveData(data, Constants.BEST_5_SELL_DATA_OFFSET);
    }

    private static List<SmartApiBBSInfo> parseBestFiveData(byte[] data, int startOffset) {
        List<SmartApiBBSInfo> list = new ArrayList<>();
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 5; i++) {
            int offset = startOffset + (i * 24);
            SmartApiBBSInfo info = new SmartApiBBSInfo();
            info.setFlag(buffer.getShort(offset));
            info.setQuantity(buffer.getLong(offset + 2));
            info.setPrice(buffer.getLong(offset + 10));
            info.setNumberOfOrders(buffer.getInt(offset + 18));
            list.add(info);
        }
        return list;
    }

    public static List<SmartApiBBSInfo> getBestTwentyBuyData(byte[] data) {
        return parseBestTwentyData(data, Constants.BEST_20_BUY_DATA_OFFSET);
    }

    public static List<SmartApiBBSInfo> getBestTwentySellData(byte[] data) {
        return parseBestTwentyData(data, Constants.BEST_20_SELL_DATA_OFFSET);
    }

    private static List<SmartApiBBSInfo> parseBestTwentyData(byte[] data, int startOffset) {
        List<SmartApiBBSInfo> list = new ArrayList<>();
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 20; i++) {
            int offset = startOffset + (i * 36);
            if (offset + 36 > data.length) break;
            SmartApiBBSInfo info = new SmartApiBBSInfo();
            info.setFlag(buffer.getShort(offset));
            info.setQuantity(buffer.getLong(offset + 2));
            info.setPrice(buffer.getLong(offset + 10));
            info.setNumberOfOrders(buffer.getInt(offset + 18));
            list.add(info);
        }
        return list;
    }
}
