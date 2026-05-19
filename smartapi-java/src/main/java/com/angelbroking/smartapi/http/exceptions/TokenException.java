package com.angelbroking.smartapi.http.exceptions;

public class TokenException extends SmartAPIException {
    private static final long serialVersionUID = 1L;

    public TokenException(String message) {
        super(message);
    }

    public TokenException(String message, String code) {
        super(message, code);
    }
}
