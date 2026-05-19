package com.angelbroking.smartapi.algo.rds;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
public class OrderRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public void insertOrder(
            String orderId, String uniqueOrderId, String exchangeOrderId, String status,
            String tradingSymbol, String symbolToken, String exchange,
            String transactionType, String productType, String orderType,
            String variety, String duration, double price, double triggerPrice,
            int quantity, int filledShares, int unfilledShares, double averagePrice,
            String text, String orderTag, String orderUpdateTime, String exchTime,
            String exchOrderUpdateTime, String updateTime, double squareOff,
            double stopLoss, double trailingStopLoss, String instrumentType,
            String optionType, double strikePrice, String expiryDate,
            int lotSize, int cancelSize, String parentOrderId, String brokerOrderId,
            String clientCode, double pnl, String strategy, String remarks
    ) {
        String sql = """
            INSERT INTO orders (
                order_id, unique_order_id, exchange_order_id, status,
                trading_symbol, symbol_token, exchange, transaction_type,
                product_type, order_type, variety, duration, price, trigger_price,
                quantity, filled_shares, unfilled_shares, average_price,
                text, order_tag, order_update_time, exch_time, exch_order_update_time,
                update_time, square_off, stop_loss, trailing_stop_loss,
                instrument_type, option_type, strike_price, expiry_date,
                lot_size, cancel_size, parent_order_id, broker_order_id,
                client_code, pnl, strategy, remarks
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON DUPLICATE KEY UPDATE status = VALUES(status), update_time = VALUES(update_time)
            """;
        try {
            jdbcTemplate.update(sql,
                orderId, uniqueOrderId, exchangeOrderId, status,
                tradingSymbol, symbolToken, exchange, transactionType,
                productType, orderType, variety, duration, price, triggerPrice,
                quantity, filledShares, unfilledShares, averagePrice,
                text, orderTag, orderUpdateTime, exchTime, exchOrderUpdateTime,
                updateTime, squareOff, stopLoss, trailingStopLoss,
                instrumentType, optionType, strikePrice, expiryDate,
                lotSize, cancelSize, parentOrderId, brokerOrderId,
                clientCode, pnl, strategy, remarks
            );
        } catch (Exception e) {
            log.error("Error inserting order {}", orderId, e);
        }
    }
}
