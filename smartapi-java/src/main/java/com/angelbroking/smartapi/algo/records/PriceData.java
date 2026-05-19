package com.angelbroking.smartapi.algo.records;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PriceData {
    private final String date;
    private final double open;
    private final double high;
    private final double low;
    private final double close;
    private final long volume;
}
