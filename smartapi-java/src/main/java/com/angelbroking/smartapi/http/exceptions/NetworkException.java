package com.angelbroking.smartapi.http.exceptions;

public class NetworkException extends SmartAPIException {
    private static final long serialVersionUID = 1L;

    public NetworkException(String message) {
        super(message);
    }

    public NetworkException(String message, String code) {
        super(message, code);
    }
}
