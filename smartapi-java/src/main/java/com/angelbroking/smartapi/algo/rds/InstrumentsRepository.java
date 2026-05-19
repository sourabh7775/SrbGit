package com.angelbroking.smartapi.algo.rds;

import com.angelbroking.smartapi.algo.records.Instrument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InstrumentsRepository extends JpaRepository<Instrument, String> {

    @Query("SELECT DISTINCT i.expiry FROM Instrument i WHERE i.exchSeg = 'NFO' AND i.expiry IS NOT NULL AND i.expiry != '' ORDER BY i.expiry")
    List<String> findDistinctExpiries();

    @Query("SELECT DISTINCT i.strike FROM Instrument i WHERE i.exchSeg = 'NFO' AND i.expiry = :expiry ORDER BY i.strike")
    List<Double> findStrikesByExpiry(@Param("expiry") String expiry);

    @Query("SELECT i FROM Instrument i WHERE i.exchSeg = 'NFO' AND i.expiry = :expiry AND i.strike = :strike AND i.instrumenttype IN ('CE', 'PE')")
    List<Instrument> findOptionsByExpiryAndStrike(@Param("expiry") String expiry, @Param("strike") double strike);

    @Query("SELECT i FROM Instrument i WHERE i.symbol = :symbol")
    Optional<Instrument> findTokenBySymbol(@Param("symbol") String symbol);

    @Query("SELECT i FROM Instrument i WHERE i.exchSeg = 'NFO' AND i.expiry = :expiry AND i.strike = :strike AND i.instrumenttype = :type")
    Optional<Instrument> findByExpiryStrikeAndType(@Param("expiry") String expiry, @Param("strike") double strike, @Param("type") String type);
}
