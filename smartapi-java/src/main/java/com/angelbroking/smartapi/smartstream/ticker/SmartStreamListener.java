package com.angelbroking.smartapi.smartstream.ticker;

import com.angelbroking.smartapi.smartstream.models.*;

public interface SmartStreamListener {
    void onConnected();
    void onDisconnected();
    void onError(SmartStreamError error);
    void onLTPArrival(LTP ltp);
    void onQuoteArrival(Quote quote);
    void onSnapQuoteArrival(SnapQuote snapQuote);
}
