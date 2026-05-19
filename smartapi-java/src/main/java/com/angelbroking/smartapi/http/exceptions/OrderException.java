package com.angelbroking.smartapi.http.exceptions;

public class OrderException extends SmartAPIException {
    private static final long serialVersionUID = 1L;

    public OrderException(String message) {
        super(message);
    }

    public OrderException(String message, String code) {
        super(message, code);
    }
}
