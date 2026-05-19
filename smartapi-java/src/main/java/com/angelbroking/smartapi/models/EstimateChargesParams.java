package com.angelbroking.smartapi.models;

import java.util.List;

public class EstimateChargesParams {
    public List<OrderItem> orders;

    public static class OrderItem {
        public String product_type;
        public String transaction_type;
        public int quantity;
        public double price;
        public String exchange;
        public String symbol_name;
        public String token;
    }
}
