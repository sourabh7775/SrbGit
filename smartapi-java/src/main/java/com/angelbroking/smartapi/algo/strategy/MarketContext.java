package com.angelbroking.smartapi.algo.strategy;

import com.angelbroking.smartapi.algo.records.PriceData;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Snapshot of all market data collected before asking Claude to decide.
 * Built fresh on every strategy invocation so Claude always sees live data.
 */
@Getter
@Builder
public class MarketContext {
    private final double       niftySpot;
    private final double       atmStrike;
    private final List<PriceData> recentCandles;  // 5-min candles, today's session
    private final List<Double> swingHighs;         // ascending list of swing-high prices
    private final List<Double> swingLows;           // ascending list of swing-low prices
    private final double       atmCeLtp;
    private final double       atmPeLtp;
    private final String       greeksJson;          // raw JSON from SmartAPI optionGreek()
    private final double       putCallRatio;
    private final boolean      hasOpenPosition;
    private final double       dailyPnl;            // running P&L tracked by RiskManager
    private final String       timestamp;           // "yyyy-MM-dd HH:mm"
}
