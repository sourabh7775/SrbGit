package com.angelbroking.smartapi.algo.event.listener;

import com.angelbroking.smartapi.algo.event.OrderEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderEventListener implements ApplicationListener<OrderEvent> {

    @Override
    public void onApplicationEvent(OrderEvent event) {
        log.info("Order event: {}", event.getOrderData());
    }
}
