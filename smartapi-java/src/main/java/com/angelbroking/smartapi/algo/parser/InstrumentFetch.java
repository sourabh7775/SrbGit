package com.angelbroking.smartapi.algo.parser;

import com.angelbroking.smartapi.algo.records.Instrument;
import com.angelbroking.smartapi.algo.rds.InstrumentsRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class InstrumentFetch {

    private static final String SCRIP_MASTER_URL =
            "https://margincalculator.angelbroking.com/OpenAPI_File/files/OpenAPIScripMaster.json";

    @Autowired
    private InstrumentsRepository instrumentsRepository;

    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    public void fetchInstruments() throws JsonProcessingException {
        log.info("Fetching instruments from Angel Broking scrip master...");
        try {
            Request request = new Request.Builder().url(SCRIP_MASTER_URL).build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.error("Failed to fetch scrip master: {}", response.code());
                    return;
                }
                String json = response.body().string();
                List<Map<String, Object>> rawList = objectMapper.readValue(json, List.class);

                List<Instrument> instruments = new ArrayList<>();
                for (Map<String, Object> item : rawList) {
                    String exchSeg = (String) item.getOrDefault("exch_seg", "");
                    if (!"NFO".equals(exchSeg)) continue;

                    Instrument inst = new Instrument();
                    inst.setToken(String.valueOf(item.getOrDefault("token", "")));
                    inst.setSymbol(String.valueOf(item.getOrDefault("symbol", "")));
                    inst.setName(String.valueOf(item.getOrDefault("name", "")));
                    inst.setExpiry(String.valueOf(item.getOrDefault("expiry", "")));
                    inst.setInstrumenttype(String.valueOf(item.getOrDefault("instrumenttype", "")));
                    inst.setExchSeg(exchSeg);

                    Object strike = item.get("strike");
                    inst.setStrike(strike != null ? parseDouble(strike) / 100.0 : 0.0);

                    Object lotsize = item.get("lotsize");
                    inst.setLotsize(lotsize != null ? parseInt(lotsize) : 0);

                    Object tickSize = item.get("tick_size");
                    inst.setTickSize(tickSize != null ? parseDouble(tickSize) / 100.0 : 0.05);

                    instruments.add(inst);
                }

                instrumentsRepository.saveAll(instruments);
                log.info("Saved {} NFO instruments", instruments.size());
            }
        } catch (IOException e) {
            log.error("Error fetching instruments", e);
        }
    }

    private double parseDouble(Object val) {
        try {
            return Double.parseDouble(String.valueOf(val));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private int parseInt(Object val) {
        try {
            return Integer.parseInt(String.valueOf(val));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
