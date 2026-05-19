package com.angelbroking.smartapi.smartstream.models;

import lombok.Data;

@Data
public class SmartApiBBSInfo {
    private short flag;
    private long quantity;
    private long price;
    private int numberOfOrders;
}
