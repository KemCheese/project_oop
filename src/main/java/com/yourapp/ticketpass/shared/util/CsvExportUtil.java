package com.yourapp.ticketpass.shared.util;

import java.nio.charset.StandardCharsets;
import java.util.List;

public final class CsvExportUtil {

    private static final String UTF8_BOM = "\uFEFF";
    private static final String CRLF = "\r\n";

    private CsvExportUtil() {
    }

    public static byte[] toUtf8BomCsv(List<String> headers, List<List<String>> rows) {
        return toCsvString(headers, rows).getBytes(StandardCharsets.UTF_8);
    }

    public static String toCsvString(List<String> headers, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(UTF8_BOM);
        appendRow(sb, headers);
        if (rows != null) {
            for (List<String> row : rows) {
                appendRow(sb, row);
            }
        }
        return sb.toString();
    }

    public static String sanitizeFormulaCell(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        char rawFirst = raw.charAt(0);
        if (rawFirst == '\t' || rawFirst == '\r' || rawFirst == '\n') {
            return "'" + raw;
        }
        int idx = 0;
        int len = raw.length();
        while (idx < len && (Character.isWhitespace(raw.charAt(idx)) || Character.isSpaceChar(raw.charAt(idx)))) {
            idx++;
        }
        if (idx < len) {
            char firstNonSpace = raw.charAt(idx);
            if (firstNonSpace == '=' || firstNonSpace == '+' || firstNonSpace == '-' || firstNonSpace == '@') {
                return "'" + raw;
            }
        }
        return raw;
    }

    private static void appendRow(StringBuilder sb, List<String> cells) {
        if (cells == null || cells.isEmpty()) {
            sb.append(CRLF);
            return;
        }
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(formatCell(cells.get(i)));
        }
        sb.append(CRLF);
    }

    private static String formatCell(String raw) {
        String safe = sanitizeFormulaCell(raw);
        boolean needsQuoting = safe.indexOf(',') >= 0
                || safe.indexOf('"') >= 0
                || safe.indexOf('\n') >= 0
                || safe.indexOf('\r') >= 0;
        if (!needsQuoting) {
            return safe;
        }
        return '"' + safe.replace("\"", "\"\"") + '"';
    }
}
