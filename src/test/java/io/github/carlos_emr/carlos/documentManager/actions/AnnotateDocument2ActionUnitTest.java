// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.documentManager.actions;

import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.documentManager.annotation.AnnotatedDocumentService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.io.IOException;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/** Exercises the annotation viewer gate and its patient/document limits.
 * @since 2026-09-20
 */
class AnnotateDocument2ActionUnitTest extends CarlosUnitTestBase {
    private MockHttpServletRequest request;
    private LoggedInInfo info;
    private SecurityInfoManager security;

    @BeforeEach
    void setUpGate() {
        request = new MockHttpServletRequest();
        request.setParameter("docId", "42");
        info = mock(LoggedInInfo.class);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), info);
        security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(eq(info), eq("_edoc"), eq(SecurityInfoManager.WRITE), isNull()))
                .thenReturn(true);
    }

    @Test
    void shouldRefuseViewer_whenDocumentWritePrivilegeMissing() {
        when(security.hasPrivilege(eq(info), eq("_edoc"), eq(SecurityInfoManager.WRITE), isNull()))
                .thenReturn(false);
        try (MockedStatic<EDocUtil> documents = mockStatic(EDocUtil.class)) {
            assertThatThrownBy(this::execute).isInstanceOf(SecurityException.class)
                    .hasMessageContaining("_edoc");
            documents.verifyNoInteractions();
        }
    }

    @Test
    void shouldExplainMissingSelection_whenDocIdIsBlank() {
        request.removeParameter("docId");

        AnnotateDocument2Action action = execute();

        assertThat(action.getMessage()).contains("must be selected");
        assertThat(action.getDocId()).isZero();
    }

    @Test
    void shouldExplainMalformedId_whenDocIdCannotParse() {
        request.setParameter("docId", "abc");
        try (MockedStatic<EDocUtil> documents = mockStatic(EDocUtil.class)) {
            AnnotateDocument2Action action = execute();
            assertThat(action.getMessage()).contains("could not be opened");
            documents.verifyNoInteractions();
        }
    }

    @Test
    void shouldExplainUnknownDocument_whenLookupHasNoFilename() {
        try (MockedStatic<EDocUtil> documents = mockStatic(EDocUtil.class)) {
            documents.when(() -> EDocUtil.getDoc("42")).thenReturn(new EDoc());
            assertThat(execute().getMessage()).contains("could not be found");
        }
    }

    @Test
    void shouldRejectNonPdf_whenDocumentHasWrongContentType() {
        EDoc document = document("text/plain");
        try (MockedStatic<EDocUtil> documents = mockStatic(EDocUtil.class);
             MockedStatic<AnnotatedDocumentService> annotations = mockStatic(AnnotatedDocumentService.class)) {
            documents.when(() -> EDocUtil.getDoc("42")).thenReturn(document);
            assertThat(execute().getMessage()).contains("Only PDF documents");
            annotations.verifyNoInteractions();
        }
    }

    @Test
    void shouldRejectLinkedPatient_whenChartAccessMissing() {
        EDoc document = document("application/pdf");
        document.setModule("demographic");
        document.setModuleId("770001");
        try (MockedStatic<EDocUtil> documents = mockStatic(EDocUtil.class);
             MockedStatic<AnnotatedDocumentService> annotations = mockStatic(AnnotatedDocumentService.class)) {
            documents.when(() -> EDocUtil.getDoc("42")).thenReturn(document);
            assertThatThrownBy(this::execute).isInstanceOf(SecurityException.class)
                    .hasMessageContaining("patient record");
            annotations.verifyNoInteractions();
            verify(security).isAllowedAccessToPatientRecord(info, 770001);
        }
    }

    @Test
    void shouldCheckPatientAccessBeforeMissingFilename_whenLinkedPatientIsDenied() {
        EDoc document = new EDoc();
        document.setModule("demographic");
        document.setModuleId("770001");
        try (MockedStatic<EDocUtil> documents = mockStatic(EDocUtil.class)) {
            documents.when(() -> EDocUtil.getDoc("42")).thenReturn(document);

            assertThatThrownBy(this::execute).isInstanceOf(SecurityException.class)
                    .hasMessageContaining("patient record");

            verify(security).isAllowedAccessToPatientRecord(info, 770001);
        }
    }

    @Test
    void shouldCheckPatientAccessBeforeRevealingFileType_whenLinkedPatientIsDenied() {
        EDoc document = document("text/plain");
        document.setModule("demographic");
        document.setModuleId("770001");
        try (MockedStatic<EDocUtil> documents = mockStatic(EDocUtil.class)) {
            documents.when(() -> EDocUtil.getDoc("42")).thenReturn(document);

            assertThatThrownBy(this::execute).isInstanceOf(SecurityException.class)
                    .hasMessageContaining("patient record");

            verify(security).isAllowedAccessToPatientRecord(info, 770001);
        }
    }

    @Test
    void shouldExposePageCountAndDigest_whenUnlinkedPdfIsValid() {
        EDoc document = document("application/pdf");
        document.setModule("provider");
        document.setModuleId("770001");
        document.setDescription("Referral");
        try (MockedStatic<EDocUtil> documents = mockStatic(EDocUtil.class);
             MockedStatic<AnnotatedDocumentService> annotations = mockStatic(AnnotatedDocumentService.class)) {
            documents.when(() -> EDocUtil.getDoc("42")).thenReturn(document);
            annotations.when(() -> AnnotatedDocumentService.sourceDigest(document)).thenReturn("digest-fixture");
            annotations.when(() -> AnnotatedDocumentService.pageCountOf(document)).thenReturn(2);

            AnnotateDocument2Action action = execute();

            assertThat(action.getDocId()).isEqualTo(42);
            assertThat(action.getPageCount()).isEqualTo(2);
            assertThat(action.getSourceDigest()).isEqualTo("digest-fixture");
            assertThat(action.getDocumentTitle()).isEqualTo("Referral");
            assertThat(action.getDemographicNo()).isZero();
            assertThat(action.getMessage()).isNull();
            verify(security, never()).isAllowedAccessToPatientRecord(info, 770001);
        }
    }

    @Test
    void shouldExplainEmptyDocument_whenPageCountIsZero() {
        EDoc document = document("application/pdf");
        try (MockedStatic<EDocUtil> documents = mockStatic(EDocUtil.class);
             MockedStatic<AnnotatedDocumentService> annotations = mockStatic(AnnotatedDocumentService.class)) {
            documents.when(() -> EDocUtil.getDoc("42")).thenReturn(document);
            annotations.when(() -> AnnotatedDocumentService.sourceDigest(document)).thenReturn("digest-fixture");
            annotations.when(() -> AnnotatedDocumentService.pageCountOf(document)).thenReturn(0);
            assertThat(execute().getMessage()).contains("no pages");
        }
    }

    @Test
    void shouldExplainOversizedDocument_whenPageCeilingExceeded() {
        EDoc document = document("application/pdf");
        try (MockedStatic<EDocUtil> documents = mockStatic(EDocUtil.class);
             MockedStatic<AnnotatedDocumentService> annotations = mockStatic(AnnotatedDocumentService.class)) {
            documents.when(() -> EDocUtil.getDoc("42")).thenReturn(document);
            annotations.when(() -> AnnotatedDocumentService.sourceDigest(document)).thenReturn("digest-fixture");
            annotations.when(() -> AnnotatedDocumentService.pageCountOf(document))
                    .thenReturn(AnnotatedDocumentService.MAX_ANNOTATABLE_PAGES + 1);
            assertThat(execute().getMessage()).contains("Documents longer than");
        }
    }

    @Test
    void shouldExplainUnreadableDocument_whenDigestReadFails() {
        EDoc document = document("application/pdf");
        try (MockedStatic<EDocUtil> documents = mockStatic(EDocUtil.class);
             MockedStatic<AnnotatedDocumentService> annotations = mockStatic(AnnotatedDocumentService.class)) {
            documents.when(() -> EDocUtil.getDoc("42")).thenReturn(document);
            annotations.when(() -> AnnotatedDocumentService.sourceDigest(document))
                    .thenThrow(new IOException("fixture read failure"));
            assertThat(execute().getMessage()).contains("could not be opened for annotation");
        }
    }

    private AnnotateDocument2Action execute() {
        try (MockedStatic<ServletActionContext> context = mockStatic(ServletActionContext.class)) {
            context.when(ServletActionContext::getRequest).thenReturn(request);
            AnnotateDocument2Action action = new AnnotateDocument2Action(security);
            // The result is represented by the message (for noAnnotate) or populated viewer fields.
            String result = action.execute();
            assertThat(result).isEqualTo(action.getMessage() == null ? ActionSupport.SUCCESS : "noAnnotate");
            return action;
        }
    }

    private static EDoc document(String contentType) {
        EDoc document = new EDoc();
        document.setFileName("source.pdf");
        document.setContentType(contentType);
        return document;
    }
}
