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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.eform.actions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import org.apache.struts2.ServletActionContext;

import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.eform.EFormLoader;
import io.github.carlos_emr.carlos.eform.EFormUtil;
import io.github.carlos_emr.carlos.eform.util.EFormRenderApprovalService;
import io.github.carlos_emr.carlos.eform.util.EFormRenderCompletenessReport;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.EformDataManager;
import io.github.carlos_emr.carlos.managers.EmailManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.EformContentUnavailableException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code print=true} is the legacy contract of {@code library/eforms/printControl.js}: the "PDF" and
 * "Submit &amp; PDF" buttons it injects beside an eForm's SubmitButton. The Rich Text Letter loads that
 * library, and the eForm Generator and Visual Editor emit it into every generated clinic eForm.
 *
 * <p>Until this alias existed the flag resolved to a {@code print} result that {@code struts-eform.xml}
 * never mapped — an error page after the record had already been saved. In practice that was masked
 * by {@code printControl.js} never appending its hidden input, so the buttons were a plain Save with no
 * PDF. The flag now folds into the toolbar's save-and-download workflow. The last test pins the general
 * invariant behind the latent half: every literal result the action can return must be declared for
 * {@code eform/addEForm}.</p>
 *
 * @since 2026-09-02
 */
@DisplayName("AddEForm2Action legacy print flag")
@Tag("unit")
@Tag("eform")
class AddEForm2ActionPrintAliasUnitTest extends CarlosUnitTestBase {

    private static final Path ADD_EFORM_ACTION = Path.of(
            "src", "main", "java", "io", "github", "carlos_emr", "carlos",
            "eform", "actions", "AddEForm2Action.java");
    private static final Path STRUTS_EFORM_XML = Path.of(
            "src", "main", "webapp", "WEB-INF", "classes", "struts-eform.xml");

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockedStatic<EFormUtil> eFormUtilMock;
    private MockedStatic<EFormLoader> eFormLoaderMock;
    private AutoCloseable mockitoMocks;

    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private EformDataManager mockEformDataManager;
    @Mock private DocumentAttachmentManager mockDocumentAttachmentManager;
    @Mock private EmailManager mockEmailManager;
    @Mock private DemographicManager mockDemographicManager;
    @Mock private EFormRenderApprovalService mockRenderApprovalService;
    @Mock private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;

