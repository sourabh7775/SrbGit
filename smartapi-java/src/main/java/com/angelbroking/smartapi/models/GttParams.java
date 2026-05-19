package com.angelbroking.smartapi.models;

import java.util.List;

public class GttParams {
    public String tradingsymbol;
    public String symboltoken;
    public String exchange;
    public String transactiontype;
    public String producttype;
    public int qty;
    public String discqty;
    public String timeperiod;
    public double price;
    public List<Double> triggerprice;
    public String ordertype;
}
