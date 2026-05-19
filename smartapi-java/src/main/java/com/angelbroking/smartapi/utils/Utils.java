package com.angelbroking.smartapi.utils;

import org.json.JSONObject;

public class Utils {

    public static boolean isNull(Object obj) {
        return obj == null;
    }

    public static boolean isNotNull(Object obj) {
        return obj != null;
    }

    public static String optString(JSONObject json, String key) {
        return json != null ? json.optString(key, "") : "";
    }

    public static double optDouble(JSONObject json, String key) {
        return json != null ? json.optDouble(key, 0.0) : 0.0;
    }

    public static long optLong(JSONObject json, String key) {
        return json != null ? json.optLong(key, 0L) : 0L;
    }

    public static int optInt(JSONObject json, String key) {
        return json != null ? json.optInt(key, 0) : 0;
    }
}
