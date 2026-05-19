package com.angelbroking.smartapi.models;

import com.google.gson.annotations.SerializedName;

public class TokenSet {
    @SerializedName("jwtToken") public String jwtToken;
    @SerializedName("refreshToken") public String refreshToken;
    @SerializedName("feedToken") public String feedToken;
}
