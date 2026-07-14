package com.stocket.system.export;

public final class CsvCellSanitizer {
    private CsvCellSanitizer() { }

    public static String sanitize(Object value) {
        if (value == null) return "";
        String text = value.toString();
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) return "'" + text;
        return text;
    }
}
