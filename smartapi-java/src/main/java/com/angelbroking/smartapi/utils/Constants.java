package com.angelbroking.smartapi.utils;

public class Constants {

    // Product types
    public static final String PRODUCT_DELIVERY = "DELIVERY";
    public static final String PRODUCT_CARRYFORWARD = "CARRYFORWARD";
    public static final String PRODUCT_MARGIN = "MARGIN";
    public static final String PRODUCT_INTRADAY = "INTRADAY";
    public static final String PRODUCT_BO = "BO";

    // Order types
    public static final String ORDER_TYPE_MARKET = "MARKET";
    public static final String ORDER_TYPE_LIMIT = "LIMIT";
    public static final String ORDER_TYPE_STOPLOSS_LIMIT = "STOPLOSS_LIMIT";
    public static final String ORDER_TYPE_STOPLOSS_MARKET = "STOPLOSS_MARKET";

    // Transaction types
    public static final String TRANSACTION_TYPE_BUY = "BUY";
    public static final String TRANSACTION_TYPE_SELL = "SELL";

    // Variety
    public static final String VARIETY_NORMAL = "NORMAL";
    public static final String VARIETY_AMO = "AMO";
    public static final String VARIETY_STOPLOSS = "STOPLOSS";
    public static final String VARIETY_ROBO = "ROBO";

    // Duration
    public static final String DURATION_DAY = "DAY";
    public static final String DURATION_IOC = "IOC";

    // Exchanges
    public static final String EXCHANGE_BSE = "BSE";
    public static final String EXCHANGE_NSE = "NSE";
    public static final String EXCHANGE_NFO = "NFO";
    public static final String EXCHANGE_MCX = "MCX";
    public static final String EXCHANGE_BFO = "BFO";
    public static final String EXCHANGE_CDS = "CDS";

    // SmartStream subscription modes
    public static final int MODE_LTP = 1;
    public static final int MODE_QUOTE = 2;
    public static final int MODE_SNAP_QUOTE = 3;
    public static final int MODE_DEPTH20 = 4;

    // Binary packet offsets
    public static final int SUBSCRIPTION_MODE_OFFSET = 0;
    public static final int EXCHANGE_TYPE_OFFSET = 1;
    public static final int TOKEN_OFFSET = 2;
    public static final int SEQUENCE_NUMBER_OFFSET = 27;
    public static final int EXCHANGE_TIMESTAMP_OFFSET = 35;
    public static final int LAST_TRADED_PRICE_OFFSET = 43;
    public static final int LAST_TRADED_QUANTITY_OFFSET = 51;
    public static final int AVERAGE_TRADED_PRICE_OFFSET = 59;
    public static final int VOLUME_TRADE_FOR_THE_DAY_OFFSET = 67;
    public static final int TOTAL_BUY_QUANTITY_OFFSET = 75;
    public static final int TOTAL_SELL_QUANTITY_OFFSET = 83;
    public static final int OPEN_PRICE_OF_THE_DAY_OFFSET = 91;
    public static final int HIGH_PRICE_OF_THE_DAY_OFFSET = 99;
    public static final int LOW_PRICE_OF_THE_DAY_OFFSET = 107;
    public static final int CLOSED_PRICE_OFFSET = 115;
    public static final int LAST_TRADED_TIMESTAMP_OFFSET = 123;
    public static final int OPEN_INTEREST_OFFSET = 131;
    public static final int OI_DAY_HIGH_OFFSET = 139;
    public static final int OI_DAY_LOW_OFFSET = 147;
    public static final int NET_CHANGE_OFFSET = 155;
    public static final int UPPER_CIRCUIT_LIMIT_OFFSET = 163;
    public static final int LOWER_CIRCUIT_LIMIT_OFFSET = 171;
    public static final int WEEK_52_HIGH_PRICE_OFFSET = 179;
    public static final int WEEK_52_LOW_PRICE_OFFSET = 187;
    public static final int BEST_5_BUY_DATA_OFFSET = 195;
    public static final int BEST_5_SELL_DATA_OFFSET = 315;
    public static final int PACKET_RECEIVED_TIME_OFFSET = 435;

    // Depth20 offsets
    public static final int BEST_20_BUY_DATA_OFFSET = 195;
    public static final int BEST_20_SELL_DATA_OFFSET = 915;
}
