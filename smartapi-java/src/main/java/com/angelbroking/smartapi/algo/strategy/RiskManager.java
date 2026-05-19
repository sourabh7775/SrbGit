package com.angelbroking.smartapi.algo.strategy;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Hard guardrails that run AFTER Claude makes its decision.
 * No trade passes through unless every check here passes.
 *
 * Configurable via application.properties:
 *   risk.capital                  (default 100000)
 *   risk.max.daily.loss.percent   (default 1.5)
 *   risk.max.trades.per.day       (default 3)
 *   risk.min.confidence           (default 0.82)
 */
@Service
@Slf4j
@Getter
public class RiskManager {

    @Value("${risk.capital:100000}")
    private double capital;

    @Value("${risk.max.daily.loss.percent:1.5}")
    private double maxDailyLossPercent;

    @Value("${risk.max.trades.per.day:3}")
    private int maxTradesPerDay;

    @Value("${risk.min.confidence:0.82}")
    private double minConfidence;

    private volatile double    dailyPnl       = 0.0;
    private volatile int       tradesCount    = 0;
    private volatile boolean   tradingHalted  = false;
    private volatile LocalDate lastResetDate  = LocalDate.now();

    /**
     * Returns true only if ALL risk conditions are satisfied.
     * Any single failure blocks the trade and logs the reason.
     */
    public synchronized boolean canTrade(TradeDecision decision) {
        resetIfNewDay();

        if (tradingHalted) {
            log.warn("BLOCKED: trading halted — daily loss limit reached. P&L: {}", dailyPnl);
            return false;
        }
        if (decision.confidence() < minConfidence) {
            log.info("BLOCKED: confidence {:.3f} < minimum {:.2f}", decision.confidence(), minConfidence);
            return false;
        }
        if ("HIGH".equals(decision.riskAssessment())) {
            log.warn("BLOCKED: Claude assessed risk as HIGH — {}", decision.reason());
            return false;
        }
        if (!"TRENDING".equals(decision.marketRegime())) {
            log.info("BLOCKED: market regime is {} (only TRENDING is tradeable)", decision.marketRegime());
            return false;
        }
        if (tradesCount >= maxTradesPerDay) {
            log.info("BLOCKED: max {} trades per day reached", maxTradesPerDay);
            return false;
        }
        if (dailyPnl < 0) {
            log.warn("BLOCKED: daily P&L is negative ({:.2f}). No new trades today.", dailyPnl);
            return false;
        }

        LocalTime now = LocalTime.now();
        LocalTime openTime  = LocalTime.of(9, 30);
        LocalTime closeTime = LocalTime.of(14, 30);
        if (now.isBefore(openTime) || now.isAfter(closeTime)) {
            log.info("BLOCKED: outside trading window 09:30–14:30. Current time: {}", now);
            return false;
        }

        double maxLoss = capital * maxDailyLossPercent / 100.0;
        if (dailyPnl <= -maxLoss) {
            tradingHalted = true;
            log.error("BLOCKED + HALTED: daily loss limit ₹{:.2f} breached. P&L: {:.2f}", maxLoss, dailyPnl);
            return false;
        }

        return true;
    }

    /**
     * Call this after a trade is closed to update running P&L.
     * pnl is positive for profit, negative for loss.
     */
    public synchronized void recordClosedTrade(double pnl) {
        dailyPnl += pnl;
        tradesCount++;
        double maxLoss = capital * maxDailyLossPercent / 100.0;
        if (dailyPnl <= -maxLoss) {
            tradingHalted = true;
            log.error("Daily loss limit breached after recording trade. Halting. P&L: {:.2f}", dailyPnl);
        }
        log.info("Trade recorded. P&L this trade: {:.2f} | Daily P&L: {:.2f} | Trades today: {}/{}",
                pnl, dailyPnl, tradesCount, maxTradesPerDay);
    }

    public synchronized String status() {
        resetIfNewDay();
        return String.format(
                "Daily P&L: %.2f | Trades: %d/%d | Halted: %s | Window: 09:30–14:30",
                dailyPnl, tradesCount, maxTradesPerDay, tradingHalted);
    }

    private void resetIfNewDay() {
        LocalDate today = LocalDate.now();
        if (!today.equals(lastResetDate)) {
            log.info("New trading day {} — resetting risk counters.", today);
            dailyPnl      = 0.0;
            tradesCount   = 0;
            tradingHalted = false;
            lastResetDate = today;
        }
    }
}
