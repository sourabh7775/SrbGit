package com.angelbroking.smartapi.algo.strategy;

import com.angelbroking.smartapi.algo.Login;
import com.angelbroking.smartapi.algo.records.Instrument;
import com.angelbroking.smartapi.algo.records.PriceData;
import com.angelbroking.smartapi.algo.service.InstrumentService;
import com.angelbroking.smartapi.models.OrderParams;
import com.angelbroking.smartapi.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * NIFTY Momentum CE/PE Buy Strategy
 *
 * Entry rule:
 *   - Fetch 5-min NIFTY candles for the current session.
 *   - Detect the most recent swing high and swing low from those candles.
 *   - If the latest candle closes ABOVE the last swing high → BUY ATM CE (bullish breakout).
 *   - If the latest candle closes BELOW the last swing low  → BUY ATM PE (bearish breakdown).
 *
 * Strike selection:
 *   - ATM = round(NIFTY spot LTP / 50) * 50
 *   - Nearest weekly expiry from the instruments DB.
 *
 * Risk management:
 *   - SL     = entry premium × (1 – SL_PERCENT)    [default 30% below entry]
 *   - Target = entry premium × (1 + TARGET_PERCENT) [default 60% above entry]
 *   - Both SL and target orders are placed immediately after the entry fill.
 *
 * Prerequisites:
 *   1. Call POST /v1/login first so that Login.smartConnect is initialised.
 *   2. Call GET  /v1/instruments to populate the instruments DB with NFO data.
 */
@Service
@Slf4j
public class NiftyMomentumStrategy {

    private static final String NIFTY_INDEX_TOKEN  = "99926000";
    private static final String NIFTY_INDEX_SYMBOL = "Nifty 50";
    private static final String NIFTY_FUTURES_TOKEN = "35001";

    private static final String NSE_EXCHANGE  = "NSE";
    private static final String NFO_EXCHANGE  = "NFO";
    private static final int    NIFTY_LOT_SIZE = 50;
    private static final double SL_PERCENT     = 30.0;
    private static final double TARGET_PERCENT = 60.0;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Autowired
    private InstrumentService instrumentService;

    public String execute() {
        if (Login.smartConnect == null) {
            return "Not logged in — call POST /v1/login first";
        }
        try {
            double niftySpot = fetchNiftyLtp();
            log.info("NIFTY spot LTP: {}", niftySpot);

            List<PriceData> candles = fetchTodayCandles();
            if (candles.size() < 5) {
                return "Not enough candle data yet (need ≥ 5 candles, got " + candles.size() + ")";
            }

            Signal signal = detectSignal(candles);
            log.info("Signal: {}", signal);
            if (signal == Signal.NONE) {
                return "No breakout signal — price is inside the swing range";
            }

            double atmStrike  = roundToNearest50(niftySpot);
            String optionType = signal == Signal.BULLISH ? "CE" : "PE";
            log.info("Targeting strike {} {}", atmStrike, optionType);

            List<String> expiries = instrumentService.getExpiries();
            if (expiries.isEmpty()) {
                return "No expiries in DB — call GET /v1/instruments first";
            }
            String nearestExpiry = selectNearestExpiry(expiries);
            log.info("Using expiry: {}", nearestExpiry);

            Optional<Instrument> instOpt =
                    instrumentService.findByExpiryStrikeAndType(nearestExpiry, atmStrike, optionType);
            if (instOpt.isEmpty()) {
                return String.format("No NIFTY %s found at strike %.0f expiry %s",
                        optionType, atmStrike, nearestExpiry);
            }

            Instrument inst        = instOpt.get();
            String     optionSymbol = inst.getSymbol();
            String     token        = inst.getToken();
            log.info("Option: {} | Token: {}", optionSymbol, token);

            double entryPremium = fetchOptionLtp(token, optionSymbol);
            if (entryPremium <= 0) {
                return "Could not fetch a valid LTP for " + optionSymbol;
            }
            log.info("Entry premium: {}", entryPremium);

            String buyOrderId = placeMarketBuyOrder(optionSymbol, token);
            log.info("BUY order placed. Order ID: {}", buyOrderId);

            double slPrice  = roundToTick(entryPremium * (1 - SL_PERCENT / 100.0));
            double tgtPrice = roundToTick(entryPremium * (1 + TARGET_PERCENT / 100.0));

            String slOrderId  = placeSLOrder(optionSymbol, token, slPrice);
            String tgtOrderId = placeTargetOrder(optionSymbol, token, tgtPrice);
            log.info("SL={} id={} | Target={} id={}", slPrice, slOrderId, tgtPrice, tgtOrderId);

            return String.format(
                    "Trade entered | %s | Spot: %.2f | Strike: %.0f | Premium: %.2f | SL: %.2f | Target: %.2f",
                    optionSymbol, niftySpot, atmStrike, entryPremium, slPrice, tgtPrice);

        } catch (Exception e) {
            log.error("Strategy execution failed", e);
            return "Error: " + e.getMessage();
        }
    }

    private Signal detectSignal(List<PriceData> candles) {
        List<Double> swingHighs = swingHighPrices(candles);
        List<Double> swingLows  = swingLowPrices(candles);

        if (swingHighs.isEmpty() || swingLows.isEmpty()) {
            return Signal.NONE;
        }

        double latestClose   = candles.get(candles.size() - 1).getClose();
        double lastSwingHigh = swingHighs.get(swingHighs.size() - 1);
        double lastSwingLow  = swingLows.get(swingLows.size() - 1);

        log.info("Close: {} | Swing high: {} | Swing low: {}", latestClose, lastSwingHigh, lastSwingLow);

        if (latestClose > lastSwingHigh) return Signal.BULLISH;
        if (latestClose < lastSwingLow)  return Signal.BEARISH;
        return Signal.NONE;
    }

