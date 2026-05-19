package com.angelbroking.smartapi.models;

import com.google.gson.annotations.SerializedName;

public class Order {
    @SerializedName("variety") public String variety;
    @SerializedName("orderid") public String orderId;
    @SerializedName("uniqueorderid") public String uniqueOrderId;
    @SerializedName("exchangeorderid") public String exchangeOrderId;
    @SerializedName("parentorderid") public String parentOrderId;
    @SerializedName("status") public String status;
    @SerializedName("tradingsymbol") public String tradingSymbol;
    @SerializedName("symboltoken") public String symbolToken;
    @SerializedName("instrumenttype") public String instrumentType;
    @SerializedName("exchange") public String exchange;
    @SerializedName("transactiontype") public String transactionType;
    @SerializedName("producttype") public String productType;
    @SerializedName("duration") public String duration;
    @SerializedName("price") public double price;
    @SerializedName("triggerprice") public double triggerPrice;
    @SerializedName("quantity") public String quantity;
    @SerializedName("disclosedquantity") public String disclosedQuantity;
    @SerializedName("filledshares") public String filledShares;
    @SerializedName("unfilledshares") public String unfilledShares;
    @SerializedName("optiontype") public String optionType;
    @SerializedName("strikeprice") public double strikePrice;
    @SerializedName("expirydate") public String expiryDate;
    @SerializedName("lotsize") public String lotSize;
    @SerializedName("cancelsize") public String cancelSize;
    @SerializedName("averageprice") public double averagePrice;
    @SerializedName("text") public String text;
    @SerializedName("ordertag") public String orderTag;
    @SerializedName("ordertype") public String orderType;
    @SerializedName("orderupdatetime") public String orderUpdateTime;
    @SerializedName("exchtime") public String exchTime;
    @SerializedName("exchorderupdatetime") public String exchOrderUpdateTime;
    @SerializedName("updatetime") public String updateTime;
    @SerializedName("squareoff") public double squareOff;
    @SerializedName("stoploss") public double stopLoss;
    @SerializedName("trailingstoploss") public double trailingStopLoss;
}
