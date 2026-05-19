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
 * AI-powered NIFTY options strategy.
 *
 * Flow on every call to execute():
 *   1. Collect live market data → MarketContext
 *   2. Send context to Claude → TradeDecision
 *   3. RiskManager hard-gates the decision
 *   4. Place entry + SL + target orders if approved
 *
 * The primary goal is capital preservation: Claude and the RiskManager both
 * independently reject trades that do not meet strict quality criteria.
 */
@Service
@Slf4j
public class AiTradingStrategy {

    // NIFTY 50 index token on NSE — used for spot LTP
    private static final String NIFTY_INDEX_TOKEN  = "99926000";
    private static final String NIFTY_INDEX_SYMBOL = "Nifty 50";

    // Active NIFTY near-month/week futures token — UPDATE before each expiry
    private static final String NIFTY_FUTURES_TOKEN = "35001";

    private static final String NSE_EXCHANGE  = "NSE";
    private static final String NFO_EXCHANGE  = "NFO";
    private static final int    NIFTY_LOT_SIZE = 50;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Autowired private InstrumentService   instrumentService;
    @Autowired private ClaudeDecisionEngine claudeEngine;
    @Autowired private RiskManager          riskManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // -----------------------------------------------------------------------
    // Public entry points
    // -----------------------------------------------------------------------

    /**
     * Main strategy loop: gather data → Claude decides → risk check → execute.
     * Returns a human-readable summary of the outcome.
     */
    public String execute() {
        if (Login.smartConnect == null) {
            return "Not logged in — call POST /v1/login first";
        }

        try {
            MarketContext ctx = buildMarketContext();

            TradeDecision decision = claudeEngine.decide(ctx);

            if ("HOLD".equals(decision.action())) {
                return "HOLD | " + decision.reason() + "\n" + riskManager.status();
            }

            if (!riskManager.canTrade(decision)) {
                return "Trade BLOCKED by risk manager.\n" + riskManager.status();
            }

            return executeTrade(ctx, decision);

        } catch (Exception e) {
            log.error("AiTradingStrategy execution failed", e);
            return "Error: " + e.getMessage();
        }
    }

    public String status() {
        return riskManager.status();
    }

    // -----------------------------------------------------------------------
    // Market context builder
    // -----------------------------------------------------------------------

    private MarketContext buildMarketContext() throws Exception {
        double niftySpot = fetchNiftyLtp();
        double atmStrike = roundToNearest50(niftySpot);

        List<PriceData> candles    = fetchTodayCandles();
        List<Double>    swingHighs = swingHighPrices(candles);
        List<Double>    swingLows  = swingLowPrices(candles);

        // Resolve nearest expiry once so we reuse it for both CE and PE LTP calls
        List<String> expiries    = instrumentService.getAllExpiries();
        String       nearestExpiry = expiries.isEmpty() ? "" : selectNearestExpiry(expiries);

        double atmCeLtp   = fetchOptionLtp(atmStrike, "CE", nearestExpiry);
        double atmPeLtp   = fetchOptionLtp(atmStrike, "PE", nearestExpiry);
        String greeksJson = fetchOptionGreeks(nearestExpiry);
        double pcr        = fetchPutCallRatio();

        log.info("Context — spot={} strike={} CE_ltp={} PE_ltp={} PCR={} candles={}",
                niftySpot, atmStrike, atmCeLtp, atmPeLtp, pcr, candles.size());

        return MarketContext.builder()
                .niftySpot(niftySpot)
                .atmStrike(atmStrike)
                .recentCandles(candles)
                .swingHighs(swingHighs)
                .swingLows(swingLows)
                .atmCeLtp(atmCeLtp)
                .atmPeLtp(atmPeLtp)
                .greeksJson(greeksJson)
                .putCallRatio(pcr)
                .hasOpenPosition(false) // TODO: replace with smartConnect.getPosition() check
                .dailyPnl(riskManager.getDailyPnl())
                .timestamp(LocalDateTime.now().format(DATE_FMT))
                .build();
    }

    // -----------------------------------------------------------------------
    // Trade execution
    // -----------------------------------------------------------------------

