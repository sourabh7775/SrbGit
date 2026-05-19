package com.angelbroking.smartapi.algo.strategy;

import com.angelbroking.smartapi.algo.Login;
import com.angelbroking.smartapi.algo.records.PriceData;
import com.angelbroking.smartapi.algo.records.Symbol;
import com.angelbroking.smartapi.algo.service.InstrumentService;
import com.angelbroking.smartapi.models.OrderParams;
import com.angelbroking.smartapi.utils.Constants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 *   3. Update NIFTY_FUTURES_TOKEN to the current-month/week NIFTY futures token
 *      (used only for candle data; the spot token is used for LTP).
 */
@Service
@Slf4j
public class NiftyMomentumStrategy {

    // NIFTY 50 index token on NSE (for spot LTP)
    private static final String NIFTY_INDEX_TOKEN = "99926000";
    private static final String NIFTY_INDEX_SYMBOL = "Nifty 50";

    // NIFTY near-month futures token on NFO (for candle data).
    // Update this to the active futures contract token before each expiry.
    private static final String NIFTY_FUTURES_TOKEN = "35001";

    private static final String NSE_EXCHANGE = "NSE";
    private static final String NFO_EXCHANGE = "NFO";
    private static final int    NIFTY_LOT_SIZE    = 50;
    private static final double SL_PERCENT        = 0.30; // 30% below entry premium
    private static final double TARGET_PERCENT    = 0.60; // 60% above entry premium
    private static final String CANDLE_INTERVAL   = "FIVE_MINUTE";
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Autowired
    private InstrumentService instrumentService;

    // -----------------------------------------------------------------------
    // Public entry point
    // -----------------------------------------------------------------------

    public String execute() {
        if (Login.smartConnect == null) {
            return "Not logged in — call POST /v1/login first";
        }

        try {
            // 1. NIFTY spot LTP
            double niftySpot = fetchNiftyLtp();
            log.info("NIFTY spot LTP: {}", niftySpot);

            // 2. Fetch today's 5-min candles
            List<PriceData> candles = fetchTodayCandles();
            if (candles.size() < 5) {
                return "Not enough candle data yet (need ≥ 5 candles)";
            }

            // 3. Detect breakout / breakdown signal
            Signal signal = detectSignal(candles);
            log.info("Signal: {}", signal);
            if (signal == Signal.NONE) {
                return "No breakout signal — price is inside the swing range";
            }

            // 4. ATM strike = round spot to nearest 50
            double atmStrike  = roundToNearest50(niftySpot);
            String optionType = signal == Signal.BULLISH ? "CE" : "PE";
            log.info("Targeting strike {} {}", atmStrike, optionType);

            // 5. Nearest expiry from DB
            List<String> expiries = instrumentService.getAllExpiries();
            if (expiries.isEmpty()) {
                return "No expiries in DB — call GET /v1/instruments first";
            }
            String nearestExpiry = selectNearestExpiry(expiries);
            log.info("Using expiry: {}", nearestExpiry);

            // 6. Resolve option symbol + token
            List<String> options =
                    instrumentService.getOptionsByExpiryAndStrike(nearestExpiry, atmStrike);
            Optional<String> symbolOpt = options.stream()
                    .filter(s -> s.startsWith("NIFTY") && s.endsWith(optionType))
                    .findFirst();
            if (symbolOpt.isEmpty()) {
                return String.format("No NIFTY %s found at strike %.0f expiry %s",
                        optionType, atmStrike, nearestExpiry);
            }

            String optionSymbol = symbolOpt.get();
            String token        = instrumentService.getToken(new Symbol(optionSymbol));
            log.info("Option: {} | Token: {}", optionSymbol, token);

            // 7. Entry premium (current LTP of the option)
            double entryPremium = fetchOptionLtp(token, optionSymbol);
            if (entryPremium <= 0) {
                return "Could not fetch a valid LTP for " + optionSymbol;
            }
            log.info("Entry premium: {}", entryPremium);

            // 8. Place MARKET buy order
            String buyOrderId = placeMarketBuyOrder(optionSymbol, token);
            log.info("BUY order placed. Order ID: {}", buyOrderId);

            // 9. SL and target prices based on premium
            double slPrice  = roundToTick(entryPremium * (1 - SL_PERCENT));
            double tgtPrice = roundToTick(entryPremium * (1 + TARGET_PERCENT));

            // 10. SL order (SL-LIMIT)
            String slOrderId = placeSLOrder(optionSymbol, token, slPrice);
            log.info("SL order placed at {}. Order ID: {}", slPrice, slOrderId);

            // 11. Target LIMIT sell order
            String tgtOrderId = placeTargetOrder(optionSymbol, token, tgtPrice);
            log.info("Target order placed at {}. Order ID: {}", tgtPrice, tgtOrderId);

            return String.format(
                    "Trade entered | %s | Spot: %.2f | Strike: %.0f | Premium: %.2f | SL: %.2f | Target: %.2f",
                    optionSymbol, niftySpot, atmStrike, entryPremium, slPrice, tgtPrice);

        } catch (Exception e) {
            log.error("Strategy execution failed", e);
            return "Error: " + e.getMessage();
        }
    }

