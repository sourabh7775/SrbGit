package com.angelbroking.smartapi.http.exceptions;

public class ApiKeyException extends SmartAPIException {
    private static final long serialVersionUID = 1L;

    public ApiKeyException(String message) {
        super(message);
    }

    public ApiKeyException(String message, String code) {
        super(message, code);
    }
}
