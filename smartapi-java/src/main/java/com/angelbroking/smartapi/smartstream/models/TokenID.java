package com.angelbroking.smartapi.smartstream.models;

public class TokenID {
    private ExchangeType exchangeType;
    private String token;

    public TokenID(ExchangeType exchangeType, String token) {
        this.exchangeType = exchangeType;
        this.token = token;
    }

    public ExchangeType getExchangeType() { return exchangeType; }
    public void setExchangeType(ExchangeType exchangeType) { this.exchangeType = exchangeType; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
}
