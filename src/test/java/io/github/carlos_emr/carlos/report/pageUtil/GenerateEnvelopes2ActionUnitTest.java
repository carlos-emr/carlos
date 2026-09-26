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
package io.github.carlos_emr.carlos.report.pageUtil;

import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.commn.dao.DemographicCustDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.openpdf.text.pdf.PRStream;
import org.openpdf.text.pdf.PdfDictionary;
import org.openpdf.text.pdf.PdfName;
import org.openpdf.text.pdf.PdfObject;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.PdfString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GenerateEnvelopes2Action}: the empty-selection guard, the in-memory PDF
 * download contract, and encoding of the printer-name preference (issue #3963).
 */
@DisplayName("GenerateEnvelopes2Action")
@Tag("unit")
@Tag("report")
class GenerateEnvelopes2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    private SecurityInfoManager securityInfoManager;
    private DemographicManager demographicManager;
    private UserPropertyDAO userPropertyDao;
    private LoggedInInfo loggedInInfo;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        demographicManager = mock(DemographicManager.class);
        userPropertyDao = mock(UserPropertyDAO.class);
        loggedInInfo = mock(LoggedInInfo.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(DemographicManager.class, demographicManager);
        registerMock(UserPropertyDAO.class, userPropertyDao);
        // DemographicData resolves this at construction; the envelope path never uses it.
        registerMock(DemographicCustDao.class, mock(DemographicCustDao.class));

        request = new MockHttpServletRequest("GET", "/carlos/report/GenerateEnvelopes");
        request.getSession().setAttribute("user", "999998");
        response = new MockHttpServletResponse();

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_report"), eq("r"), isNull()))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    /** Reads the document-level open-action JavaScript back out of a generated PDF. */
    private static String openActionScript(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        try {
            PdfDictionary openAction = reader.getCatalog().getAsDict(PdfName.OPENACTION);
            PdfObject js = PdfReader.getPdfObject(openAction.get(PdfName.JS));
            if (js.isStream()) {
                return new String(PdfReader.getStreamBytes((PRStream) js), StandardCharsets.ISO_8859_1);
            }
            return ((PdfString) js).toUnicodeString();
        } finally {
            reader.close();
        }
    }

    private static Demographic patient(String first, String last) {
        Demographic d = new Demographic();
        d.setFirstName(first);
        d.setLastName(last);
        d.setAddress("1 FAKE Street");
        d.setCity("Toronto");
        d.setProvince("ON");
        d.setPostal("M1M 1M1");
        return d;
    }

    @Nested
    @DisplayName("empty or unusable selection")
    class EmptySelection {

        @Test
        @DisplayName("should redisplay the letters page when no demos parameter is sent")
        void shouldReturnInput_whenDemosParameterMissing() {
            String result = new GenerateEnvelopes2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.INPUT);
            assertThat(request.getAttribute(GenerateEnvelopes2Action.NO_PATIENTS_SELECTED_ATTRIBUTE))
                    .isEqualTo(Boolean.TRUE);
            assertThat(response.getContentAsByteArray()).isEmpty();
            assertThat(response.getContentType()).isNull();
            verifyNoInteractions(demographicManager, userPropertyDao);
        }

        @Test
        @DisplayName("should redisplay the letters page when every demos value is blank, non-numeric or unknown")
        void shouldReturnInput_whenNoDemographicResolves() {
            request.addParameter("demos", "", "abc", "12;DROP", "99");
            when(demographicManager.getDemographic(loggedInInfo, "99")).thenReturn(null);

            String result = new GenerateEnvelopes2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.INPUT);
            assertThat(request.getAttribute(GenerateEnvelopes2Action.NO_PATIENTS_SELECTED_ATTRIBUTE))
                    .isEqualTo(Boolean.TRUE);
            assertThat(response.getContentAsByteArray()).isEmpty();
            verify(demographicManager).getDemographic(loggedInInfo, "99");
            verify(demographicManager, never()).getDemographic(eq(loggedInInfo), eq("abc"));
            verifyNoInteractions(userPropertyDao);
        }

        @Test
        @DisplayName("should ignore non-ASCII digits in demos values")
        void shouldSkipDemo_forNonAsciiDigits() {
            request.addParameter("demos", "١٢٣");

            String result = new GenerateEnvelopes2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.INPUT);
            verifyNoInteractions(demographicManager);
        }
    }

    @Nested
    @DisplayName("PDF download")
    class PdfDownload {

        @Test
        @DisplayName("should stream a PDF and return NONE when a patient is selected")
        void shouldStreamPdfAndReturnNone_whenPatientSelected() {
            request.addParameter("demos", "1", "abc", "2");
            when(demographicManager.getDemographic(loggedInInfo, "1")).thenReturn(patient("FAKE-Ann", "FAKE-Smith"));
            when(demographicManager.getDemographic(loggedInInfo, "2")).thenReturn(patient("FAKE-Bob", "FAKE-Jones"));

            String result = new GenerateEnvelopes2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getContentType()).isEqualTo("application/pdf");
            byte[] body = response.getContentAsByteArray();
            assertThat(new String(body, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
            assertThat(response.getContentLength()).isEqualTo(body.length);
            assertThat(response.getHeader("Content-Disposition")).startsWith("filename=\"envelopePDF-");
            assertThat(request.getAttribute(GenerateEnvelopes2Action.NO_PATIENTS_SELECTED_ATTRIBUTE)).isNull();
        }

        @Test
        @DisplayName("should embed the encoded printer name in the PDF open action")
        void shouldUsePrinterPreference_whenConfigured() throws Exception {
            request.addParameter("demos", "1");
            when(demographicManager.getDemographic(loggedInInfo, "1")).thenReturn(patient("FAKE-Ann", "FAKE-Smith"));
            UserProperty printer = new UserProperty();
            printer.setValue("Envelope Printer");
            when(userPropertyDao.getProp("999998", UserProperty.DEFAULT_PRINTER_PDF_ENVELOPE)).thenReturn(printer);
            UserProperty silent = new UserProperty();
            silent.setValue("YES");
            when(userPropertyDao.getProp("999998", UserProperty.DEFAULT_PRINTER_PDF_ENVELOPE_SILENT_PRINT))
                    .thenReturn(silent);

            String result = new GenerateEnvelopes2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            String script = openActionScript(response.getContentAsByteArray());
            assertThat(script).contains("params.printerName='Envelope Printer';")
                    .contains("interactionLevel.silent");
        }

        @Test
        @DisplayName("should throw SecurityException without _report read")
        void shouldThrowSecurityException_whenReportPrivilegeMissing() {
            when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_report"), eq("r"), isNull()))
                    .thenReturn(false);
            request.addParameter("demos", "1");

            assertThatThrownBy(() -> new GenerateEnvelopes2Action().execute())
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_report)");
            verifyNoInteractions(demographicManager, userPropertyDao);
        }
    }

    @Nested
    @DisplayName("helpers")
    class Helpers {

        @Test
        @DisplayName("should escape quotes in the printer name so it cannot leave the JS string")
        void shouldEncodePrinterName_withQuoteBreakout() {
            String script = GenerateEnvelopes2Action.buildAutoPrintScript("x';app.alert(1);//", false);

            assertThat(script).doesNotContain("x';app.alert");
            assertThat(script).contains("params.printerName='x\\x27;app.alert(1);\\/\\/';");
            assertThat(script).endsWith("this.print(params);");
            assertThat(script).doesNotContain("interactionLevel.silent");
        }

        @Test
        @DisplayName("should return no script when no printer is configured")
        void shouldReturnEmptyScript_whenPrinterBlank() {
            assertThat(GenerateEnvelopes2Action.buildAutoPrintScript("", true)).isEmpty();
            assertThat(GenerateEnvelopes2Action.buildAutoPrintScript(null, true)).isEmpty();
        }

        @Test
        @DisplayName("should render missing name and address parts as empty text")
        void shouldFormatLabel_withoutNullLiterals() {
            Demographic d = new Demographic();
            d.setFirstName("FAKE-Ann");

            String label = GenerateEnvelopes2Action.formatEnvelopeLabel(d);

            assertThat(label).doesNotContain("null");
            assertThat(label).isEqualTo("FAKE-Ann \n\n, \n");
        }

        @Test
        @DisplayName("should format a full mailing block on four lines")
        void shouldFormatLabel_forCompleteAddress() {
            assertThat(GenerateEnvelopes2Action.formatEnvelopeLabel(patient("FAKE-Ann", "FAKE-Smith")))
                    .isEqualTo("FAKE-Ann FAKE-Smith\n1 FAKE Street\nToronto, ON\nM1M 1M1");
        }
    }

    @Test
    @DisplayName("should not look up patients for a demos value with surrounding text")
    void shouldSkipDemo_forPartiallyNumericValue() {
        request.addParameter("demos", " 1", "1 ");

        new GenerateEnvelopes2Action().execute();

        verify(demographicManager, never()).getDemographic(any(LoggedInInfo.class), anyString());
    }
}