    @BeforeEach
    void setUp() {
        mockitoMocks = MockitoAnnotations.openMocks(this);

        mockRequest = new MockHttpServletRequest();
        mockRequest.setMethod("POST");
        mockResponse = new MockHttpServletResponse();

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerMock(EformDataManager.class, mockEformDataManager);
        registerMock(DocumentAttachmentManager.class, mockDocumentAttachmentManager);
        registerMock(EmailManager.class, mockEmailManager);
        // generateFileName() resolves the patient's name for the download filename.
        registerMock(DemographicManager.class, mockDemographicManager);
        // offerDownloadApproval() issues the one-time approval ticket when the render is refused.
        registerMock(EFormRenderApprovalService.class, mockRenderApprovalService);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("doc1");

        eFormUtilMock = mockStatic(EFormUtil.class);
        HashMap<String, Object> eformData = new HashMap<>();
        eformData.put("formName", "Rich Text Letter");
        eformData.put("formHtml", "");
        eformData.put("formSubject", "");
        eformData.put("formDate", "");
        eformData.put("formFileName", "RichTextLetter.html");
        eformData.put("formCreator", "doc1");
        eFormUtilMock.when(() -> EFormUtil.loadEForm(anyString())).thenReturn(eformData);
        eFormUtilMock.when(() -> EFormUtil.addEFormValues(any(), any(), anyInt(), anyInt(), anyInt()))
                .then(invocation -> null);

        eFormLoaderMock = mockStatic(EFormLoader.class);
        eFormLoaderMock.when(EFormLoader::getInstance).thenReturn(mock(EFormLoader.class));
        eFormLoaderMock.when(EFormLoader::getOpener).thenReturn("oscarOPEN=");

        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_eform"), eq("w"), isNull()))
                .thenReturn(true);
        when(mockEformDataManager.saveEformData(any(LoggedInInfo.class), any())).thenReturn(42);
        when(mockDemographicManager.getDemographicFormattedName(any(LoggedInInfo.class), anyInt()))
                .thenReturn("SMITH, JANE");

        mockRequest.setParameter("efmfid", "3");
        mockRequest.setParameter("efmdemographic_no", "123");
        // What printControl.js's "PDF" button posts, alongside the letter itself.
        mockRequest.setParameter("print", "true");
        mockRequest.setParameter("skipSave", "true");
        mockRequest.setParameter("Letter", "&lt;p&gt;Dear Dr. Smith&lt;/p&gt;");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (eFormLoaderMock != null) eFormLoaderMock.close();
        if (eFormUtilMock != null) eFormUtilMock.close();
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoMocks != null) mockitoMocks.close();
    }

    private void stubSuccessfulRender() throws PDFGenerationException {
        when(mockDocumentAttachmentManager.renderEFormPacketWithCompleteness(any(), any(), isNull()))
                .thenReturn(new EformDataManager.EformPdfRender(Path.of("letter.pdf"),
                        new EFormRenderCompletenessReport(0, 0, 0, 0, false, false, false, false, false)));
        when(mockDocumentAttachmentManager.convertPDFToBase64(any())).thenReturn("JVBERi0=");
    }

    @Test
    @DisplayName("should save and return the mapped download result when the legacy print flag is posted")
    void shouldReturnDownload_whenLegacyPrintFlagPosted() throws Exception {
        stubSuccessfulRender();

        String result = new AddEForm2Action().execute();

        assertThat(result).isEqualTo("download");
        assertThat(mockRequest.getAttribute("isDownload")).isEqualTo("true");
        assertThat(mockRequest.getAttribute("eFormPDF")).isEqualTo("JVBERi0=");
        assertThat((String) mockRequest.getAttribute("eFormPDFName")).endsWith("_SMITH.pdf");
        assertThat(mockRequest.getAttribute("fdid")).isEqualTo("42");
        // "PDF" (skipSave=true) is a preview: the window stays open, so no auto-close flag. The
        // "Submit & PDF" submission (skipSave=false) sets it; AddEForm2ActionTemplateWriteUnitTest
        // covers that path because it needs the template-write scaffolding.
        assertThat(mockRequest.getAttribute("isSuccess_Autoclose")).isNull();
        // The render works from the stored record, so the letter is persisted first (skipSave is
        // advisory only) — exactly once.
        verify(mockEformDataManager, times(1)).saveEformData(any(LoggedInInfo.class), any());
        verify(mockDocumentAttachmentManager, times(1))
                .renderEFormPacketWithCompleteness(eq(mockRequest), eq(mockResponse), isNull());
        // Same as every other download/fax/email path: no chart-note template write.
        eFormUtilMock.verify(
                () -> EFormUtil.writeEformTemplate(any(), any(), any(), any(), anyString(), anyString(), anyString()),
                never());
    }

    @Test
    @DisplayName("should surface the mapped error result when the print-alias render fails")
    void shouldReturnMappedError_whenPrintRenderFails() throws Exception {
        when(mockDocumentAttachmentManager.renderEFormPacketWithCompleteness(any(), any(), isNull()))
                .thenThrow(new PDFGenerationException("headless browser unavailable"));

        String result = new AddEForm2Action().execute();

        assertThat(result).isEqualTo("error");
        assertThat(mockRequest.getAttribute("error")).isEqualTo("true");
        assertThat(mockRequest.getAttribute("errorMessage"))
                .isEqualTo("This eForm (and attachments, if applicable) could not be downloaded.");
    }

    @Test
    @DisplayName("should offer the download approval when the print-alias render is incomplete")
    void shouldOfferDownloadApproval_whenPrintRenderIncomplete() throws Exception {
        EFormRenderCompletenessReport report =
                new EFormRenderCompletenessReport(1, 0, 0, 0, false, false, false, false, false);
        when(mockDocumentAttachmentManager.renderEFormPacketWithCompleteness(any(), any(), isNull()))
                .thenThrow(new EformContentUnavailableException("incomplete", 42, report));
        when(mockRenderApprovalService.issue(any(), any(), anyInt(), anyString(), any(), any(), any(), anyInt()))
                .thenReturn("ticket");

        String result = new AddEForm2Action().execute();

        assertThat(result).isEqualTo("missingContent");
        assertThat(mockRequest.getAttribute("approvalAction")).isEqualTo("eform/downloadEFormPdf");
        assertThat(mockRequest.getAttribute("renderApproval")).isEqualTo("ticket");
        // A preview keeps its window open, so the approval page carries no auto-close intent.
        assertThat(mockRequest.getAttribute("approvalAutoClose")).isNull();
    }

    @Test
    @DisplayName("should declare every result AddEForm2Action can return under eform/addEForm")
    void shouldDeclareEveryReturnedResult_forStrutsConfig() throws IOException {
        String action = Files.readString(ADD_EFORM_ACTION, StandardCharsets.UTF_8);
        String struts = Files.readString(STRUTS_EFORM_XML, StandardCharsets.UTF_8);

        // The regression this file exists for: an unmapped literal result. Struts cannot resolve it,
        // and the clinician sees "CARLOS Error" after the record was already saved.
        assertThat(action).doesNotContain("return \"print\"");

        Set<String> returned = new LinkedHashSet<>();
        Matcher literal = Pattern.compile("return \"([a-zA-Z]+)\";").matcher(action);
        while (literal.find()) {
            returned.add(literal.group(1));
        }
        assertThat(returned).as("sanity: the action returns named results").isNotEmpty();

        Matcher mapping = Pattern.compile(
                "<action name=\"eform/addEForm\"[\\s\\S]*?</action>").matcher(struts);
        assertThat(mapping.find()).as("eform/addEForm mapping present").isTrue();
        String addEFormMapping = mapping.group();
        for (String result : returned) {
            assertThat(addEFormMapping)
                    .as("result \"%s\" returned by AddEForm2Action must be declared for eform/addEForm", result)
                    .contains("<result name=\"" + result + "\"");
        }
    }
}
