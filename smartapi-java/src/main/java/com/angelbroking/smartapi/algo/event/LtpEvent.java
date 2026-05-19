package com.angelbroking.smartapi.algo.event;

import org.springframework.context.ApplicationEvent;

public class LtpEvent extends ApplicationEvent {
    private final String token;
    private final double ltp;

    public LtpEvent(Object source, String token, double ltp) {
        super(source);
        this.token = token;
        this.ltp = ltp;
    }

    public String getToken() { return token; }
    public double getLtp() { return ltp; }
}
