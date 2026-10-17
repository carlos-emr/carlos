/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.tickler.pageUtil;

import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.documentManager.TicklerAttachmentService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * A document's Tickler link forwards here with the patient named by number. The provider's own
 * eDoc list sent the provider number as that patient, so the form opened for whichever patient
 * shared it (#4839). A document forward keeps its patient only when the document is theirs.
 */
@DisplayName("ForwardDemographicTickler2Action document-patient guard")
@Tag("unit")
@Tag("fast")
@Tag("tickler")
class ForwardDemographicTickler2ActionUnitTest {

    private static final int PATIENT = 1001;
    private static final int OTHER_PATIENT = 1002;
    private static final int PATIENT_DOCUMENT = 12;
    private static final int PROVIDER_DOCUMENT = 13;
    private static final int MISSING_DOCUMENT = 99999;

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockHttpServletRequest request;
    private SecurityInfoManager securityInfoManager;
    private TicklerAttachmentService ticklerAttachmentService;
    private LoggedInInfo loggedInInfo;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("GET", "/tickler/ForwardDemographicTickler");
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());

        loggedInInfo = mock(LoggedInInfo.class);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        securityInfoManager = mock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_tickler"), eq("u"), (String) isNull()))
                .thenReturn(true);
        ticklerAttachmentService = mock(TicklerAttachmentService.class);
        // Only the patient's own document belongs to them. Every other lookup answers false, as
        // the real one does for the provider's document, another patient's and a missing one.
        when(ticklerAttachmentService.belongsToPatient(loggedInInfo, DocumentType.DOC, PATIENT_DOCUMENT, null, PATIENT))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        loggedInInfoMock.close();
        servletActionContextMock.close();
    }

    private String forward(String docType, String docId, String demographicNo) {
        if (docType != null) {
            request.setParameter("docType", docType);
        }
        if (docId != null) {
            request.setParameter("docId", docId);
        }
        if (demographicNo != null) {
            request.setParameter("demographic_no", demographicNo);
        }
        return new ForwardDemographicTickler2Action(securityInfoManager, ticklerAttachmentService).execute();
    }

    private Object dropped() {
        return request.getAttribute(ForwardDemographicTickler2Action.PATIENT_DROPPED_FOR_DOCUMENT);
    }

    @Test
    @DisplayName("should keep the patient when the document is that patient's own")
    void shouldKeepPatient_whenDocumentIsThePatients() {
        String result = forward("DOC", String.valueOf(PATIENT_DOCUMENT), String.valueOf(PATIENT));

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(dropped()).isNull();
        verify(ticklerAttachmentService).belongsToPatient(loggedInInfo, DocumentType.DOC, PATIENT_DOCUMENT, null, PATIENT);
    }

    @Test
    @DisplayName("should drop the patient when a provider's document sends the provider number as the patient")
    void shouldDropPatient_whenProviderDocumentSendsProviderNumber() {
        String result = forward("DOC", String.valueOf(PROVIDER_DOCUMENT), "999998");

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(dropped()).isEqualTo(Boolean.TRUE);
        verify(ticklerAttachmentService).belongsToPatient(loggedInInfo, DocumentType.DOC, PROVIDER_DOCUMENT, null, 999998);
    }

    @Test
    @DisplayName("should drop the patient even when providerNo is sent too")
    void shouldDropPatient_whenProviderNoIsSentToo() {
        request.setParameter("providerNo", "999998");

        String result = forward("DOC", String.valueOf(PROVIDER_DOCUMENT), "999998");

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(dropped()).isEqualTo(Boolean.TRUE);
        // The legacy provider-tickler attributes are not set for a dropped patient.
        assertThat(request.getAttribute("demographic_no")).isNull();
        assertThat(request.getAttribute("demoName")).isNull();
    }

    @Test
    @DisplayName("should drop the patient when the document is another patient's")
    void shouldDropPatient_whenDocumentIsAnotherPatients() {
        String result = forward("DOC", String.valueOf(PATIENT_DOCUMENT), String.valueOf(OTHER_PATIENT));

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(dropped()).isEqualTo(Boolean.TRUE);
        verify(ticklerAttachmentService).belongsToPatient(loggedInInfo, DocumentType.DOC, PATIENT_DOCUMENT, null, OTHER_PATIENT);
    }

    @Test
    @DisplayName("should drop the patient when the document does not exist")
    void shouldDropPatient_whenDocumentIsMissing() {
        String result = forward("DOC", String.valueOf(MISSING_DOCUMENT), String.valueOf(PATIENT));

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(dropped()).isEqualTo(Boolean.TRUE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "abc", "12;13"})
    @DisplayName("should drop the patient when a document forward has no usable document id")
    void shouldDropPatient_whenDocumentIdIsUnusable(String docId) {
        String result = forward("DOC", docId, String.valueOf(PATIENT));

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(dropped()).isEqualTo(Boolean.TRUE);
        verifyNoInteractions(ticklerAttachmentService);
    }

    @Test
    @DisplayName("should drop the patient when a document forward has no document id at all")
    void shouldDropPatient_whenDocumentIdIsAbsent() {
        String result = forward("DOC", null, String.valueOf(PATIENT));

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(dropped()).isEqualTo(Boolean.TRUE);
        verifyNoInteractions(ticklerAttachmentService);
    }

    @Test
    @DisplayName("should drop the patient when a document forward names a non-numeric patient")
    void shouldDropPatient_whenPatientIsNotANumber() {
        String result = forward("DOC", String.valueOf(PATIENT_DOCUMENT), "x1001");

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(dropped()).isEqualTo(Boolean.TRUE);
        verifyNoInteractions(ticklerAttachmentService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"HRM", "HL7", "MDS"})
    @DisplayName("should leave the patient untouched for HRM and lab forwards")
    void shouldKeepPatient_forNonDocumentTypes(String docType) {
        String result = forward(docType, "5", String.valueOf(PATIENT));

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(dropped()).isNull();
        verifyNoInteractions(ticklerAttachmentService);
    }

    @Test
    @DisplayName("should leave the patient untouched for a plain forward without a document")
    void shouldKeepPatient_forPlainForward() {
        String result = forward(null, null, String.valueOf(PATIENT));

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(dropped()).isNull();
        verifyNoInteractions(ticklerAttachmentService);
    }

    @Test
    @DisplayName("should not look the document up when no patient is sent")
    void shouldSkipLookup_whenNoPatientIsSent() {
        String result = forward("DOC", String.valueOf(PATIENT_DOCUMENT), "");

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        assertThat(dropped()).isNull();
        verifyNoInteractions(ticklerAttachmentService);
    }

    @Test
    @DisplayName("should refuse before any document lookup without tickler update rights")
    void shouldRefuse_withoutTicklerRights() {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), anyString(), anyString(), (String) isNull()))
                .thenReturn(false);

        assertThatThrownBy(() -> forward("DOC", String.valueOf(PATIENT_DOCUMENT), String.valueOf(PATIENT)))
                .hasMessage("missing required sec object (_tickler)");
        verifyNoInteractions(ticklerAttachmentService);
    }
}
