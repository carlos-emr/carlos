/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report.export;

import io.github.carlos_emr.OscarDocumentCreator;
import io.github.carlos_emr.carlos.billings.ca.bc.data.PayRefSummary;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;

/** Real Jasper compilation, PDF rendering and pre-response failure checks. @since 2026-11-09 */
@Tag("unit")
class BcPdfTemplatesUnitTest {
    private static final String ROOT="/oscar/oscarBilling/ca/bc/reports/";
    private static HashMap<String,Object> parameters() {
        var params=new HashMap<String,Object>();
        for(String key:List.of("provider","payee","account","insurers","billCnt","demNoCnt","billMasterCnt","amtSubmitted","startDate","endDate")) params.put(key,"0");
        params.put("sumPayed",new PayRefSummary()); params.put("sumRefunded",new PayRefSummary());
        return params;
    }
    private static Map<String,Object> row() {
        var values=new HashMap<String,Object>();
        for(String name:List.of("providerFirstName","providerLastName","billingtype","serviceDate","billMasterNo","billing_no","demoName","demoNo","billingUnit","code","amount","reason","amtOwing","accountNo","accountName","adjustmentCode","adjustmentCodeAmt","adjustmentCodeDesc","serviceDateRange","expString","paymentDate","paymentMethod","type","dx","dx1","dx2","dx3","birthDate","phone","phone2","serviceStartTime","serviceEndTime","serviceToDay","rejectionDate","sex","referral1","referral2","mvaCode","familyDoc","serviceLocation","seqNum")) values.put(name,"");
        values.put("providerFirstName","FAKE"); values.put("providerLastName","Provider");
        values.put("amount","12.34"); values.put("amtOwing","5.67"); values.put("adjustmentCodeAmt","0.00");
        values.put("serviceDate","20260801"); values.put("paymentDate","20260802"); values.put("rejectionDate","20260803");
        values.put("demoName","FAKE-PdfContract"); values.put("demoNo","9556"); values.put("billing_no","9556"); values.put("billMasterNo","9557");
        values.put("serviceDateRange","4"); values.put("billingUnit","1"); values.put("type","PMT"); values.put("paymentMethod","1");
        return values;
    }

    @ParameterizedTest
    @ValueSource(strings={"invoice","account_rec","wo","rej","payref","payref_sum"})
    void shouldRenderValidAccountingPdf_fromTheShippedTemplate(String report) throws Exception {
        var response=new MockHttpServletResponse();
        PdfDownload.inline(response,"report.pdf", output -> {
            try(var template=getClass().getResourceAsStream(ROOT+"pdf_rep_"+report+".jrxml")) {
                new OscarDocumentCreator().fillDocumentStream(parameters(),output,"pdf",template,new JRMapCollectionDataSource(List.of(row())));
            }
        });
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        try(var document=Loader.loadPDF(response.getContentAsByteArray())) { assertThat(document.getNumberOfPages()).isPositive(); }
    }

    @ParameterizedTest
    @ValueSource(strings={"invoice","account_rec","wo","rej","payref","payref_sum"})
    void shouldRenderAValidEmptyReport_whenTheSuccessfulQueryHasNoRows(String report) throws Exception {
        var response=new MockHttpServletResponse();
        PdfDownload.inline(response,"report.pdf", output -> {
            try(var template=getClass().getResourceAsStream(ROOT+"pdf_rep_"+report+".jrxml")) {
                new OscarDocumentCreator().fillDocumentStream(parameters(),output,"pdf",template,new JRMapCollectionDataSource(List.of()));
            }
        });
        assertThat(response.getStatus()).as(report).isEqualTo(200);
        try(var document=Loader.loadPDF(response.getContentAsByteArray())) { assertThat(document.getNumberOfPages()).isPositive(); }
    }

