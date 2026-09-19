/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.commn.web;

import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openpdf.text.Document;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.PdfWriter;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class ReferralLabelUnitTest extends CarlosWebTestBase {
    private PrintReferralLabel2Action action() throws Exception {
        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_demographic"), eq("r"), isNull())).thenReturn(true);
        PrintReferralLabel2Action action = spy(new PrintReferralLabel2Action());
        doReturn(mock(JasperReport.class)).when(action).loadTemplate();
        return action;
    }

    private byte[] pdf(String text) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document();
        PdfWriter.getInstance(document, output);
        document.open();
        document.add(new Paragraph(text));
        document.close();
        return output.toByteArray();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Dr."})
    void shouldRenderNameAndAddress_whenSalutationIsOptional(String salutation) throws Exception {
        try (var template = getClass().getResourceAsStream("/org/oscarehr/common/web/reflabel.xml")) {
            JasperReport report = JasperCompileManager.compileReport(template);
            assertThat(report.getQuery().getText()).contains("$P{billingreferral_no}");
            Map<String, Object> row = new HashMap<>();
            row.put("salutation", salutation);
            row.put("fName", "TestGiven");
            row.put("lName", "TestFamily");
            row.put("address", "123 Fixture Street");
            JasperPrint print = JasperFillManager.fillReport(report, new HashMap<>(),
                    new JRMapCollectionDataSource(List.of(row)));
            try (PdfReader reader = new PdfReader(JasperExportManager.exportReportToPdf(print))) {
                assertThat(reader.getNumberOfPages()).isEqualTo(1);
                assertThat(new PdfTextExtractor(reader).getTextFromPage(1))
                        .contains("TestGiven", "TestFamily", "123 Fixture Street").doesNotContain("null");
            }
        }
    }

    @Test
    void shouldReturnCompleteBatch_whenAllLabelsRender() throws Exception {
        PrintReferralLabel2Action action = action();
        mockRequest.setParameter("ids", "1,2");
        doReturn(pdf("First label")).when(action).renderLabel(any(), eq("1"));
        doReturn(pdf("Second label")).when(action).renderLabel(any(), eq("2"));
        assertThat(action.execute()).isEqualTo("none");
        assertThat(mockResponse.getContentType()).isEqualTo("application/pdf");
        try (PdfReader reader = new PdfReader(mockResponse.getContentAsByteArray())) {
            assertThat(reader.getNumberOfPages()).isEqualTo(2);
            assertThat(new PdfTextExtractor(reader).getTextFromPage(1)).contains("First label");
            assertThat(new PdfTextExtractor(reader).getTextFromPage(2)).contains("Second label");
        }
    }

    @Test
    void shouldPreserveChecklistAndReturnError_whenLaterLabelFails() throws Exception {
        PrintReferralLabel2Action action = action();
        ProfessionalSpecialist first = mock(ProfessionalSpecialist.class);
        ProfessionalSpecialist second = mock(ProfessionalSpecialist.class);
        when(first.getId()).thenReturn(1);
        when(second.getId()).thenReturn(2);
        List<ProfessionalSpecialist> selected = new ArrayList<>(List.of(first, second));
        mockRequest.getSession().setAttribute("billingReferralAdminCheckList", selected);
        mockRequest.setParameter("useCheckList", "true");
        doReturn(pdf("First label")).when(action).renderLabel(any(), eq("1"));
        doThrow(new JRException("sensitive template detail")).when(action).renderLabel(any(), eq("2"));
        assertThat(action.execute()).isEqualTo("none");
        assertThat(mockResponse.getStatus()).isEqualTo(500);
        assertThat(mockResponse.getContentAsByteArray()).isEmpty();
        assertThat(mockResponse.getErrorMessage()).doesNotContain("sensitive template detail");
        assertThat(mockRequest.getSession().getAttribute("billingReferralAdminCheckList")).isSameAs(selected);
    }

    @Test
    void shouldReturnErrorWithoutPdf_whenTemplateCannotCompile() throws Exception {
        PrintReferralLabel2Action action = action();
        mockRequest.setParameter("billingreferralNo", "1");
        doThrow(new JRException("private path")).when(action).loadTemplate();
        action.execute();
        assertThat(mockResponse.getStatus()).isEqualTo(500);
        assertThat(mockResponse.getContentAsByteArray()).isEmpty();
        assertThat(mockResponse.getErrorMessage()).doesNotContain("private path");
    }

    @Test
    void shouldRejectEmptySelection_withoutGeneratingLabels() throws Exception {
        PrintReferralLabel2Action action = action();
        mockRequest.setParameter("useCheckList", "true");
        action.execute();
        assertThat(mockResponse.getStatus()).isEqualTo(400);
        verify(action, never()).loadTemplate();
    }
}
