package com.angelbroking.smartapi.http.exceptions;

public class InputException extends SmartAPIException {
    private static final long serialVersionUID = 1L;

    public InputException(String message) {
        super(message);
    }

    public InputException(String message, String code) {
        super(message, code);
    }
}
