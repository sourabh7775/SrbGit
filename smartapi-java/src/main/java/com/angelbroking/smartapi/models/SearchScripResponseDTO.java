package com.angelbroking.smartapi.models;

import com.google.gson.annotations.SerializedName;

public class SearchScripResponseDTO {
    @SerializedName("exchange") public String exchange;
    @SerializedName("tradingsymbol") public String tradingSymbol;
    @SerializedName("symboltoken") public String symbolToken;
}
