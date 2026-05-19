package com.angelbroking.smartapi.algo.records;

public record Order(
    String orderId,
    String symbol,
    String token,
    String exchange,
    String transactionType,
    String orderType,
    String productType,
    double price,
    double triggerPrice,
    int quantity,
    String variety,
    String status
) {}
