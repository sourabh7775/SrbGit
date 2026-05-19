package com.angelbroking.smartapi.algo.strategy;

import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Calls the Anthropic Claude API with a full market snapshot and returns a
 * structured TradeDecision.  Tool use forces Claude to always respond with
 * a machine-readable JSON block — no free-text parsing required.
 *
 * Set ANTHROPIC_API_KEY in the environment (or anthropic.api.key in
 * application.properties) before starting the application.
 */
@Service
@Slf4j
public class ClaudeDecisionEngine {

    private static final String ANTHROPIC_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL         = "claude-opus-4-7-20251001";
    private static final MediaType JSON_MEDIA = MediaType.parse("application/json; charset=utf-8");

    @Value("${anthropic.api.key}")
    private String apiKey;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .build();

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    public TradeDecision decide(MarketContext ctx) {
        try {
            String responseJson = callApi(buildRequest(ctx));
            TradeDecision decision = parseResponse(responseJson);
            log.info("Claude decision → action={} confidence={:.2f} regime={} risk={}",
                    decision.action(), decision.confidence(),
                    decision.marketRegime(), decision.riskAssessment());
            log.info("Reason: {}", decision.reason());
            return decision;
        } catch (Exception e) {
            log.error("Claude API call failed — defaulting to safe HOLD", e);
            return TradeDecision.safeHold("Claude API unavailable: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Request building
    // -----------------------------------------------------------------------

    private JSONObject buildRequest(MarketContext ctx) {
        JSONObject req = new JSONObject();
        req.put("model",      MODEL);
        req.put("max_tokens", 2048);
        req.put("system",     systemPrompt());

        JSONArray messages = new JSONArray();
        JSONObject userMsg = new JSONObject();
        userMsg.put("role",    "user");
        userMsg.put("content", buildUserMessage(ctx));
        messages.put(userMsg);
        req.put("messages", messages);

        req.put("tools",       new JSONArray().put(tradeDecisionTool()));
        // Force Claude to always call our tool so output is always structured JSON
        req.put("tool_choice", new JSONObject()
                .put("type", "tool")
                .put("name", "make_trade_decision"));

        return req;
    }

    private String buildUserMessage(MarketContext ctx) {
        JSONObject data = new JSONObject();
        data.put("timestamp",         ctx.getTimestamp());
        data.put("nifty_spot",        ctx.getNiftySpot());
        data.put("atm_strike",        ctx.getAtmStrike());
        data.put("put_call_ratio",    ctx.getPutCallRatio());
        data.put("daily_pnl",         ctx.getDailyPnl());
        data.put("has_open_position", ctx.isHasOpenPosition());

        // Swing analysis with breakout gap so Claude can judge strength
        JSONObject swing = new JSONObject();
        swing.put("swing_highs", ctx.getSwingHighs());
        swing.put("swing_lows",  ctx.getSwingLows());
        if (!ctx.getSwingHighs().isEmpty() && !ctx.getRecentCandles().isEmpty()) {
            double close    = ctx.getRecentCandles().get(ctx.getRecentCandles().size() - 1).getClose();
            double lastHigh = ctx.getSwingHighs().get(ctx.getSwingHighs().size() - 1);
            double lastLow  = ctx.getSwingLows().isEmpty() ? 0
                    : ctx.getSwingLows().get(ctx.getSwingLows().size() - 1);
            swing.put("latest_close",             close);
            swing.put("last_swing_high",          lastHigh);
            swing.put("last_swing_low",           lastLow);
            swing.put("pct_above_last_swing_high",
                    lastHigh > 0 ? String.format("%.2f%%", (close - lastHigh) / lastHigh * 100) : "N/A");
            swing.put("pct_below_last_swing_low",
                    lastLow > 0 ? String.format("%.2f%%", (lastLow - close) / lastLow * 100) : "N/A");
        }
        data.put("swing_analysis", swing);

        // Last 10 candles only — enough context without bloating the prompt
        JSONArray candles = new JSONArray();
        int start = Math.max(0, ctx.getRecentCandles().size() - 10);
        for (int i = start; i < ctx.getRecentCandles().size(); i++) {
            var c = ctx.getRecentCandles().get(i);
            candles.put(new JSONObject()
                    .put("time",   c.getDate())
                    .put("open",   c.getOpen())
                    .put("high",   c.getHigh())
                    .put("low",    c.getLow())
                    .put("close",  c.getClose())
                    .put("volume", c.getVolume()));
        }
        data.put("recent_5min_candles", candles);

        // Option chain
        JSONObject options = new JSONObject();
        options.put("atm_ce", new JSONObject().put("ltp", ctx.getAtmCeLtp()));
        options.put("atm_pe", new JSONObject().put("ltp", ctx.getAtmPeLtp()));
        if (ctx.getGreeksJson() != null && !ctx.getGreeksJson().equals("{}")) {
            options.put("greeks", ctx.getGreeksJson());
        }
        data.put("option_chain", options);

        return "Analyze this live market data and make a NIFTY options trading decision.\n\n"
                + data.toString(2);
    }

    // -----------------------------------------------------------------------
    // API call
    // -----------------------------------------------------------------------

    private String callApi(JSONObject requestBody) throws Exception {
        RequestBody body = RequestBody.create(requestBody.toString(), JSON_MEDIA);
        Request request = new Request.Builder()
                .url(ANTHROPIC_URL)
                .post(body)
                .addHeader("x-api-key",          apiKey)
                .addHeader("anthropic-version",   "2023-06-01")
                .addHeader("content-type",        "application/json")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new RuntimeException("HTTP " + response.code() + ": " + responseBody);
            }
            return responseBody;
        }
    }

    // -----------------------------------------------------------------------
    // Response parsing
    // -----------------------------------------------------------------------

    private TradeDecision parseResponse(String responseJson) {
        JSONObject response = new JSONObject(responseJson);
        JSONArray content   = response.getJSONArray("content");

        for (int i = 0; i < content.length(); i++) {
            JSONObject block = content.getJSONObject(i);
            if ("tool_use".equals(block.getString("type"))) {
                JSONObject input = block.getJSONObject("input");
                return new TradeDecision(
                        input.getString("action"),
                        input.getDouble("confidence"),
                        input.getString("reason"),
                        input.optDouble("sl_percent",     30.0),
                        input.optDouble("target_percent", 60.0),
                        input.optString("risk_assessment", "MODERATE"),
                        input.optString("market_regime",   "UNCLEAR")
                );
            }
        }

        log.warn("No tool_use block in Claude response — safe HOLD");
        return TradeDecision.safeHold("Claude returned no structured decision");
    }

    // -----------------------------------------------------------------------
    // System prompt — capital preservation is the #1 priority
    // -----------------------------------------------------------------------

    private String systemPrompt() {
        return """
You are an expert NIFTY options trader and risk analyst. Your PRIMARY mandate is \
CAPITAL PRESERVATION — protecting the trader's money is more important than \
capturing any single trade.

## Decision options
BUY_CE  — buy ATM call option (bullish momentum breakout, very high confidence only)
BUY_PE  — buy ATM put option  (bearish momentum breakdown, very high confidence only)
HOLD    — do nothing (always safe, always acceptable)

## ALL of the following must be true to trade (even one failure → HOLD)
1. CLEAN breakout: latest close is decisively above the last tested swing high (CE) \
   OR decisively below the last tested swing low (PE). "Touching" does not count.
2. Breakout candle has above-average volume (look for at least similar volume to recent candles).
3. PCR alignment: for CE → PCR between 0.8 and 1.4 (neutral to slightly put-heavy); \
   for PE → PCR between 0.6 and 1.2. Extreme PCR warns of a reversal — avoid.
4. No open position already (hasOpenPosition = false).
5. Daily P&L is zero or positive — never trade when already at a loss.
6. Your confidence is ≥ 0.82.
7. The market regime is TRENDING, not RANGING, VOLATILE, or UNCLEAR.

## Always HOLD when
- Price is inside the swing range (no clear breakout or breakdown)
- Daily P&L is negative
- Swing highs and swing lows are clustered tightly (choppy / ranging market)
- Only 1 swing point identified on each side (not enough data)
- PCR is extreme (< 0.5 or > 1.6) — potential sharp reversal risk
- Confidence < 0.82
- riskAssessment would be HIGH

## Risk parameters guidance
sl_percent    : 25 – 35% of entry premium. Use tighter SL (25%) in uncertain markets. \
                Never go below 20% (too tight, gets stopped out by noise).
target_percent: 50 – 70% of entry premium. Favour realistic targets over greedy ones. \
                Never exceed 80% (unlikely to hit; better to book and re-enter).

## Most important rule
When in doubt → HOLD. Missing a trade costs zero. A bad trade can cost thousands. \
You are the last line of defence before real money is committed. Be ruthless about quality.
""";
    }

    // -----------------------------------------------------------------------
    // Tool schema — forces Claude to output structured JSON
    // -----------------------------------------------------------------------

    private JSONObject tradeDecisionTool() {
        JSONObject props = new JSONObject();

        props.put("action", new JSONObject()
                .put("type", "string")
                .put("enum", new JSONArray().put("BUY_CE").put("BUY_PE").put("HOLD"))
                .put("description", "The trading action. HOLD when any doubt exists."));

        props.put("confidence", new JSONObject()
                .put("type", "number")
                .put("description", "0.0–1.0. Must be ≥ 0.82 to trade. Reflect genuine conviction."));

        props.put("reason", new JSONObject()
                .put("type", "string")
                .put("description", "Cite specific data points: exact price levels, swing values, PCR, candle details."));

        props.put("sl_percent", new JSONObject()
                .put("type", "number")
                .put("description", "SL as % of entry premium. Range 20–35. Recommend 25–30."));

        props.put("target_percent", new JSONObject()
                .put("type", "number")
                .put("description", "Target as % of entry premium. Range 40–80. Recommend 50–70."));

        props.put("risk_assessment", new JSONObject()
                .put("type", "string")
                .put("enum", new JSONArray().put("LOW").put("MODERATE").put("HIGH"))
                .put("description", "HIGH always results in the trade being blocked by the risk manager."));

        props.put("market_regime", new JSONObject()
                .put("type", "string")
                .put("enum", new JSONArray().put("TRENDING").put("RANGING").put("VOLATILE").put("UNCLEAR"))
                .put("description", "Non-TRENDING regimes should almost always be HOLD."));

        JSONObject schema = new JSONObject()
                .put("type", "object")
                .put("properties", props)
                .put("required", new JSONArray()
                        .put("action").put("confidence").put("reason")
                        .put("sl_percent").put("target_percent")
                        .put("risk_assessment").put("market_regime"));

        return new JSONObject()
                .put("name",         "make_trade_decision")
                .put("description",  "Make a structured NIFTY options trading decision. " +
                        "This controls real money — be extremely conservative.")
                .put("input_schema", schema);
    }
}
