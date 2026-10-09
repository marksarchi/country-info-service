package com.example.countryinfoservice.util;

import java.util.Locale;

public final class TextUtils {
    private TextUtils() {}

    public static String toSentenceCase(String input) {
        if (input == null) return null;
        String t = input.trim().replaceAll("\\s+", " ");
        if (t.isEmpty()) return t;
        return t.substring(0, 1).toUpperCase(Locale.ROOT) + t.substring(1).toLowerCase(Locale.ROOT);
    }
}
