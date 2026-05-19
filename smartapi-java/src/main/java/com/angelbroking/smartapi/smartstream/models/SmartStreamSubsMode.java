package com.angelbroking.smartapi.smartstream.models;

public enum SmartStreamSubsMode {
    LTP(1),
    QUOTE(2),
    SNAP_QUOTE(3),
    DEPTH20(4);

    private final int val;

    SmartStreamSubsMode(int val) {
        this.val = val;
    }

    public int getVal() {
        return val;
    }

    public static SmartStreamSubsMode fromVal(int val) {
        for (SmartStreamSubsMode mode : values()) {
            if (mode.val == val) return mode;
        }
        return LTP;
    }
}
