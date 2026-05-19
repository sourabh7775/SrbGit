package com.angelbroking.smartapi.algo.service;

import com.angelbroking.smartapi.algo.records.Instrument;
import com.angelbroking.smartapi.algo.rds.InstrumentsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class InstrumentService {

    @Autowired
    private InstrumentsRepository instrumentsRepository;

    public List<String> getExpiries() {
        return instrumentsRepository.findDistinctExpiries();
    }

    /** Alias for getExpiries() — kept for compatibility. */
    public List<String> getAllExpiries() {
        return getExpiries();
    }

    public List<Double> getStrikesByExpiry(String expiry) {
        return instrumentsRepository.findStrikesByExpiry(expiry);
    }

    /** Returns all NFO instruments for the given expiry and strike (CE + PE). */
    public List<Instrument> getOptionsByExpiryAndStrike(String expiry, double strike) {
        return instrumentsRepository.findOptionsByExpiryAndStrike(expiry, strike);
    }

    /** Returns a list of symbol strings (e.g. "NIFTY29MAY2025CE22000") for the expiry+strike. */
    public List<String> getOptionSymbols(String expiry, double strike) {
        return getOptionsByExpiryAndStrike(expiry, strike).stream()
                .map(Instrument::getSymbol)
                .toList();
    }

    public Optional<Instrument> findInstrumentBySymbol(String symbol) {
        return instrumentsRepository.findTokenBySymbol(symbol);
    }

    public Optional<Instrument> findByExpiryStrikeAndType(String expiry, double strike, String type) {
        return instrumentsRepository.findByExpiryStrikeAndType(expiry, strike, type);
    }

    /** Returns the token for a given symbol, or empty string if not found. */
    public String getTokenForSymbol(String symbol) {
        return findInstrumentBySymbol(symbol)
                .map(Instrument::getToken)
                .orElse("");
    }
}
