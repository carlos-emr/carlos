/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.bc.MSP;

import java.io.OutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("web")
class BcCsvDownloadUnitTest extends CarlosWebTestBase {
    @Override
    protected void setUpActionContext() {
        super.setUpActionContext();
        org.apache.struts2.ActionContext.getContext().withServletContext(getMockRequest().getServletContext()).bind();
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "REP_PAYREF,Pri,NaN,NaN,NaN",
            "REP_PAYREF_SUM,Pri,NaN,Known amounts only,12.50",
            "REP_PAYREF_SUM,MSP,0.00,Known amounts only,12.50",
            "REP_PAYREF,MSP,Unparsed Teleplan amount: 12.3,Unparsed,12.3",
            "REP_PAYREF_SUM,MSP,Unparsed Teleplan amount: 12.3,Known amounts only,12.50"})
    void shouldRenderKnownOnlyPaymentPdf_whenAnAmountCannotBeParsed(
            String reportType, String insurer, String source, String expectedText, String expectedVisibleAmount) throws Exception {
        var unknown = new io.github.carlos_emr.carlos.entities.MSPBill();
        unknown.billingtype = insurer;
        unknown.amount = source;
        unknown.paymentMethod = "1";
        unknown.paymentDate = "20261101";
        unknown.serviceDate = "20261101";
        unknown.recordUnparsedAmount();
        var known = new io.github.carlos_emr.carlos.entities.MSPBill();
        known.billingtype = "Pri";
        known.amount = "12.50";
        known.paymentMethod = "1";
        known.paymentDate = "20261101";
        known.serviceDate = "20261101";
        try (var reads = mockConstruction(MSPReconcile.class, (reader, context) -> {
            var search = reader.new BillSearch();
            search.list = new java.util.ArrayList<>(java.util.List.of(unknown, known));
            when(reader.getPayments(any(), any(), any(), any(), any(), anyBoolean(), anyBoolean(),
                    anyBoolean(), anyBoolean(), any())).thenReturn(search);
            when(reader.getProvider(any(), anyInt())).thenReturn(new io.github.carlos_emr.carlos.entities.Provider());
        })) {
            var action = new CreateBillingReport2Action();
            action.setDocFormat("pdf");
            action.setRepType(reportType);
            action.setSelAccount("ALL");
            action.setSelPayee("ALL");
            action.setSelProv("ALL");
            assertThat(executeAction(action)).isEqualTo("none");
            assertThat(getMockResponse().getStatus()).isEqualTo(200);
            try (var pdf = org.apache.pdfbox.Loader.loadPDF(getMockResponse().getContentAsByteArray())) {
                String text = new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);
                assertThat(text).as(reportType).contains("12.50", expectedText, "Unparsed amount fields excluded: 1")
                        .doesNotContain("$NaN", "$12.30");
                assertThat(text.replaceAll("\\s+", "")).contains(expectedVisibleAmount);
            }
        }
    }

    @BeforeEach
    void prepareSecurity() {
        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);
        setSessionAttribute(LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY", mockLoggedInInfo);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("r"), isNull())).thenReturn(true);
    }

    @Test
    void shouldDiscardPartialFile_whenGenerationFails() throws Exception {
        var action = new CreateBillingReport2Action() {
            @Override protected void writeCsv(OutputStream output) throws Exception {
                output.write("partial synthetic data".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                throw new java.io.IOException("synthetic sensitive error");
            }
        };
        action.setDocFormat("csv"); action.setRepType("REP_WO");
        try (LogCapture logs = LogCapture.forLogger(CreateBillingReport2Action.class)) {
            assertThat(executeAction(action)).isEqualTo("none");
            assertThat(getMockResponse().getStatus()).isEqualTo(500);
            assertThat(getMockResponse().getContentAsByteArray()).isEmpty();
            assertThat(getMockResponse().getHeader("Content-Disposition")).isNull();
            assertThat(logs.events()).isNotEmpty()
                    .allSatisfy(event -> assertThat(event.getThrown()).isNull());
            assertThat(logs.messages())
                    .allSatisfy(message -> assertThat(message).doesNotContain("synthetic sensitive error"))
                    .anySatisfy(message -> assertThat(message).contains(java.io.IOException.class.getName()));
        }
    }

    @Test
    void shouldSendCompleteFile_whenGenerationSucceeds() throws Exception {
        var action = new CreateBillingReport2Action() {
            @Override protected void writeCsv(OutputStream output) throws Exception {
                assertThat(getMockResponse().getHeader("Content-Disposition")).isNull();
                output.write("\"Header\"\n\"Synthetic\"\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        };
        action.setDocFormat("csv"); action.setRepType("REP_WO");
        assertThat(executeAction(action)).isEqualTo("none");
        assertThat(getMockResponse().getStatus()).isEqualTo(200);
        assertThat(getMockResponse().getContentAsString()).isEqualTo("\"Header\"\n\"Synthetic\"\n");
        assertThat(getMockResponse().getHeader("Content-Disposition")).isEqualTo("attachment; filename=\"REP_WO.csv\"");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "00000000", "20260230", "not a date"})
    void shouldDownloadUnagedClaim_whenServiceDateIsUnavailable(String serviceDate) throws Exception {
        var action = new CreateBillingReport2Action() {
            @Override protected void writeCsv(OutputStream output) throws Exception {
                var bill = new io.github.carlos_emr.carlos.entities.MSPBill();
                bill.setServiceDate(serviceDate);
                bill.setAmount("23.45");
                bill.setAmtOwing("23.45");
                io.github.carlos_emr.carlos.report.export.BcBillingCsv.write(output, "REP_ACCOUNT_REC", java.util.List.of(bill));
            }
        };
        action.setDocFormat("csv"); action.setRepType("REP_ACCOUNT_REC");
        assertThat(executeAction(action)).isEqualTo("none");
        assertThat(getMockResponse().getStatus()).isEqualTo(200);
        assertThat(getMockResponse().getContentAsString()).contains("\"Unknown age\"", ",\"23.45\"\n");
        assertThat(getMockResponse().getHeader("Content-Disposition")).isEqualTo("attachment; filename=\"REP_ACCOUNT_REC.csv\"");
    }

    @Test
    void shouldDenyExport_whenBillingPrivilegeIsMissing() {
        when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any())).thenReturn(false);
        var action = new CreateBillingReport2Action();
        action.setDocFormat("csv"); action.setRepType("REP_WO");
        assertThatThrownBy(() -> executeAction(action)).isInstanceOf(SecurityException.class);
        assertThat(getMockResponse().getContentAsByteArray()).isEmpty();
    }
    @Test
    void shouldDiscardPartialPdf_whenRenderingFails() throws Exception {
        var action = new CreateBillingReport2Action() {
            @Override protected void renderPdf(OutputStream output) throws java.io.IOException {
                output.write("%PDF-partial synthetic content".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                throw new java.io.IOException("synthetic sensitive failure");
            }
        };
        action.setDocFormat("pdf"); action.setRepType("REP_INVOICE");
        try (LogCapture logs = LogCapture.forLogger(CreateBillingReport2Action.class)) {
            assertThat(executeAction(action)).isEqualTo("none");
            assertThat(getMockResponse().getStatus()).isEqualTo(500);
            assertThat(getMockResponse().getContentAsByteArray()).isEmpty();
            assertThat(getMockResponse().getHeader("Content-Disposition")).isNull();
            assertThat(logs.events()).isNotEmpty().allSatisfy(event -> assertThat(event.getThrown()).isNull());
            assertThat(logs.messages()).allSatisfy(message -> assertThat(message).doesNotContain("synthetic sensitive failure"));
        }
    }

    @Test
    void shouldRefuseNonPdfOutput_whenRendererReturnsNormally() throws Exception {
        var action = new CreateBillingReport2Action() {
            @Override protected void renderPdf(OutputStream output) throws java.io.IOException {
                output.write("not a PDF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            }
        };
        action.setDocFormat("pdf"); action.setRepType("REP_INVOICE");
        assertThat(executeAction(action)).isEqualTo("none");
        assertThat(getMockResponse().getStatus()).isEqualTo(500);
        assertThat(getMockResponse().getContentAsByteArray()).isEmpty();
        assertThat(getMockResponse().getHeader("Content-Disposition")).isNull();
    }

    @Test
    void shouldRefuseTruncatedPdf_whenItsSignatureIsPresentButTrailerIsMissing() throws Exception {
        var action = new CreateBillingReport2Action() {
            @Override protected void renderPdf(OutputStream output) throws java.io.IOException {
                output.write("%PDF-1.7\ntruncated document".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            }
        };
        action.setDocFormat("pdf"); action.setRepType("REP_INVOICE");
        assertThat(executeAction(action)).isEqualTo("none");
        assertThat(getMockResponse().getStatus()).isEqualTo(500);
        assertThat(getMockResponse().getContentAsByteArray()).isEmpty();
        assertThat(getMockResponse().getHeader("Content-Disposition")).isNull();
    }

    @Test
    void shouldSendCompletePdf_whenGenerationSucceeds() throws Exception {
        byte[] expected = "%PDF-1.7\nsynthetic complete document\n%%EOF\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        var action = new CreateBillingReport2Action() {
            @Override protected void renderPdf(OutputStream output) throws java.io.IOException {
                assertThat(getMockResponse().getHeader("Content-Disposition")).isNull();
                output.write(expected);
            }
        };
        action.setDocFormat("pdf"); action.setRepType("REP_INVOICE");
        assertThat(executeAction(action)).isEqualTo("none");
        assertThat(getMockResponse().getStatus()).isEqualTo(200);
        assertThat(getMockResponse().getContentType()).isEqualTo("application/pdf");
        assertThat(getMockResponse().getHeader("Content-Disposition")).isEqualTo("attachment; filename=\"REP_INVOICE.pdf\"");
        assertThat(getMockResponse().getContentAsByteArray()).isEqualTo(expected);
    }

    @Test
    void shouldReturnBadRequest_whenProviderHasNoPractitionerNumber() throws Exception {
        var action = new CreateBillingReport2Action() {
            @Override protected void renderPdf(OutputStream output) {
                throw new io.github.carlos_emr.carlos.billings.ca.bc.data.BillingmasterDAO.MissingBcPaymentProviderException();
            }
        };
        action.setDocFormat("pdf"); action.setRepType("REP_PAYREF");
        assertThat(executeAction(action)).isEqualTo("none");
        assertThat(getMockResponse().getStatus()).isEqualTo(400);
        assertThat(getMockResponse().getContentAsByteArray()).isEmpty();
        assertThat(getMockResponse().getHeader("Content-Disposition")).isNull();
    }

}