    @Test
    void shouldRenderExactS23Totals_fromMultipleDecodedAdjustmentRows() throws Exception {
        var rows = new java.util.ArrayList<Map<String, ?>>();
        for (String cents : List.of("000010", "000020")) {
            var row = new HashMap<String, Object>();
            for (String key : List.of("t_ajc", "adj_desc", "t_aji", "t_calcmethod", "t_rpercent", "t_opercent")) row.put(key, "0");
            for (String key : List.of("t_gamount", "t_ramount", "t_oamount", "t_balancefwd", "t_adjmade", "t_adjoutstanding")) row.put(key, cents);
            rows.add(row);
        }
        try (var template = getClass().getResourceAsStream(ROOT + "msppremsum.s23.jrxml")) {
            var report = JasperCompileManager.compileReport(template);
            var print = net.sf.jasperreports.engine.JasperFillManager.fillReport(report, new HashMap<>(),
                    new JRMapCollectionDataSource(rows));
            try (var pdf = Loader.loadPDF(net.sf.jasperreports.engine.JasperExportManager.exportReportToPdf(print))) {
                assertThat(new PDFTextStripper().getText(pdf)).contains("0.30");
            }
        }
    }

    @Test
    void shouldLogSanitizedCauseStacks_whenTheRendererFails() throws Exception {
        var response = new MockHttpServletResponse();
        try (var logs = io.github.carlos_emr.carlos.test.logging.LogCapture.forLogger(PdfDownload.class)) {
            PdfDownload.inline(response, "report.pdf", output -> {
                throw new java.io.IOException("FAKE-PHI-OUTER", new IllegalArgumentException("FAKE-PHI-CAUSE"));
            });
            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(logs.events()).anySatisfy(event -> {
                Throwable error = event.getThrown();
                assertThat(error).isNotNull();
                assertThat(error.getMessage()).isEqualTo(java.io.IOException.class.getName());
                assertThat(error.getCause().getMessage()).isEqualTo(IllegalArgumentException.class.getName());
                assertThat(error.getStackTrace()).isNotEmpty();
            });
            assertThat(logs.messages()).allSatisfy(message -> assertThat(message).doesNotContain("FAKE-PHI"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"invoice", "account_rec"})
    void shouldDiscardEntireDocument_whenALaterAccountingAmountCannotBeRendered(String report) throws Exception {
        var response=new MockHttpServletResponse();
        response.setHeader("X-Partial-Receipt", "discard-me");
        var invalid=row(); invalid.put("amount","synthetic-invalid-amount"); invalid.put("amtOwing", "");
        PdfDownload.inline(response,"report.pdf", output -> {
            try(var template=getClass().getResourceAsStream(ROOT+"pdf_rep_"+report+".jrxml")) {
                new OscarDocumentCreator().fillDocumentStream(parameters(),output,"pdf",template,new JRMapCollectionDataSource(List.of(row(),invalid)));
            }
        });
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getHeader("Content-Disposition")).isNull();
        assertThat(response.getHeader("X-Partial-Receipt")).isNull();
    }

    @Test
    void shouldMarkKnownOnlySummary_whenUnparsedAmountFieldsAreExcluded() throws Exception {
        var response=new MockHttpServletResponse();
        var params=parameters(); params.put("unparsedAmountCount",2);
        PdfDownload.inline(response,"report.pdf", output -> {
            try(var template=getClass().getResourceAsStream(ROOT+"pdf_rep_payref_sum.jrxml")) {
                new OscarDocumentCreator().fillDocumentStream(params,output,"pdf",template,new JRMapCollectionDataSource(List.of(row())));
            }
        });
        assertThat(response.getStatus()).isEqualTo(200);
        try(var document=Loader.loadPDF(response.getContentAsByteArray())) {
            assertThat(new PDFTextStripper().getText(document)).contains("Known amounts only", "Unparsed amount fields excluded: 2");
        }
    }

    @ParameterizedTest
    @ValueSource(strings={"pdf_rep_msprem.jrxml","pdf_rep_mspremsum.jrxml","msppremsum.practsum.jrxml","msppremsum.s23.jrxml","broadcastmessages.jrxml"})
    void shouldCompileRemittanceAndSubreportTemplates_withStrictAmountDecoding(String report) throws Exception {
        try(var template=getClass().getResourceAsStream(ROOT+report)) {
            assertThat(template).as(report).isNotNull();
            assertThat(JasperCompileManager.compileReport(template)).isNotNull();
        }
    }
}
