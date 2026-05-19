package com.angelbroking.smartapi.http.exceptions;

public class PermissionException extends SmartAPIException {
    private static final long serialVersionUID = 1L;

    public PermissionException(String message) {
        super(message);
    }

    public PermissionException(String message, String code) {
        super(message, code);
    }
}
