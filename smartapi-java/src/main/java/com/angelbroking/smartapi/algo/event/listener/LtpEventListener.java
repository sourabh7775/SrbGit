package com.angelbroking.smartapi.algo.event.listener;

import com.angelbroking.smartapi.algo.event.LtpEvent;
import com.angelbroking.smartapi.algo.order.OrderProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LtpEventListener implements ApplicationListener<LtpEvent> {

    @Autowired
    private OrderProcessor orderProcessor;

    @Override
    public void onApplicationEvent(LtpEvent event) {
        try {
            orderProcessor.onLtpUpdate(event.getToken(), event.getLtp());
        } catch (Exception e) {
            log.error("Error processing LTP event for token {}", event.getToken(), e);
        }
    }
}
