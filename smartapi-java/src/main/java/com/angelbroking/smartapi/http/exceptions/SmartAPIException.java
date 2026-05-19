package com.angelbroking.smartapi.http.exceptions;

public class SmartAPIException extends Exception {
    private static final long serialVersionUID = 1L;
    public String message;
    public String code;

    public SmartAPIException(String message) {
        super(message);
        this.message = message;
    }

    public SmartAPIException(String message, String code) {
        super(message);
        this.message = message;
        this.code = code;
    }
}
