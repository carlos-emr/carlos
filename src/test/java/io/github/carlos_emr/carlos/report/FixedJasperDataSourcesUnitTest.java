/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;
import io.github.carlos_emr.carlos.commn.dao.FixedJasperReportDao.InvoiceRow;
import io.github.carlos_emr.carlos.commn.dao.FixedJasperReportDao.ItemRow;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.design.JRDesignField;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class FixedJasperDataSourcesUnitTest {
    @Test
    void shouldRejectUnknownAndMismatchedFields_withoutReturningBlankValues() throws Exception {
        var source = FixedJasperDataSources.invoices(List.of(invoice()));
        assertThat(source.next()).isTrue();
        assertThatThrownBy(() -> source.getFieldValue(field("missing", String.class))).isInstanceOf(JRException.class);
        assertThatThrownBy(() -> source.getFieldValue(field("paid", String.class))).isInstanceOf(JRException.class);
        assertThat(source.getFieldValue(field("paid", BigDecimal.class))).isEqualTo(new BigDecimal("1.20"));
    }

    @Test
    void shouldCreateIndependentRewindableSubreportCursors_forEveryEvaluation() throws Exception {
        var source = FixedJasperDataSources.invoices(List.of(invoice()));
        source.next();
        var first = (JRDataSource) source.getFieldValue(field("items", JRDataSource.class));
        var second = (JRDataSource) source.getFieldValue(field("items", JRDataSource.class));
        assertThat(first.next()).isTrue();
        assertThat(second.next()).isTrue();
        assertThat(first.getFieldValue(field("fee", String.class))).isEqualTo("1.2");
        assertThat(first.next()).isFalse();
        ((net.sf.jasperreports.engine.JRRewindableDataSource) first).moveFirst();
        assertThat(first.next()).isTrue();
    }

    private static InvoiceRow invoice() {
        return new InvoiceRow(1, Date.valueOf("2025-01-01"), new BigDecimal("2.30"),
                new BigDecimal("1.20"), List.of(new ItemRow(1, "A", "1.2")));
    }
    private static JRDesignField field(String name, Class<?> type) {
        var field = new JRDesignField(); field.setName(name); field.setValueClass(type); return field;
    }
}