    private String executeTrade(MarketContext ctx, TradeDecision decision) throws Exception {
        String optionType = "BUY_CE".equals(decision.action()) ? "CE" : "PE";

        List<String> expiries = instrumentService.getAllExpiries();
        if (expiries.isEmpty()) {
            return "No expiries in DB — call GET /v1/instruments first";
        }
        String nearestExpiry = selectNearestExpiry(expiries);

        List<String>     options   = instrumentService.getOptionsByExpiryAndStrike(
                nearestExpiry, ctx.getAtmStrike());
        Optional<String> symbolOpt = options.stream()
                .filter(s -> s.startsWith("NIFTY") && s.endsWith(optionType))
                .findFirst();

        if (symbolOpt.isEmpty()) {
            return "No NIFTY " + optionType + " found at strike "
                    + ctx.getAtmStrike() + " expiry " + nearestExpiry;
        }

        String optionSymbol  = symbolOpt.get();
        String token         = instrumentService.getToken(new Symbol(optionSymbol));
        double entryPremium  = "CE".equals(optionType) ? ctx.getAtmCeLtp() : ctx.getAtmPeLtp();

        if (entryPremium <= 0) {
            return "Invalid premium (0) for " + optionSymbol + " — cannot size SL/target";
        }

        double slPrice  = roundToTick(entryPremium * (1.0 - decision.slPercent()     / 100.0));
        double tgtPrice = roundToTick(entryPremium * (1.0 + decision.targetPercent() / 100.0));

        String buyOrderId = placeMarketBuy(optionSymbol, token);
        String slOrderId  = placeSLOrder(optionSymbol, token, slPrice);
        String tgtOrderId = placeTargetOrder(optionSymbol, token, tgtPrice);

        log.info("Orders placed — buy={} sl={} target={}", buyOrderId, slOrderId, tgtOrderId);

        return String.format(
                "TRADE ENTERED%n"
                + "Option  : %s%n"
                + "Spot    : %.2f | Strike : %.0f%n"
                + "Premium : %.2f | SL     : %.2f (-%.0f%%) | Target : %.2f (+%.0f%%)%n"
                + "Confidence : %.2f | Regime : %s | Risk : %s%n"
                + "Reason  : %s%n"
                + "Risk    : %s",
                optionSymbol,
                ctx.getNiftySpot(), ctx.getAtmStrike(),
                entryPremium, slPrice, decision.slPercent(),
                tgtPrice, decision.targetPercent(),
                decision.confidence(), decision.marketRegime(), decision.riskAssessment(),
                decision.reason(),
                riskManager.status());
    }

    // -----------------------------------------------------------------------
    // Market data helpers
    // -----------------------------------------------------------------------

    private double fetchNiftyLtp() {
        JSONObject resp =
                Login.smartConnect.getLTP(NSE_EXCHANGE, NIFTY_INDEX_SYMBOL, NIFTY_INDEX_TOKEN);
        return resp.getJSONObject("data").getDouble("ltp");
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

        JSONArray raw     = Login.smartConnect.candleData(req);
        List<PriceData> candles = new ArrayList<>();
        for (int i = 0; i < raw.length(); i++) {
            JsonNode node = objectMapper.readTree(raw.get(i).toString());
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

    private double fetchOptionLtp(double strike, String type, String expiry) {
        try {
            if (expiry.isEmpty()) return 0.0;
            List<String> options = instrumentService.getOptionsByExpiryAndStrike(expiry, strike);
            Optional<String> sym = options.stream()
                    .filter(s -> s.startsWith("NIFTY") && s.endsWith(type))
                    .findFirst();
            if (sym.isEmpty()) return 0.0;
            String token = instrumentService.getToken(new Symbol(sym.get()));
            JSONObject resp = Login.smartConnect.getLTP(NFO_EXCHANGE, sym.get(), token);
            return resp.getJSONObject("data").getDouble("ltp");
        } catch (Exception e) {
            log.warn("Could not fetch NIFTY {} LTP at {}: {}", type, strike, e.getMessage());
            return 0.0;
        }
    }

    private String fetchOptionGreeks(String expiry) {
        try {
            JSONObject req = new JSONObject();
            req.put("name",       "NIFTY");
            req.put("expiryDate", expiry);
            return Login.smartConnect.optionGreek(req).toString();
        } catch (Exception e) {
            log.warn("Could not fetch option Greeks: {}", e.getMessage());
            return "{}";
        }
    }

    private double fetchPutCallRatio() {
        try {
            JSONObject resp =
                    Login.smartConnect.putCallRatio(new JSONObject().put("name", "NIFTY"));
            return resp.getJSONObject("data").getDouble("putCallRatio");
        } catch (Exception e) {
            log.warn("Could not fetch PCR — using neutral 1.0: {}", e.getMessage());
            return 1.0;
        }
    }

    // -----------------------------------------------------------------------
    // Swing detection (same 3-bar pivot logic as Login, returns actual prices)
    // -----------------------------------------------------------------------

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

    // -----------------------------------------------------------------------
    // Order placement
    // -----------------------------------------------------------------------

    private String placeMarketBuy(String symbol, String token) {
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
        return Login.smartConnect.placeOrder(p, Constants.VARIETY_NORMAL).orderId;
    }

    private String placeSLOrder(String symbol, String token, double slPrice) {
        double trigger = roundToTick(slPrice + 0.50);
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
        p.triggerprice    = String.valueOf(trigger);
        return Login.smartConnect.placeOrder(p, Constants.VARIETY_STOPLOSS).orderId;
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
        return Login.smartConnect.placeOrder(p, Constants.VARIETY_NORMAL).orderId;
    }

    // -----------------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------------

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
}
