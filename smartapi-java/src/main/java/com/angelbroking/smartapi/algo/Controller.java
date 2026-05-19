package com.angelbroking.smartapi.algo;

import com.angelbroking.smartapi.algo.parser.InstrumentFetch;
import com.angelbroking.smartapi.algo.rds.InstrumentsRepository;
import com.angelbroking.smartapi.algo.records.Instrument;
import com.angelbroking.smartapi.algo.strategy.AiTradingStrategy;
import com.angelbroking.smartapi.algo.strategy.NiftyMomentumStrategy;
import com.angelbroking.smartapi.http.exceptions.SmartAPIException;
import com.angelbroking.smartapi.records.ClientInfo;
import com.angelbroking.smartapi.records.OptionGreeks;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.neovisionaries.ws.client.WebSocketException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@CrossOrigin
public class Controller {

    @Autowired
    private Login login;

    @Autowired
    private InstrumentFetch instrumentFetch;

    @Autowired
    private InstrumentsRepository instrumentsRepository;

    @Autowired
    private NiftyMomentumStrategy niftyMomentumStrategy;

    @Autowired
    private AiTradingStrategy aiTradingStrategy;

    @PostMapping("v1/login")
    public String login(@RequestBody ClientInfo clientInfo) {
        return login.login(clientInfo);
    }

    @PostMapping("v1/ltp")
    public String ltp() {
        return login.getLtp();
    }

    @PostMapping("v1/optionGreeks")
    public String optionGreeks(@RequestBody OptionGreeks optionGreeks) throws SmartAPIException, IOException {
        return login.getOptionGreeks(optionGreeks);
    }

    @PostMapping("v1/order")
    public void webSocketOrder() throws SmartAPIException, IOException, WebSocketException {
        login.webSocketOrder();
    }

    @GetMapping("v1/instruments")
    public void fetchInstruments() throws JsonProcessingException {
        instrumentFetch.fetchInstruments();
    }

    @GetMapping("v1/candle")
    public void getCandle() throws IOException {
        login.getCandleData();
    }

    @GetMapping("v1/ui/instruments")
    public List<Instrument> getInstruments() throws IOException {
        try {
            return instrumentsRepository.findAll();
        } catch (Exception e) {
            log.error("{}", e);
            return new ArrayList<>();
        }
    }

    /**
     * Runs the NIFTY momentum CE/PE buy strategy.
     *
     * Prerequisites:
     *   1. POST /v1/login       — initialise the SmartAPI session
     *   2. GET  /v1/instruments — load NFO instrument data into the DB
     *
     * Returns a summary string: option symbol, entry premium, SL, and target,
     * or a descriptive message when no signal is detected.
     */
    @PostMapping("v1/strategy/nifty-momentum")
    public String runNiftyMomentum() {
        return niftyMomentumStrategy.execute();
    }

    /**
     * AI-powered strategy: Claude analyzes live market data and decides
     * whether to buy CE, buy PE, or hold — with hard risk guardrails.
     *
     * Prerequisites (in order):
     *   1. POST /v1/login
     *   2. GET  /v1/instruments
     *   Set ANTHROPIC_API_KEY environment variable before starting the app.
     */
    @PostMapping("v1/strategy/ai-trade")
    public String runAiTrade() {
        return aiTradingStrategy.execute();
    }

    /** Current risk manager state: daily P&L, trade count, halted flag. */
    @GetMapping("v1/strategy/status")
    public String strategyStatus() {
        return aiTradingStrategy.status();
    }
}
