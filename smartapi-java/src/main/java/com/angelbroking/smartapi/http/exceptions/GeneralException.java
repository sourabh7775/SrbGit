package com.angelbroking.smartapi.http.exceptions;

public class GeneralException extends SmartAPIException {
    private static final long serialVersionUID = 1L;

    public GeneralException(String message) {
        super(message);
    }

    public GeneralException(String message, String code) {
        super(message, code);
    }
}
