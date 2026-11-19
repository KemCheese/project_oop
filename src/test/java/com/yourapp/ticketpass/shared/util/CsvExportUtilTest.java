package com.yourapp.ticketpass.shared.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Unit Test - CsvExportUtil (UTF-8 BOM, RFC 4180, Formula Injection)")
class CsvExportUtilTest {

    @Test
    @DisplayName("Xuất byte CSV phải bắt đầu bằng UTF-8 BOM (EF BB BF) và dùng CRLF")
    void toUtf8BomCsv_ShouldIncludeUtf8BomAndCrlf() {
        byte[] bytes = CsvExportUtil.toUtf8BomCsv(
                List.of("Cột A", "Cột B"),
                List.of(List.of("Vé xem phim", "150000"))
        );

        assertThat(bytes.length).isGreaterThan(3);
        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(bytes[1]).isEqualTo((byte) 0xBB);
        assertThat(bytes[2]).isEqualTo((byte) 0xBF);

        String decoded = new String(bytes, StandardCharsets.UTF_8);
        assertThat(decoded).startsWith("\uFEFFCột A,Cột B\r\n");
        assertThat(decoded).contains("Vé xem phim,150000\r\n");
    }

    @Test
    @DisplayName("Sanitize CSV Formula Injection cho các tiền tố =, +, -, @, tab, CR và khoảng trắng đầu")
    void sanitizeFormulaCell_ShouldPrefixSingleQuoteOnDangerousPrefixes() {
        assertThat(CsvExportUtil.sanitizeFormulaCell("=SUM(A1:A5)")).isEqualTo("'=SUM(A1:A5)");
        assertThat(CsvExportUtil.sanitizeFormulaCell("+cmd|'/C calc'!A0")).isEqualTo("'+cmd|'/C calc'!A0");
        assertThat(CsvExportUtil.sanitizeFormulaCell("-1+1")).isEqualTo("'-1+1");
        assertThat(CsvExportUtil.sanitizeFormulaCell("@SUM(1,2)")).isEqualTo("'@SUM(1,2)");
        assertThat(CsvExportUtil.sanitizeFormulaCell("\t=HYPERLINK()")).isEqualTo("'\t=HYPERLINK()");
        assertThat(CsvExportUtil.sanitizeFormulaCell("\rcmd")).isEqualTo("'\rcmd");
        assertThat(CsvExportUtil.sanitizeFormulaCell("   =1+1")).isEqualTo("'   =1+1");
        assertThat(CsvExportUtil.sanitizeFormulaCell("\u00A0=1+1")).isEqualTo("'\u00A0=1+1");
        assertThat(CsvExportUtil.sanitizeFormulaCell("Vé concert bình thường")).isEqualTo("Vé concert bình thường");
        assertThat(CsvExportUtil.sanitizeFormulaCell("0")).isEqualTo("0");
    }

    @Test
    @DisplayName("Tuân thủ RFC 4180 khi cell chứa dấu phẩy, ngoặc kép hoặc xuống dòng")
    void toCsvString_ShouldQuoteAndDoubleEscapeRfc4180SpecialChars() {
        String csv = CsvExportUtil.toCsvString(
                List.of("Title", "Note"),
                List.of(List.of("Vé \"VIP\", Hàng A", "=cmd\nline2"))
        );

        assertThat(csv).contains("\"Vé \"\"VIP\"\", Hàng A\"");
        assertThat(csv).contains("\"'=cmd\nline2\"");
    }
}
