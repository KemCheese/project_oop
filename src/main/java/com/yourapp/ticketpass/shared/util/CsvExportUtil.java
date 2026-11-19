package com.yourapp.ticketpass.shared.util;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Tiện ích xuất dữ liệu dạng CSV chuẩn RFC 4180 phục vụ báo cáo tài chính Admin.
 *
 * <p>Đáp ứng hai yêu cầu quan trọng khi mở file CSV trên Microsoft Excel tiếng Việt:</p>
 * <ul>
 *   <li>Chèn UTF-8 BOM ({@code 0xEF 0xBB 0xBF}) ở đầu luồng byte để Excel hiển thị đúng
 *       tiếng Việt có dấu thay vì lỗi bảng mã ANSI.</li>
 *   <li>Chống tấn công CSV Formula Injection (DDE / Excel Formula): mọi ô có ký tự đầu raw
 *       là {@code \t}, {@code \r}, {@code \n} hoặc ký tự đầu sau {@code trim()} là
 *       {@code =}, {@code +}, {@code -}, {@code @} đều được chèn thêm dấu nháy đơn
 *       {@code '} ở phía trước để Excel coi là văn bản thuần.</li>
 * </ul>
 *
 * <p>Module sở hữu: shared.</p>
 * <p>Chủ sở hữu theo phân công: Người C.</p>
 */
public final class CsvExportUtil {

    private static final String UTF8_BOM = "\uFEFF";
    private static final String CRLF = "\r\n";

    private CsvExportUtil() {
    }

    /**
     * Chuyển tiêu đề và danh sách dòng dữ liệu thành mảng byte CSV mã hóa UTF-8 kèm BOM.
     *
     * @param headers danh sách tên cột ở dòng tiêu đề
     * @param rows danh sách từng dòng dữ liệu
     * @return mảng byte CSV hoàn chỉnh sẵn sàng trả về qua HTTP response
     */
    public static byte[] toUtf8BomCsv(List<String> headers, List<List<String>> rows) {
        return toCsvString(headers, rows).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Dựng chuỗi CSV hoàn chỉnh bắt đầu bằng UTF-8 BOM.
     *
     * @param headers danh sách tên cột ở dòng tiêu đề
     * @param rows danh sách từng dòng dữ liệu
     * @return chuỗi CSV chuẩn RFC 4180 kèm BOM
     */
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

    /**
     * Sanitize giá trị văn bản thô để chống CSV Formula Injection (không thực hiện bọc ngoặc kép).
     *
     * @param raw giá trị ô thô (có thể null)
     * @return chuỗi an toàn trước khi đưa vào format CSV
     */
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
