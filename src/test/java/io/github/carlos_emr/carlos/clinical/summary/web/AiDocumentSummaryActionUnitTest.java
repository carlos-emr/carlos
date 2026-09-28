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
package io.github.carlos_emr.carlos.clinical.summary.web;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.clinical.summary.*;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.CtlDocumentPK;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.managers.DocumentManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiDocumentSummaryActionUnitTest extends CarlosUnitTestBase {
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final DocumentManager documents = mock(DocumentManager.class);
    private final DocumentSummaryService summarizer = mock(DocumentSummaryService.class);
    private final LoggedInInfo user = mock(LoggedInInfo.class);
    private final CarlosProperties properties = mock(CarlosProperties.class);
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> sessions;
    private MockedStatic<CarlosProperties> settings;
    private MockedStatic<EDocUtil> visibility;
    private MockedStatic<ClinicalSummaryTextExtractor> reader;
    private AiDocumentSummary2Action action;
    private Document document;
    private CtlDocument link;

    @BeforeEach
    void setUp() throws Exception {
        registerMock(SecurityInfoManager.class, security);
        registerMock(DocumentManager.class, documents);
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        sessions = mockStatic(LoggedInInfo.class);
        sessions.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(user);
        settings = mockStatic(CarlosProperties.class);
        settings.when(CarlosProperties::getInstance).thenReturn(properties);
        visibility = mockStatic(EDocUtil.class);
        reader = mockStatic(ClinicalSummaryTextExtractor.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getParameterValues("documentId")).thenReturn(new String[]{"42"});
        when(security.hasPrivilege(user, "_edoc", "r", null)).thenReturn(true);
        when(properties.getProperty(DocumentSummaryService.ENABLED_PROPERTY, "false")).thenReturn("true");
        when(properties.getProperty(ClinicalSummaryGenerationService.ENABLED_PROPERTY, "false")).thenReturn("true");
        link = new CtlDocument();
        link.setId(new CtlDocumentPK("demographic", 3001, 42));
        when(documents.getCtlDocumentByDocumentId(user, 42)).thenReturn(link);
        EDoc visible = mock(EDoc.class);
        when(visible.getDocId()).thenReturn("42");
        visibility.when(() -> EDocUtil.listDocs(user, "demographic", "3001", "all", EDocUtil.PRIVATE,
                EDocUtil.EDocSort.OBSERVATIONDATE, "active")).thenReturn(new ArrayList<>(List.of(visible)));
        document = new Document();
        document.setDocumentNo(42);
        document.setDocdesc("Referral");
        document.setDocfilename("referral.txt");
        document.setContenttype("text/plain");
        document.setStatus('A');
        document.setUpdatedatetime(new Date(1000));
        when(documents.getDocument(user, 42)).thenReturn(document);
        reader.when(() -> ClinicalSummaryTextExtractor.document("referral.txt", "text/plain"))
                .thenReturn(new ClinicalSummaryTextExtractor.Extract("Blood work planned.", true, "Complete text."));
        when(summarizer.summarize(anyString())).thenReturn(mock(DocumentSummary.class));
        action = new AiDocumentSummary2Action(summarizer);
    }

    @AfterEach
    void tearDown() {
        reader.close();
        visibility.close();
        settings.close();
        sessions.close();
        servlet.close();
    }

    @Test
    void shouldRenderDraft_whenDocumentStillAuthorizedAndUnchanged() throws Exception {
        assertThat(action.generate()).isEqualTo(ActionSupport.SUCCESS);
        verify(summarizer).summarize("Blood work planned.");
        verify(documents, times(2)).getDocument(user, 42);
        verify(request).setAttribute("documentSummaryGenerated", true);
        verify(response).setHeader("Cache-Control", "no-store");
        verify(response).setHeader("Referrer-Policy", "no-referrer");
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    void shouldRenderPreview_withoutInference(String method) throws Exception {
        when(request.getMethod()).thenReturn(method);
        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        verifyNoInteractions(summarizer);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "PUT", "DELETE", "OPTIONS"})
    void shouldRejectGeneration_withoutPost(String method) throws Exception {
        when(request.getMethod()).thenReturn(method);
        assertThat(action.generate()).isEqualTo(ActionSupport.NONE);
        verify(response).sendError(405);
        verifyNoInteractions(documents, summarizer);
        reader.verifyNoInteractions();
    }

    @ParameterizedTest
    @ValueSource(strings = {DocumentSummaryService.ENABLED_PROPERTY, ClinicalSummaryGenerationService.ENABLED_PROPERTY})
    void shouldHideFeature_whenEitherFlagDisabled(String flag) throws Exception {
        when(properties.getProperty(flag, "false")).thenReturn("false");
        assertThat(action.generate()).isEqualTo(ActionSupport.NONE);
        verify(response).sendError(404);
        verifyNoInteractions(documents, summarizer);
    }

    @Test
    void shouldDenyAccess_withoutPrivilegeOrLogin() {
        when(security.hasPrivilege(user, "_edoc", "r", null)).thenReturn(false);
        assertThatThrownBy(action::generate).isInstanceOf(SecurityException.class);
        sessions.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(null);
        assertThatThrownBy(action::generate).isInstanceOf(SecurityException.class);
        verifyNoInteractions(documents, summarizer);
        reader.verifyNoInteractions();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0", "-1", "1.0", " 42", "2147483648", "../42"})
    void shouldRejectId_whenMalformed(String id) throws Exception {
        when(request.getParameterValues("documentId")).thenReturn(new String[]{id});
        assertThat(action.generate()).isEqualTo(ActionSupport.NONE);
        verify(response).sendError(400);
        verifyNoInteractions(documents, summarizer);
    }

    @Test
    void shouldRejectId_whenMissingOrRepeated() throws Exception {
        when(request.getParameterValues("documentId")).thenReturn(null, new String[]{"42", "43"});
        assertThat(action.generate()).isEqualTo(ActionSupport.NONE);
        assertThat(action.generate()).isEqualTo(ActionSupport.NONE);
        verify(response, times(2)).sendError(400);
        verifyNoInteractions(documents, summarizer);
    }

    @Test
    void shouldDenyHiddenDocument_beforeReadingFile() {
        visibility.when(() -> EDocUtil.listDocs(user, "demographic", "3001", "all", EDocUtil.PRIVATE,
                EDocUtil.EDocSort.OBSERVATIONDATE, "active")).thenReturn(new ArrayList<>());
        assertThatThrownBy(action::generate).isInstanceOf(SecurityException.class);
        verifyNoInteractions(summarizer);
        reader.verifyNoInteractions();
    }

    @Test
    void shouldDenyNonPatientDocument_beforeReadingFile() {
        link.getId().setModule("provider");
        assertThatThrownBy(action::generate).isInstanceOf(SecurityException.class);
        verifyNoInteractions(summarizer);
        reader.verifyNoInteractions();
    }

    @Test
    void shouldDenyDeletedDocument_beforeReadingFile() {
        document.setStatus('D');
        assertThatThrownBy(action::generate).isInstanceOf(SecurityException.class);
        verifyNoInteractions(summarizer);
        reader.verifyNoInteractions();
    }

    @Test
    void shouldDisableGeneration_whenTextUnavailable() throws Exception {
        reader.when(() -> ClinicalSummaryTextExtractor.document("referral.txt", "text/plain"))
                .thenThrow(new IOException("private filename"));
        assertThat(action.generate()).isEqualTo(ActionSupport.SUCCESS);
        verify(request).setAttribute("documentSummaryAllowed", false);
        verifyNoInteractions(summarizer);
    }

    @Test
    void shouldDiscardDraft_whenFileTextChanges() throws Exception {
        reader.when(() -> ClinicalSummaryTextExtractor.document("referral.txt", "text/plain"))
                .thenReturn(new ClinicalSummaryTextExtractor.Extract("Blood work planned.", true, "Complete text."),
                        new ClinicalSummaryTextExtractor.Extract("Blood work cancelled.", true, "Complete text."));
        assertThat(action.generate()).isEqualTo(ActionSupport.SUCCESS);
        verify(request).setAttribute(eq("documentSummaryError"), contains("changed during generation"));
        verify(request, never()).setAttribute(eq("documentSummaryGenerated"), any());
    }

    @Test
    void shouldDiscardDraft_whenMutableMetadataChanges() throws Exception {
        when(summarizer.summarize(anyString())).thenAnswer(call -> {
            document.getUpdatedatetime().setTime(2000);
            return mock(DocumentSummary.class);
        });
        assertThat(action.generate()).isEqualTo(ActionSupport.SUCCESS);
        verify(request).setAttribute(eq("documentSummaryError"), contains("changed during generation"));
        verify(request, never()).setAttribute(eq("documentSummaryGenerated"), any());
    }

    @Test
    void shouldDenyDraft_whenPrivilegeRevokedDuringGeneration() {
        when(security.hasPrivilege(user, "_edoc", "r", null)).thenReturn(true, false);
        assertThatThrownBy(action::generate).isInstanceOf(SecurityException.class);
        verify(request, never()).setAttribute(eq("documentSummaryGenerated"), any());
    }

    @Test
    void shouldDenyDraft_whenVisibilityRevokedDuringGeneration() {
        // Initial visibility is allowed; after inference the document disappears from the list.
        EDoc visible = mock(EDoc.class);
        when(visible.getDocId()).thenReturn("42");
        visibility.when(() -> EDocUtil.listDocs(user, "demographic", "3001", "all", EDocUtil.PRIVATE,
                EDocUtil.EDocSort.OBSERVATIONDATE, "active")).thenReturn(new ArrayList<>(List.of(visible)), new ArrayList<>());
        assertThatThrownBy(action::generate).isInstanceOf(SecurityException.class);
        verify(request, never()).setAttribute(eq("documentSummaryGenerated"), any());
    }
}
