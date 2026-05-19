package com.angelbroking.smartapi.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class Gtt {
    @SerializedName("id") public String id;
    @SerializedName("tradingsymbol") public String tradingSymbol;
    @SerializedName("symboltoken") public String symbolToken;
    @SerializedName("exchange") public String exchange;
    @SerializedName("transactiontype") public String transactionType;
    @SerializedName("producttype") public String productType;
    @SerializedName("qty") public int qty;
    @SerializedName("price") public double price;
    @SerializedName("triggerprice") public List<Double> triggerPrice;
    @SerializedName("ordertype") public String orderType;
    @SerializedName("status") public String status;
    @SerializedName("createddate") public String createdDate;
    @SerializedName("updateddate") public String updatedDate;
    @SerializedName("expirydate") public String expiryDate;
    @SerializedName("timeperiod") public String timePeriod;
}