    // -----------------------------------------------------------------------
    // Signal detection
    // -----------------------------------------------------------------------

    /**
     * BULLISH  → latest close > most recent swing high  (breakout)
     * BEARISH  → latest close < most recent swing low   (breakdown)
     * NONE     → price is inside the swing range
     */
    private Signal detectSignal(List<PriceData> candles) {
        List<Double> swingHighs = swingHighPrices(candles);
        List<Double> swingLows  = swingLowPrices(candles);

        if (swingHighs.isEmpty() || swingLows.isEmpty()) {
            log.info("Not enough swing points yet");
            return Signal.NONE;
        }

        double latestClose   = candles.get(candles.size() - 1).getClose();
        double lastSwingHigh = swingHighs.get(swingHighs.size() - 1);
        double lastSwingLow  = swingLows.get(swingLows.size() - 1);

        log.info("Close: {} | Last swing high: {} | Last swing low: {}",
                latestClose, lastSwingHigh, lastSwingLow);

        if (latestClose > lastSwingHigh) return Signal.BULLISH;
        if (latestClose < lastSwingLow)  return Signal.BEARISH;
        return Signal.NONE;
    }

    /**
     * A swing high at candle i-1 when: high[i-2] < high[i-1] AND high[i] < high[i-1]
     * (same logic as Login.findSwingHighs, but returns the actual high price)
     */
    private List<Double> swingHighPrices(List<PriceData> candles) {
        List<Double> result = new ArrayList<>();
        for (int i = 2; i < candles.size(); i++) {
            double h0 = candles.get(i - 2).getHigh();
            double h1 = candles.get(i - 1).getHigh();
            double h2 = candles.get(i).getHigh();
            if (h0 < h1 && h2 < h1) {
                result.add(h1);
            }
        }
        return result;
    }

    /**
     * A swing low at candle i-1 when: low[i-2] > low[i-1] AND low[i] > low[i-1]
     */
    private List<Double> swingLowPrices(List<PriceData> candles) {
        List<Double> result = new ArrayList<>();
        for (int i = 2; i < candles.size(); i++) {
            double l0 = candles.get(i - 2).getLow();
            double l1 = candles.get(i - 1).getLow();
            double l2 = candles.get(i).getLow();
            if (l0 > l1 && l2 > l1) {
                result.add(l1);
            }
        }
        return result;
    }

    // -----------------------------------------------------------------------
    // Market data helpers
    // -----------------------------------------------------------------------

    private double fetchNiftyLtp() {
        JSONObject response =
                Login.smartConnect.getLTP(NSE_EXCHANGE, NIFTY_INDEX_SYMBOL, NIFTY_INDEX_TOKEN);
        return response.getJSONObject("data").getDouble("ltp");
    }

    private List<PriceData> fetchTodayCandles() throws Exception {
        String today    = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String fromdate = today + " 09:15";
        String todate   = LocalDateTime.now().format(DATE_FMT);

        JSONObject req = new JSONObject();
        req.put("exchange",    NFO_EXCHANGE);
        req.put("symboltoken", NIFTY_FUTURES_TOKEN);
        req.put("interval",    CANDLE_INTERVAL);
        req.put("fromdate",    fromdate);
        req.put("todate",      todate);

        JSONArray raw = Login.smartConnect.candleData(req);
        List<PriceData> candles = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        for (int i = 0; i < raw.length(); i++) {
            JsonNode node = mapper.readTree(raw.get(i).toString());
            candles.add(new PriceData(
                    node.get(0).asText(),
                    node.get(1).asDouble(),
                    node.get(2).asDouble(),
                    node.get(3).asDouble(),
                    node.get(4).asDouble(),
                    node.get(5).asInt()
            ));
        }
        return candles;
    }

