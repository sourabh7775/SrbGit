package com.angelbroking.smartapi.algo.web;

import com.angelbroking.smartapi.algo.records.Instrument;
import com.angelbroking.smartapi.algo.service.InstrumentService;
import com.angelbroking.smartapi.algo.websocket.SmartStremWebSocket;
import com.angelbroking.smartapi.algo.Login;
import com.angelbroking.smartapi.smartstream.models.ExchangeType;
import com.angelbroking.smartapi.smartstream.models.TokenID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/algo/v1")
@CrossOrigin
public class InstrumentController {

    @Autowired
    private InstrumentService instrumentService;

    @Autowired
    private SmartStremWebSocket smartStremWebSocket;

    @GetMapping("/expiries")
    public List<String> getExpiries() {
        return instrumentService.getExpiries();
    }

    @GetMapping("/strikes")
    public List<Double> getStrikes(@RequestParam String expiry) {
        return instrumentService.getStrikesByExpiry(expiry);
    }

    @GetMapping("/options")
    public List<Instrument> getOptions(@RequestParam String expiry, @RequestParam double strike) {
        return instrumentService.getOptionsByExpiryAndStrike(expiry, strike);
    }

    @PostMapping("/subscribe")
    public String subscribe(@RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            List<String> tokens = (List<String>) body.get("tokens");
            if (tokens == null || tokens.isEmpty()) return "No tokens provided";

            Set<TokenID> tokenSet = tokens.stream()
                    .map(t -> new TokenID(ExchangeType.NSE_FO, t))
                    .collect(Collectors.toSet());

            if (Login.user != null) {
                smartStremWebSocket.subscribe(tokenSet);
                return "Subscribed to " + tokens.size() + " tokens";
            }
            return "Not logged in";
        } catch (Exception e) {
            log.error("Subscribe error", e);
            return "Error: " + e.getMessage();
        }
    }

    @PostMapping("/unsubscribe")
    public String unsubscribe(@RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            List<String> tokens = (List<String>) body.get("tokens");
            if (tokens == null || tokens.isEmpty()) return "No tokens provided";

            Set<TokenID> tokenSet = tokens.stream()
                    .map(t -> new TokenID(ExchangeType.NSE_FO, t))
                    .collect(Collectors.toSet());

            smartStremWebSocket.unsubscribe(tokenSet);
            return "Unsubscribed from " + tokens.size() + " tokens";
        } catch (Exception e) {
            log.error("Unsubscribe error", e);
            return "Error: " + e.getMessage();
        }
    }
}
