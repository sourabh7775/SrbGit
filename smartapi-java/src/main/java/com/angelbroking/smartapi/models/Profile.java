package com.angelbroking.smartapi.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class Profile {
    @SerializedName("clientcode") public String clientCode;
    @SerializedName("name") public String name;
    @SerializedName("email") public String email;
    @SerializedName("mobileno") public String mobile;
    @SerializedName("exchanges") public List<String> exchanges;
    @SerializedName("products") public List<String> products;
    @SerializedName("lastlogintime") public String lastLoginTime;
    @SerializedName("brokerid") public String brokerId;
}
