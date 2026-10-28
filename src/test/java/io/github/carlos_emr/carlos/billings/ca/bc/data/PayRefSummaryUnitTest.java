/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.bc.data;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.carlos_emr.carlos.billings.ca.bc.MSP.MSPReconcile;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.engine.export.JRCsvExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleWriterExporterOutput;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** BC financial reports must retain their CAD amounts under the packaged C.UTF-8 server locale. */
@Tag("unit")
@Tag("billing")
@ResourceLock("java.util.Locale.default")
class PayRefSummaryUnitTest {
    private Locale originalFormatLocale;

    @BeforeEach
    void rememberLocale() {
        originalFormatLocale = Locale.getDefault(Locale.Category.FORMAT);
    }

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(Locale.Category.FORMAT, originalFormatLocale);
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "en-CA", "fr-FR", "de-DE"})
    void shouldFormatEveryPaymentMethodInCanadianDollars_underDifferentHostLocales(String locale) {
        Locale.setDefault(Locale.Category.FORMAT, Locale.forLanguageTag(locale));
        PayRefSummary summary = new PayRefSummary();
        for (String method : List.of(MSPReconcile.PAYTYPE_CASH, MSPReconcile.PAYTYPE_CHEQUE,
                MSPReconcile.PAYTYPE_VISA, MSPReconcile.PAYTYPE_MC, MSPReconcile.PAYTYPE_AMEX,
                MSPReconcile.PAYTYPE_ELECTRONIC, MSPReconcile.PAYTYPE_DEBIT, MSPReconcile.PAYTYPE_OTHER)) {
            summary.addIncValue(method, 33.70);
        }

        assertThat(List.of(summary.getCash(), summary.getCheque(), summary.getVisa(), summary.getMc(),
                summary.getAmex(), summary.getElectronic(), summary.getDebit(), summary.getOther()))
                .containsOnly("$33.70");
        assertThat(summary.getTotal()).isEqualTo("$269.60");
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "en-CA", "fr-FR", "de-DE"})
    void shouldRenderCanadianPdfAndCsvAmounts_whenPaymentIncludesRefund(String locale) throws Exception {
        Locale.setDefault(Locale.Category.FORMAT, Locale.forLanguageTag(locale));
        PayRefSummary paid = new PayRefSummary();
        paid.addIncValue(MSPReconcile.PAYTYPE_CASH, 33.70);
        // The report action includes negative payments in sumPayed and also identifies them in sumRefunded.
        paid.addIncValue(MSPReconcile.PAYTYPE_CASH, -10.00);
        PayRefSummary refunded = new PayRefSummary();
        refunded.addIncValue(MSPReconcile.PAYTYPE_CASH, -10.00);
        Map<String, Object> params = new HashMap<>();
        params.put("sumPayed", paid);
        params.put("sumRefunded", refunded);
        params.put("provider", "QF");
        params.put("payee", "YF");
        params.put("account", "ALL");
        params.put("insurers", "MSP");
        params.put("startDate", "2026-10-19");
        params.put("endDate", "2026-10-19");

        for (String template : List.of("pdf_rep_payref.jrxml", "pdf_rep_payref_sum.jrxml",
                "csv_rep_payref.jrxml", "csv_rep_payref_sum.jrxml")) {
            try (var stream = getClass().getResourceAsStream("/oscar/oscarBilling/ca/bc/reports/" + template)) {
                assertThat(stream).as(template).isNotNull();
                var report = JasperCompileManager.compileReport(stream);
                List<Map<String, ?>> rows = new ArrayList<>();
                for (String amount : List.of("33.70", "-10.00")) {
                    Map<String, Object> row = new HashMap<>();
                    for (var field : report.getFields()) row.put(field.getName(), "");
                    row.put("amount", amount);
                    row.put("serviceDate", "20261019");
                    row.put("paymentDate", "20261019");
                    row.put("type", MSPReconcile.PAYTYPE_CASH);
                    rows.add(row);
                }
                var print = JasperFillManager.fillReport(report, params, new JRMapCollectionDataSource(rows));
                if (template.startsWith("csv_")) {
                    StringWriter output = new StringWriter();
                    JRCsvExporter exporter = new JRCsvExporter();
                    exporter.setExporterInput(new SimpleExporterInput(print));
                    exporter.setExporterOutput(new SimpleWriterExporterOutput(output));
                    exporter.exportReport();
                    assertThat(output.toString()).as(template).contains("-$10.00").doesNotContain("$13.70", "\u00a4", "\u20ac");
                    assertThat(output.toString()).as(template)
                            .contains(template.contains("_sum") ? "$23.70" : "$33.70");
                } else {
                byte[] pdf = JasperExportManager.exportReportToPdf(print);
                try (var document = Loader.loadPDF(pdf)) {
                    String text = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
                    assertThat(text).as(template).containsPattern("TOTAL:\\s*\\$23\\.70\\b").doesNotContain("$13.70");
                }
                }
            }
        }
    }
}
