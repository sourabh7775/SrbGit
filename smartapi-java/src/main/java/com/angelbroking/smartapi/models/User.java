package com.angelbroking.smartapi.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class User {
    @SerializedName("jwtToken") public String accessToken;
    @SerializedName("refreshToken") public String refreshToken;
    @SerializedName("feedToken") public String feedToken;
    @SerializedName("clientcode") public String clientCode;
    @SerializedName("name") public String name;
    @SerializedName("email") public String email;
    @SerializedName("mobileno") public String mobile;
    @SerializedName("exchanges") public List<String> exchanges;
    @SerializedName("products") public List<String> products;
    @SerializedName("brokerid") public String brokerId;
}
