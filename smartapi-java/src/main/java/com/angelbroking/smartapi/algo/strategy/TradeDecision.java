package com.angelbroking.smartapi.algo.strategy;

/**
 * Structured decision returned by the Claude decision engine.
 * action      : "BUY_CE" | "BUY_PE" | "HOLD"
 * confidence  : 0.0 – 1.0  (< 0.82 always becomes HOLD via RiskManager)
 * slPercent   : SL as a % of entry premium   (e.g. 30 → SL at 70% of entry)
 * targetPercent: target as a % of entry premium (e.g. 60 → target at 160% of entry)
 * riskAssessment: "LOW" | "MODERATE" | "HIGH"  (HIGH is always blocked by RiskManager)
 * marketRegime: "TRENDING" | "RANGING" | "VOLATILE" | "UNCLEAR"
 */
public record TradeDecision(
        String action,
        double confidence,
        String reason,
        double slPercent,
        double targetPercent,
        String riskAssessment,
        String marketRegime
) {
    /** Safe sentinel used whenever the API is unavailable or confidence is too low. */
    public static TradeDecision safeHold(String reason) {
        return new TradeDecision("HOLD", 0.0, reason, 30.0, 60.0, "HIGH", "UNCLEAR");
    }
}
