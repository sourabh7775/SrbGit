package com.angelbroking.smartapi.models;

import com.google.gson.annotations.SerializedName;

public class Trade {
    @SerializedName("exchange") public String exchange;
    @SerializedName("tradingsymbol") public String tradingSymbol;
    @SerializedName("symboltoken") public String symbolToken;
    @SerializedName("instrumenttype") public String instrumentType;
    @SerializedName("producttype") public String productType;
    @SerializedName("transactiontype") public String transactionType;
    @SerializedName("tradevalue") public double tradeValue;
    @SerializedName("quantity") public String quantity;
    @SerializedName("price") public double price;
    @SerializedName("carryforwardquantity") public String carryForwardQuantity;
    @SerializedName("carryforwardvalue") public double carryForwardValue;
    @SerializedName("buyquantity") public String buyQuantity;
    @SerializedName("buyvalue") public double buyValue;
    @SerializedName("buyprice") public double buyPrice;
    @SerializedName("sellquantity") public String sellQuantity;
    @SerializedName("sellvalue") public double sellValue;
    @SerializedName("sellprice") public double sellPrice;
    @SerializedName("realisedprofitandloss") public double realisedPnl;
    @SerializedName("unrealisedprofitandloss") public double unrealisedPnl;
    @SerializedName("day_buy_quantity") public String dayBuyQuantity;
    @SerializedName("day_buy_value") public double dayBuyValue;
    @SerializedName("day_buy_price") public double dayBuyPrice;
    @SerializedName("day_sell_quantity") public String daySellQuantity;
    @SerializedName("day_sell_value") public double daySellValue;
    @SerializedName("day_sell_price") public double daySellPrice;
}