    private double fetchOptionLtp(String token, String symbol) {
        try {
            JSONObject response = Login.smartConnect.getLTP(NFO_EXCHANGE, symbol, token);
            return response.getJSONObject("data").getDouble("ltp");
        } catch (Exception e) {
            log.warn("Could not fetch LTP for {}: {}", symbol, e.getMessage());
            return 0.0;
        }
    }

    // -----------------------------------------------------------------------
    // Order helpers
    // -----------------------------------------------------------------------

    private String placeMarketBuyOrder(String symbol, String token) {
        OrderParams p = new OrderParams();
        p.variety         = Constants.VARIETY_NORMAL;
        p.exchange        = NFO_EXCHANGE;
        p.tradingsymbol   = symbol;
        p.symboltoken     = token;
        p.transactiontype = "BUY";
        p.ordertype       = Constants.ORDER_TYPE_MARKET;
        p.producttype     = Constants.PRODUCT_INTRADAY;
        p.duration        = Constants.DURATION_DAY;
        p.quantity        = NIFTY_LOT_SIZE;
        p.price           = 0.0;

        com.angelbroking.smartapi.models.Order order =
                Login.smartConnect.placeOrder(p, Constants.VARIETY_NORMAL);
        return order.orderId;
    }

    private String placeSLOrder(String symbol, String token, double slPrice) {
        // Trigger fires when price falls to (slPrice + 0.50); limit fills at slPrice.
        double triggerPrice = roundToTick(slPrice + 0.50);

        OrderParams p = new OrderParams();
        p.variety         = Constants.VARIETY_STOPLOSS;
        p.exchange        = NFO_EXCHANGE;
        p.tradingsymbol   = symbol;
        p.symboltoken     = token;
        p.transactiontype = "SELL";
        p.ordertype       = Constants.ORDER_TYPE_STOPLOSS_LIMIT;
        p.producttype     = Constants.PRODUCT_INTRADAY;
        p.duration        = Constants.DURATION_DAY;
        p.quantity        = NIFTY_LOT_SIZE;
        p.price           = slPrice;
        p.triggerprice    = String.valueOf(triggerPrice);

        com.angelbroking.smartapi.models.Order order =
                Login.smartConnect.placeOrder(p, Constants.VARIETY_STOPLOSS);
        return order.orderId;
    }

    private String placeTargetOrder(String symbol, String token, double targetPrice) {
        OrderParams p = new OrderParams();
        p.variety         = Constants.VARIETY_NORMAL;
        p.exchange        = NFO_EXCHANGE;
        p.tradingsymbol   = symbol;
        p.symboltoken     = token;
        p.transactiontype = "SELL";
        p.ordertype       = Constants.ORDER_TYPE_LIMIT;
        p.producttype     = Constants.PRODUCT_INTRADAY;
        p.duration        = Constants.DURATION_DAY;
        p.quantity        = NIFTY_LOT_SIZE;
        p.price           = targetPrice;

        com.angelbroking.smartapi.models.Order order =
                Login.smartConnect.placeOrder(p, Constants.VARIETY_NORMAL);
        return order.orderId;
    }

    // -----------------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------------

    private double roundToNearest50(double price) {
        return Math.round(price / 50.0) * 50.0;
    }

    /** Round to the nearest 0.05 tick (NFO options tick size). */
    private double roundToTick(double price) {
        return Math.round(price / 0.05) * 0.05;
    }

    /**
     * Sort expiries ascending and return the first one (nearest).
     * Angel Broking expiry format in the DB is typically "29MAY2025" — lexicographic
     * sort works only if the month abbreviation is consistent, so we sort by parsed date
     * when possible and fall back to lexicographic order.
     */
    private String selectNearestExpiry(List<String> expiries) {
        return expiries.stream()
                .min((a, b) -> {
                    try {
                        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("ddMMMyyyy");
                        LocalDate da = LocalDate.parse(a, fmt);
                        LocalDate db = LocalDate.parse(b, fmt);
                        return da.compareTo(db);
                    } catch (Exception e) {
                        return a.compareTo(b);
                    }
                })
                .orElse(expiries.get(0));
    }

    // -----------------------------------------------------------------------
    // Inner types
    // -----------------------------------------------------------------------

    public enum Signal {
        BULLISH, BEARISH, NONE
    }
}
