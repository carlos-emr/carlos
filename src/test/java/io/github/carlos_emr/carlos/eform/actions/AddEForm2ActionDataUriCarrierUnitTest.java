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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import org.apache.struts2.ServletActionContext;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.eform.EFormDataUriCarrier;
import io.github.carlos_emr.carlos.eform.EFormLoader;
import io.github.carlos_emr.carlos.eform.EFormSubmissionGuard;
import io.github.carlos_emr.carlos.eform.EFormUtil;
import io.github.carlos_emr.carlos.eform.util.EFormRenderApprovalService;
import io.github.carlos_emr.carlos.eform.util.EFormRenderCompletenessReport;
import io.github.carlos_emr.carlos.encounter.data.EctProgramManager;
import io.github.carlos_emr.carlos.PMmodule.dao.VacancyClientMatchDao;
import io.github.carlos_emr.carlos.PMmodule.dao.VacancyDao;
import io.github.carlos_emr.carlos.PMmodule.dao.WaitlistDao;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.EformDataManager;
import io.github.carlos_emr.carlos.managers.EmailManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.EformContentUnavailableException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The eForm save puts carried signature data URIs back into their fields before anything is stored
 * (#3552).
 *
 * <p>The page moves a signature pad's data URI out of its author-named field into
 * {@link EFormDataUriCarrier#PARAMETER} and leaves {@code carlos-data-uri:<nonce>:<n>} in the field, so the
 * WAF can exclude one known argument. The stored eForm values and the template's chart writes must
 * see the signature itself, never the reference, and never the carrier argument as a field. A
 * carried value that is not an image refuses the save before storage starts, which keeps the
 * submission token retryable.</p>
 *
 * <p>Each test ends on the save-as-eDoc path with a refused render ({@code missingContent}): it
 * returns right after storage and the template write, without the patient-matching step that needs
 * real program data.</p>
 *
 * @since 2026-10-22
 */
@DisplayName("AddEForm2Action carried signature data URIs")
@Tag("unit")
@Tag("eform")
@Tag("security")
class AddEForm2ActionDataUriCarrierUnitTest extends CarlosUnitTestBase {

    private static final String PNG = "data:image/png;base64,"
            + "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl5n2QAAAAASUVORK5CYII=";
    private static final String JSIGNATURE = "data:image/jsignature;base30,3E13Z5Y5_1O24Z66";
    private static final String NONCE = "00112233445566778899aabbccddeeff";

    private static String ref(int index) {
        return EFormDataUriCarrier.REFERENCE_PREFIX + NONCE + ":" + index;
    }

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockedStatic<EFormUtil> eFormUtilMock;
    private MockedStatic<EFormLoader> eFormLoaderMock;
    private MockedStatic<WebApplicationContextUtils> webApplicationContextUtilsMock;
    private AutoCloseable mockitoMocks;

    @Mock private SecurityInfoManager mockSecurityInfoManager;
    @Mock private EformDataManager mockEformDataManager;
    @Mock private DocumentAttachmentManager mockDocumentAttachmentManager;
    @Mock private EmailManager mockEmailManager;
    @Mock private WaitlistDao mockWaitlistDao;
    @Mock private VacancyDao mockVacancyDao;
    @Mock private VacancyClientMatchDao mockVacancyClientMatchDao;
    @Mock private EFormRenderApprovalService mockRenderApprovalService;
    @Mock private DemographicManager mockDemographicManager;
    @Mock private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;

    @BeforeEach
    void setUp() throws Exception {
        mockitoMocks = MockitoAnnotations.openMocks(this);

        mockRequest = new MockHttpServletRequest();
        mockRequest.setMethod("POST");
        mockResponse = new MockHttpServletResponse();

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerMock(EformDataManager.class, mockEformDataManager);
        registerMock(org.springframework.transaction.PlatformTransactionManager.class,
                spy(new io.github.carlos_emr.carlos.test.unit.RecordingTransactionManager()));
        registerMock(DocumentAttachmentManager.class, mockDocumentAttachmentManager);
        registerMock(EmailManager.class, mockEmailManager);
        registerMock(WaitlistDao.class, mockWaitlistDao);
        registerMock(VacancyDao.class, mockVacancyDao);
        registerMock(VacancyClientMatchDao.class, mockVacancyClientMatchDao);
        registerMock(DemographicManager.class, mockDemographicManager);
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
        eformData.put("formName", "TestForm");
        eformData.put("formHtml", "");
        eformData.put("formSubject", "");
        eformData.put("formDate", "");
        eformData.put("formFileName", "test.html");
        eformData.put("formCreator", "doc1");
        eFormUtilMock.when(() -> EFormUtil.loadEForm(anyString())).thenReturn(eformData);
        eFormUtilMock.when(() -> EFormUtil.addEFormValues(any(), any(), anyInt(), anyInt(), anyInt()))
                .then(invocation -> null);

        eFormLoaderMock = mockStatic(EFormLoader.class);
        eFormLoaderMock.when(EFormLoader::getInstance).thenReturn(mock(EFormLoader.class));
        eFormLoaderMock.when(EFormLoader::getOpener).thenReturn("oscarOPEN=");

        // EctProgram resolves its manager from the servlet context, not SpringUtils.
        EctProgramManager programManager = mock(EctProgramManager.class);
        when(programManager.getProgramBeans(anyString(), isNull())).thenReturn(List.of());
        WebApplicationContext applicationContext = mock(WebApplicationContext.class);
        when(applicationContext.getBean("ectProgramManager")).thenReturn(programManager);
        webApplicationContextUtilsMock = mockStatic(WebApplicationContextUtils.class);
        webApplicationContextUtilsMock
                .when(() -> WebApplicationContextUtils.getWebApplicationContext(any()))
                .thenReturn(applicationContext);

        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_eform"), eq("w"), isNull()))
                .thenReturn(true);
        when(mockEformDataManager.saveEformData(any(LoggedInInfo.class), any())).thenReturn(42);
        Demographic client = new Demographic();
        client.setDemographicNo(123);
        when(mockDemographicManager.getDemographic(any(LoggedInInfo.class), anyString())).thenReturn(client);
        // End on the refused eDoc render, right after storage and the template write.
        mockRequest.setParameter("saveAsEdoc", "true");
        doThrow(new EformContentUnavailableException("incomplete", 42,
                new EFormRenderCompletenessReport(1, 0, 0, 0, false, false, false, false, false)))
                .when(mockDocumentAttachmentManager).saveEFormAsEDoc(any(), any());

        mockRequest.setParameter("efmfid", "1");
        mockRequest.setParameter("efmdemographic_no", "123");
        mockRequest.setParameter(EFormSubmissionGuard.PARAMETER,
                EFormSubmissionGuard.issue(mockRequest.getSession(), "1", "123"));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (webApplicationContextUtilsMock != null) webApplicationContextUtilsMock.close();
        if (eFormLoaderMock != null) eFormLoaderMock.close();
        if (eFormUtilMock != null) eFormUtilMock.close();
        if (loggedInInfoMock != null) loggedInInfoMock.close();
        if (servletActionContextMock != null) servletActionContextMock.close();
        if (mockitoMocks != null) mockitoMocks.close();
    }

    @SuppressWarnings("unchecked")
    private List<List<String>> storedFields() {
        ArgumentCaptor<ArrayList<String>> names = ArgumentCaptor.forClass(ArrayList.class);
        ArgumentCaptor<ArrayList<String>> values = ArgumentCaptor.forClass(ArrayList.class);
        eFormUtilMock.verify(() -> EFormUtil.addEFormValues(names.capture(), values.capture(), eq(42), eq(1), eq(123)));
        return List.of(names.getValue(), values.getValue());
    }

    private String storedValue(String field) {
        List<List<String>> stored = storedFields();
        int index = stored.get(0).indexOf(field);
        assertThat(index).as("field %s is stored", field).isGreaterThanOrEqualTo(0);
        return stored.get(1).get(index);
    }

    @Test
    void shouldStoreSignatureItself_whenFieldCarriesReference() throws Exception {
        mockRequest.setParameter("SignatureBox", ref(0));
        mockRequest.setParameter("StoreSignature1", ref(1));
        mockRequest.setParameter("MirrorSignature", ref(0));
        mockRequest.setParameter("clinicalNote", "Routine follow up.");
        // Typed text that only reads like a reference is stored as typed.
        mockRequest.setParameter("typedNote", "carlos-data-uri:0");
        mockRequest.setParameter(EFormDataUriCarrier.PARAMETER, PNG, JSIGNATURE);
        mockRequest.setParameter(EFormDataUriCarrier.NONCE_PARAMETER, NONCE);

        assertThat(new AddEForm2Action().execute()).isEqualTo("missingContent");

        assertThat(storedValue("SignatureBox")).isEqualTo(PNG);
        assertThat(storedValue("StoreSignature1")).isEqualTo(JSIGNATURE);
        assertThat(storedValue("MirrorSignature")).isEqualTo(PNG);
        assertThat(storedValue("clinicalNote")).isEqualTo("Routine follow up.");
        assertThat(storedValue("typedNote")).isEqualTo("carlos-data-uri:0");
        assertThat(storedFields().get(0))
                .doesNotContain(EFormDataUriCarrier.PARAMETER, EFormDataUriCarrier.NONCE_PARAMETER);
        assertThat(storedFields().get(1)).noneMatch(value -> value.contains(NONCE));
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldHandTemplateWriteRestoredValues_forChartEntries() throws Exception {
        // The template's chart writes (notes, ticklers, consults) read the same lists.
        mockRequest.setParameter("SignatureBox", ref(0));
        mockRequest.setParameter(EFormDataUriCarrier.PARAMETER, PNG);
        mockRequest.setParameter(EFormDataUriCarrier.NONCE_PARAMETER, NONCE);

        new AddEForm2Action().execute();

        ArgumentCaptor<ArrayList<String>> values = ArgumentCaptor.forClass(ArrayList.class);
        eFormUtilMock.verify(() -> EFormUtil.writeEformTemplate(any(), any(), values.capture(), any(),
                anyString(), anyString(), anyString()));
        assertThat(values.getValue()).contains(PNG).doesNotContain(ref(0));
    }

    @Test
    void shouldStoreFieldUnchanged_whenRequestCarriesNothing() throws Exception {
        // An older page, or a form without a signature: nothing is rewritten.
        mockRequest.setParameter("SignatureBox", PNG);
        mockRequest.setParameter("clinicalNote", ref(0));

        assertThat(new AddEForm2Action().execute()).isEqualTo("missingContent");

        assertThat(storedValue("SignatureBox")).isEqualTo(PNG);
        assertThat(storedValue("clinicalNote")).isEqualTo(ref(0));
    }

    @Test
    void shouldRefuseSaveBeforeStorage_whenCarriedValueIsNotAnImage() throws Exception {
        mockRequest.setParameter("SignatureBox", ref(0));
        mockRequest.setParameter(EFormDataUriCarrier.PARAMETER,
                "data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==");
        mockRequest.setParameter(EFormDataUriCarrier.NONCE_PARAMETER, NONCE);

        assertThat(new AddEForm2Action().execute()).isEqualTo("error");

        assertThat(mockResponse.getStatus()).isEqualTo(400);
        assertThat(mockRequest.getAttribute("error")).isEqualTo("true");
        assertThat((String) mockRequest.getAttribute("errorMessage")).contains("signature");
        verify(mockEformDataManager, never()).saveEformData(any(), any());
        eFormUtilMock.verify(() -> EFormUtil.addEFormValues(any(), any(), anyInt(), anyInt(), anyInt()), never());
    }

    @Test
    void shouldAllowRetry_afterRefusedCarriedValue() throws Exception {
        // Nothing was stored, so the same rendering's token must still save once corrected.
        mockRequest.setParameter("SignatureBox", ref(3));
        mockRequest.setParameter(EFormDataUriCarrier.PARAMETER, PNG);
        mockRequest.setParameter(EFormDataUriCarrier.NONCE_PARAMETER, NONCE);
        assertThat(new AddEForm2Action().execute()).isEqualTo("error");

        mockRequest.setParameter("SignatureBox", ref(0));
        mockResponse = new MockHttpServletResponse();
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);
        assertThat(new AddEForm2Action().execute()).isEqualTo("missingContent");

        verify(mockEformDataManager, times(1)).saveEformData(any(), any());
        assertThat(storedValue("SignatureBox")).isEqualTo(PNG);
    }
}