    private List<Double> swingHighPrices(List<PriceData> candles) {
        List<Double> result = new ArrayList<>();
        for (int i = 2; i < candles.size(); i++) {
            double h0 = candles.get(i - 2).getHigh();
            double h1 = candles.get(i - 1).getHigh();
            double h2 = candles.get(i).getHigh();
            if (h0 < h1 && h2 < h1) result.add(h1);
        }
        return result;
    }

    private List<Double> swingLowPrices(List<PriceData> candles) {
        List<Double> result = new ArrayList<>();
        for (int i = 2; i < candles.size(); i++) {
            double l0 = candles.get(i - 2).getLow();
            double l1 = candles.get(i - 1).getLow();
            double l2 = candles.get(i).getLow();
            if (l0 > l1 && l2 > l1) result.add(l1);
        }
        return result;
    }

    private double fetchNiftyLtp() throws Exception {
        JSONObject data = Login.smartConnect.getLTP(NSE_EXCHANGE, NIFTY_INDEX_SYMBOL, NIFTY_INDEX_TOKEN);
        if (data == null) throw new RuntimeException("getLTP returned null");
        return data.getDouble("ltp");
    }

    private List<PriceData> fetchTodayCandles() throws Exception {
        String today    = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String fromdate = today + " 09:15";
        String todate   = LocalDateTime.now().format(DATE_FMT);

        JSONObject req = new JSONObject();
        req.put("exchange",    NFO_EXCHANGE);
        req.put("symboltoken", NIFTY_FUTURES_TOKEN);
        req.put("interval",    "FIVE_MINUTE");
        req.put("fromdate",    fromdate);
        req.put("todate",      todate);

        JSONArray raw = Login.smartConnect.candleData(req);
        List<PriceData> candles = new ArrayList<>();
        for (int i = 0; i < raw.length(); i++) {
            JSONArray bar = raw.getJSONArray(i);
            candles.add(new PriceData(
                    bar.getString(0),
                    bar.getDouble(1),
                    bar.getDouble(2),
                    bar.getDouble(3),
                    bar.getDouble(4),
                    bar.getLong(5)
            ));
        }
        return candles;
    }

    private double fetchOptionLtp(String token, String symbol) {
        try {
            JSONObject data = Login.smartConnect.getLTP(NFO_EXCHANGE, symbol, token);
            return data != null ? data.getDouble("ltp") : 0.0;
        } catch (Exception e) {
            log.warn("Could not fetch LTP for {}: {}", symbol, e.getMessage());
            return 0.0;
        }
    }

    private String placeMarketBuyOrder(String symbol, String token) throws Exception {
        OrderParams p = new OrderParams();
        p.variety         = Constants.VARIETY_NORMAL;
        p.exchange        = NFO_EXCHANGE;
        p.tradingsymbol   = symbol;
        p.symboltoken     = token;
        p.transactiontype = Constants.TRANSACTION_TYPE_BUY;
        p.ordertype       = Constants.ORDER_TYPE_MARKET;
        p.producttype     = Constants.PRODUCT_INTRADAY;
        p.duration        = Constants.DURATION_DAY;
        p.quantity        = NIFTY_LOT_SIZE;
        p.price           = 0.0;
        JSONObject result = Login.smartConnect.placeOrder(p);
        return result != null ? result.optString("orderid", "") : "";
    }

    private String placeSLOrder(String symbol, String token, double slPrice) throws Exception {
        double triggerPrice = roundToTick(slPrice + 0.50);
        OrderParams p = new OrderParams();
        p.variety         = Constants.VARIETY_STOPLOSS;
        p.exchange        = NFO_EXCHANGE;
        p.tradingsymbol   = symbol;
        p.symboltoken     = token;
        p.transactiontype = Constants.TRANSACTION_TYPE_SELL;
        p.ordertype       = Constants.ORDER_TYPE_STOPLOSS_LIMIT;
        p.producttype     = Constants.PRODUCT_INTRADAY;
        p.duration        = Constants.DURATION_DAY;
        p.quantity        = NIFTY_LOT_SIZE;
        p.price           = slPrice;
        p.triggerprice    = triggerPrice;
        JSONObject result = Login.smartConnect.placeOrder(p);
        return result != null ? result.optString("orderid", "") : "";
    }

    private String placeTargetOrder(String symbol, String token, double targetPrice) throws Exception {
        OrderParams p = new OrderParams();
        p.variety         = Constants.VARIETY_NORMAL;
        p.exchange        = NFO_EXCHANGE;
        p.tradingsymbol   = symbol;
        p.symboltoken     = token;
        p.transactiontype = Constants.TRANSACTION_TYPE_SELL;
        p.ordertype       = Constants.ORDER_TYPE_LIMIT;
        p.producttype     = Constants.PRODUCT_INTRADAY;
        p.duration        = Constants.DURATION_DAY;
        p.quantity        = NIFTY_LOT_SIZE;
        p.price           = targetPrice;
        JSONObject result = Login.smartConnect.placeOrder(p);
        return result != null ? result.optString("orderid", "") : "";
    }

    private double roundToNearest50(double price) {
        return Math.round(price / 50.0) * 50.0;
    }

    private double roundToTick(double price) {
        return Math.round(price / 0.05) * 0.05;
    }

    private String selectNearestExpiry(List<String> expiries) {
        return expiries.stream()
                .min((a, b) -> {
                    try {
                        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("ddMMMyyyy");
                        return LocalDate.parse(a, fmt).compareTo(LocalDate.parse(b, fmt));
                    } catch (Exception e) {
                        return a.compareTo(b);
                    }
                })
                .orElse(expiries.get(0));
    }

    public enum Signal {
        BULLISH, BEARISH, NONE
    }
}
