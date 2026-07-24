package com.campusguinness.score.application.query;

import java.math.BigDecimal;

/**
 * Unified score display formatter for all student-facing queries.
 * Single source of truth — no duplicated CASE expressions in SQL.
 */
public final class ScoreDisplayFormatter {

    private ScoreDisplayFormatter() {}

    public static String format(String storageType, Object scoreValue,
            Long durationMs, String grade, Integer decimalPlaces) {
        if (storageType == null) return null;
        return switch (storageType) {
            case "INTEGER" -> formatInteger(scoreValue);
            case "DECIMAL" -> formatDecimal(scoreValue, decimalPlaces);
            case "DURATION" -> formatDuration(durationMs);
            case "GRADE" -> grade;
            default -> null;
        };
    }

    private static String formatInteger(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal bd) {
            return bd.stripTrailingZeros().toPlainString();
        }
        if (value instanceof Number n) {
            long v = n.longValue();
            if ((double) v == n.doubleValue()) {
                return String.valueOf(v);
            }
            return new BigDecimal(n.toString()).stripTrailingZeros().toPlainString();
        }
        return value.toString();
    }

    private static String formatDecimal(Object value, Integer decimalPlaces) {
        if (value == null) return null;
        BigDecimal bd;
        if (value instanceof BigDecimal b) {
            bd = b;
        } else {
            bd = new BigDecimal(value.toString());
        }
        int scale = decimalPlaces != null ? decimalPlaces : 0;
        return bd.setScale(scale, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    public static String formatDuration(Long durationMs) {
        if (durationMs == null) return null;
        long ms = durationMs;
        if (ms < 0) return ms + "ms";
        if (ms < 1000) return ms + "ms";
        long seconds = ms / 1000;
        if (seconds < 60) return seconds + "秒";
        long minutes = seconds / 60;
        seconds = seconds % 60;
        if (minutes < 60) return minutes + "分" + seconds + "秒";
        long hours = minutes / 60;
        minutes = minutes % 60;
        return hours + "时" + minutes + "分" + seconds + "秒";
    }
}
