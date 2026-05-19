package com.angelbroking.smartapi.algo.records;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "instruments")
public class Instrument {
    @Id
    @Column(name = "token")
    private String token;

    @Column(name = "symbol")
    private String symbol;

    @Column(name = "name")
    private String name;

    @Column(name = "expiry")
    private String expiry;

    @Column(name = "strike")
    private double strike;

    @Column(name = "lotsize")
    private int lotsize;

    @Column(name = "instrumenttype")
    private String instrumenttype;

    @Column(name = "exch_seg")
    private String exchSeg;

    @Column(name = "tick_size")
    private double tickSize;
}
