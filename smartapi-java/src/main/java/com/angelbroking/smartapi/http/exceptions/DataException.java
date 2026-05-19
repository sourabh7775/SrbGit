package com.angelbroking.smartapi.http.exceptions;

public class DataException extends SmartAPIException {
    private static final long serialVersionUID = 1L;

    public DataException(String message) {
        super(message);
    }

    public DataException(String message, String code) {
        super(message, code);
    }
}
