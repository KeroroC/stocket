package com.stocket.system.export;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CsvCellSanitizerTest {

    @Test
    void prefixesSpreadsheetFormulaTriggersWithoutChangingOrdinaryUnicode() {
        assertThat(CsvCellSanitizer.sanitize("=SUM(1,1)")).isEqualTo("'=SUM(1,1)");
        assertThat(CsvCellSanitizer.sanitize("+1")).isEqualTo("'+1");
        assertThat(CsvCellSanitizer.sanitize("-1")).isEqualTo("'-1");
        assertThat(CsvCellSanitizer.sanitize("@cmd")).isEqualTo("'@cmd");
        assertThat(CsvCellSanitizer.sanitize("\tvalue")).isEqualTo("'\tvalue");
        assertThat(CsvCellSanitizer.sanitize("\rvalue")).isEqualTo("'\rvalue");
        assertThat(CsvCellSanitizer.sanitize("中文,\"换行\"\n内容")).isEqualTo("中文,\"换行\"\n内容");
        assertThat(CsvCellSanitizer.sanitize(null)).isEmpty();
    }
}
