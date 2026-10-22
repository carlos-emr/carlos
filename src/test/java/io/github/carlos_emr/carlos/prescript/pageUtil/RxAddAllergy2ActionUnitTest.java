/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.prescript.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.AllergyDao;
import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.data.RxDrugData;
import io.github.carlos_emr.carlos.prescript.data.RxPatientData;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
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
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.mockito.ArgumentMatchers.anyString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RxAddAllergy2Action}, focused on the request guards that
 * must run before any allergy add/archive side effect fires.
 *
 * <p>Invariants pinned here:
 * <ul>
 *   <li>the "archive old allergy" branch that regressed to an IDOR (issue #2467):
 *       an amendment must name an allergy owned by the requested patient, and the
 *       audit log records an archive only when the conditional amendment actually
 *       archived it (stale amendments are refused with 409, issue #4410);</li>
 *   <li>malformed {@code type} values are rejected with HTTP 400 instead of
 *       escaping as an unhandled {@code NumberFormatException} / 500; and</li>
 *   <li>a retried save carrying the same {@code saveToken} writes at most once
 *       (issue #3488).</li>
 * </ul>
 *
 * @since 2026-07-06
 */
@DisplayName("RxAddAllergy2Action Unit Tests")
@Tag("unit")
@Tag("rx")
class RxAddAllergy2ActionUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mocks;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    @Mock
    private RxPatientData.Patient mockRxPatient;

    private MockHttpServletRequest mockRequest;
    private MockHttpServletResponse mockResponse;
    private RxAddAllergy2Action action;

    @BeforeEach
    void setUp() {
        // Bootstrap mock in case this is the first time RxPatientData.Patient is
        // initialized in this JVM/fork: its <clinit> resolves AllergyDao via
        // SpringUtils.getBean, and an unmocked failure here permanently poisons
        // the class (NoClassDefFoundError) for every other test in the same fork.
        registerMock(AllergyDao.class, mock(AllergyDao.class));

        mocks = MockitoAnnotations.openMocks(this);
        mockRequest = new MockHttpServletRequest();
        mockResponse = new MockHttpServletResponse();
        mockRequest.setMethod("POST");

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_allergy"), eq("w"), isNull()))
                .thenReturn(true);
        // Patient-level Rx access (the shared Rx write check, #3908) is granted unless a test denies it.
        when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), anyInt())).thenReturn(true);
        when(mockSecurityInfoManager.isAllowedAccessToPatientRecord(any(), any())).thenReturn(true);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("provider1");

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(mockRequest);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(mockResponse);

        mockRequest.setParameter("type", "1");
        mockRequest.setParameter("startDate", "");
        mockRequest.setParameter("formDemographicNo", "123");
        // The request names its patient; the write resolver refuses one that does not (#3875).
        mockRequest.setParameter("demographicNo", "123");
        openRxForPatient();

        action = new RxAddAllergy2Action();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    @DisplayName("should throw SecurityException when missing _allergy privilege")
    void shouldThrowSecurityException_whenPrivilegeMissing() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_allergy"), eq("w"), isNull()))
                .thenReturn(false);

        assertThatThrownBy(() -> action.execute())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_allergy");
        verify(mockRxPatient, never()).addAllergy(any(), any());
    }

    @Test
    @DisplayName("should reject a non-POST request before adding an allergy")
    void shouldRejectAdd_whenRequestMethodIsNotPost() throws Exception {
        mockRequest.setMethod("GET");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(405);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("POST");
        verify(mockRxPatient, never()).addAllergy(any(), any());
        verify(mockRxPatient, never()).deleteAllergy(anyInt());
        logActionMock.verifyNoInteractions();
    }

    @Test
    @DisplayName("should reject a lower-case post request before adding an allergy")
    void shouldRejectAdd_whenRequestMethodIsLowerCasePost() throws Exception {
        mockRequest.setMethod("post");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(405);
        assertThat(mockResponse.getHeader("Allow")).isEqualTo("POST");
        verify(mockRxPatient, never()).addAllergy(any(), any());
        verify(mockRxPatient, never()).deleteAllergy(anyInt());
        logActionMock.verifyNoInteractions();
    }

    @Test
    @DisplayName("should reject a missing rendered patient context before adding an allergy")
    void shouldRejectAdd_whenFormDemographicNoIsMissing() throws Exception {
        mockRequest.removeParameter("formDemographicNo");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(403);
        verify(mockRxPatient, never()).addAllergy(any(), any());
        verify(mockRxPatient, never()).deleteAllergy(anyInt());
        logActionMock.verifyNoInteractions();
    }

    @Test
    @DisplayName("should reject a missing session patient before adding an allergy")
    void shouldRejectAdd_whenSessionPatientIsMissing() throws Exception {
        // Rx/allergies was never opened for the form's patient in this session.
        mockRequest.getSession().removeAttribute(RxSessionBeanResolver.BEANS_ATTRIBUTE);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(403);
        verify(mockRxPatient, never()).addAllergy(any(), any());
        verify(mockRxPatient, never()).deleteAllergy(anyInt());
        logActionMock.verifyNoInteractions();
    }

    @ParameterizedTest(name = "{0}={1}")
    @CsvSource({"formDemographicNo,456", "formDemographicNo,not-a-number", "allergyToArchive,42"})
    @DisplayName("should reject mismatched patient context or a foreign allergy before adding a replacement")
    void shouldRejectAdd_whenPatientOrOriginalAllergyDoesNotMatch(String parameter, String value) throws Exception {
        mockRequest.setParameter(parameter, value);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(403);
        verify(mockRxPatient, never()).addAllergy(any(), any());
        verify(mockRxPatient, never()).deleteAllergy(anyInt());
        logActionMock.verifyNoInteractions();
    }

    @Test
    @DisplayName("should refuse when the request names one open patient and the form carries another")
    void shouldRejectAdd_whenRequestPatientDiffersFromFormPatient() throws Exception {
        // Both patients have Rx open: the old code wrote to whichever formDemographicNo named.
        RxSessionBean otherBean = new RxSessionBean();
        otherBean.setDemographicNo(456);
        RxSessionBeanResolver.register(mockRequest.getSession(), otherBean);
        mockRequest.setParameter("demographicNo", "123");
        mockRequest.setParameter("formDemographicNo", "456");
        mockRequest.setParameter("allergyToArchive", "42");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(403);
        assertThat(action.getDemographicNo()).isZero();
        verify(mockRxPatient, never()).addAllergy(any(), any());
        verify(mockRxPatient, never()).deleteAllergy(anyInt());
        logActionMock.verifyNoInteractions();
    }

    @Test
    @DisplayName("should refuse when the form names an open patient but the request names none")
    void shouldRejectAdd_whenRequestNamesNoPatient() throws Exception {
        mockRequest.removeParameter("demographicNo");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(403);
        verify(mockRxPatient, never()).addAllergy(any(), any());
        verify(mockRxPatient, never()).deleteAllergy(anyInt());
        logActionMock.verifyNoInteractions();
    }

    @Test
    @DisplayName("should reject a missing type before adding an allergy")
    void shouldRejectAdd_whenTypeParameterMissing() throws Exception {
        mockRequest.removeParameter("type");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(400);
        assertThat(mockResponse.getErrorMessage()).isEqualTo("Missing or empty type parameter");
        verify(mockRxPatient, never()).addAllergy(any(), any());
        verify(mockRxPatient, never()).deleteAllergy(anyInt());
        logActionMock.verifyNoInteractions();
    }

    @Test
    @DisplayName("should reject a blank type before adding an allergy")
    void shouldRejectAdd_whenTypeParameterIsBlank() throws Exception {
        mockRequest.setParameter("type", "   ");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(400);
        assertThat(mockResponse.getErrorMessage()).isEqualTo("Missing or empty type parameter");
        verify(mockRxPatient, never()).addAllergy(any(), any());
        verify(mockRxPatient, never()).deleteAllergy(anyInt());
        logActionMock.verifyNoInteractions();
    }

    @Test
    @DisplayName("should reject a non-numeric type before adding an allergy")
    void shouldRejectAdd_whenTypeParameterIsNonNumeric() throws Exception {
        mockRequest.setParameter("type", "abc");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(400);
        assertThat(mockResponse.getErrorMessage()).isEqualTo("Invalid type parameter");
        verify(mockRxPatient, never()).addAllergy(any(), any());
        verify(mockRxPatient, never()).deleteAllergy(anyInt());
        logActionMock.verifyNoInteractions();
    }

    @Test
    @DisplayName("should not burn the saveToken when the type is malformed")
    void shouldKeepSaveTokenUsable_whenTypeIsMalformed() throws Exception {
        mockRequest.setParameter("saveToken", "11111111-2222-3333-4444-555555555555");
        mockRequest.setParameter("type", "abc");
        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(400);

        mockRequest.setParameter("type", "1");
        assertThat(new RxAddAllergy2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
        verify(mockRxPatient, times(1)).addAllergy(any(), any());
    }

    @Test
    @DisplayName("should add allergy when start date is absent")
    void shouldAddAllergy_whenStartDateIsAbsent() throws Exception {
        ArgumentCaptor<Allergy> allergyCaptor = ArgumentCaptor.forClass(Allergy.class);
        mockRequest.setParameter("type", "0");
        mockRequest.removeParameter("startDate");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        verify(mockRxPatient).addAllergy(any(), allergyCaptor.capture());
        assertThat(allergyCaptor.getValue().getTypeCode()).isZero();
        assertThat(allergyCaptor.getValue().getStartDate()).isNull();
        logActionMock.verify(() -> LogAction.addLog(
                eq("provider1"), eq(LogConst.ADD), eq(LogConst.CON_ALLERGY),
                any(String.class), any(String.class), eq("123"), any(String.class)));
        verify(mockRxPatient, never()).deleteAllergy(anyInt());
    }

    @Test
    @DisplayName("should add allergy and log ADD when no prior allergy is archived")
    void shouldAddAllergyAndLogAdd_whenNoAllergyToArchive() throws Exception {
        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        // The success redirect returns to this patient's allergy page.
        assertThat(action.getDemographicNo()).isEqualTo(123);
        verify(mockRxPatient).addAllergy(any(), any());
        logActionMock.verify(() -> LogAction.addLog(
                eq("provider1"), eq(LogConst.ADD), eq(LogConst.CON_ALLERGY),
                any(String.class), any(String.class), eq("123"), any(String.class)));
        verify(mockRxPatient, never()).deleteAllergy(anyInt());
    }

    @ParameterizedTest(name = "type {0}")
    @ValueSource(strings = {"8", "10", "11", "12", "14"})
    @DisplayName("should not look up DrugRef identifiers for non-brand allergens")
    void shouldSkipIdentifierLookup_forNonBrandAllergenTypes(String type) throws Exception {
        mockRequest.setParameter("type", type);
        mockRequest.setParameter("ID", "39007");
        mockRequest.setParameter("name", "PENICILLINS");

        try (MockedConstruction<RxDrugData> drugData = mockConstruction(RxDrugData.class)) {
            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            assertThat(drugData.constructed()).isEmpty();
        }
        assertThat(action.isIdentifiersUnresolved()).isFalse();
        ArgumentCaptor<Allergy> saved = ArgumentCaptor.forClass(Allergy.class);
        verify(mockRxPatient).addAllergy(any(), saved.capture());
        assertThat(saved.getValue().getDrugrefId()).isEqualTo("39007");
        assertThat(saved.getValue().getRegionalIdentifier()).isNullOrEmpty();
    }

    @Test
    @DisplayName("should store the ATC code and DIN when a brand allergen resolves")
    void shouldStoreIdentifiers_whenBrandAllergenResolves() throws Exception {
        mockRequest.setParameter("type", "13");
        mockRequest.setParameter("ID", "100");
        mockRequest.setParameter("name", "AMOXIL");
        RxDrugData.DrugMonograph monograph = mock(RxDrugData.DrugMonograph.class);
        monograph.regionalIdentifier = "00012345";
        when(monograph.getAtc()).thenReturn("J01CA04");

        try (MockedConstruction<RxDrugData> ignored = mockConstruction(RxDrugData.class,
                (m, c) -> when(m.getDrug("100")).thenReturn(monograph))) {
            action.execute();
        }

        ArgumentCaptor<Allergy> saved = ArgumentCaptor.forClass(Allergy.class);
        verify(mockRxPatient).addAllergy(any(), saved.capture());
        assertThat(saved.getValue().getAtc()).isEqualTo("J01CA04");
        assertThat(saved.getValue().getRegionalIdentifier()).isEqualTo("00012345");
        assertThat(action.isIdentifiersUnresolved()).isFalse();
    }

    @Test
    @DisplayName("should still save the allergy but flag it when a brand allergen cannot be resolved")
    void shouldSaveAndFlagAllergy_whenBrandLookupFails() throws Exception {
        mockRequest.setParameter("type", "13");
        mockRequest.setParameter("ID", "39007");
        mockRequest.setParameter("name", "AMOXIL");

        try (MockedConstruction<RxDrugData> ignored = mockConstruction(RxDrugData.class,
                (m, c) -> when(m.getDrug("39007")).thenThrow(new java.util.NoSuchElementException("none")))) {
            String result = action.execute();
            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        }

        ArgumentCaptor<Allergy> saved = ArgumentCaptor.forClass(Allergy.class);
        verify(mockRxPatient).addAllergy(any(), saved.capture());
        assertThat(saved.getValue().getAtc()).isNullOrEmpty();
        // The search id is kept as the DrugRef id but is not presented as a DIN.
        assertThat(saved.getValue().getDrugrefId()).isEqualTo("39007");
        assertThat(saved.getValue().getRegionalIdentifier()).isNullOrEmpty();
        assertThat(action.isIdentifiersUnresolved()).isTrue();
    }

    @ParameterizedTest(name = "ID={0}")
    @ValueSource(strings = {"", "0", "null"})
    @DisplayName("should flag a brand allergen submitted without a usable DrugRef id")
    void shouldFlagAllergy_whenBrandAllergenHasNoLookupId(String id) throws Exception {
        mockRequest.setParameter("type", "13");
        mockRequest.setParameter("ID", id);
        mockRequest.setParameter("name", "AMOXIL");

        try (MockedConstruction<RxDrugData> drugData = mockConstruction(RxDrugData.class)) {
            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            assertThat(drugData.constructed()).isEmpty();
        }
        verify(mockRxPatient).addAllergy(any(), any());
        assertThat(action.isIdentifiersUnresolved()).isTrue();
    }

    @Test
    @DisplayName("should amend the active allergy and log ADD and ARCHIVE when it belongs to the session patient")
    void shouldLogArchive_whenAllergyBelongsToSessionPatient() throws Exception {
        mockRequest.setParameter("allergyToArchive", "42");
        when(mockRxPatient.getAllergy(42)).thenReturn(new Allergy());
        when(mockRxPatient.amendActiveAllergy(any(), any(), eq(42))).thenReturn(true);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        verify(mockRxPatient).amendActiveAllergy(any(), any(), eq(42));
        // The replacement is added only through the conditional amendment, never on its own.
        verify(mockRxPatient, never()).addAllergy(any(), any());
        logActionMock.verify(() -> LogAction.addLog(
                eq("provider1"), eq(LogConst.ADD), eq(LogConst.CON_ALLERGY),
                any(String.class), any(String.class), eq("123"), any(String.class)));
        logActionMock.verify(() -> LogAction.addLog(
                eq("provider1"), eq(LogConst.ARCHIVE), eq(LogConst.CON_ALLERGY),
                eq("42"), any(String.class), eq("123"), isNull()));
    }

    @Test
    @DisplayName("should refuse with 409 and write nothing when the allergy being amended is already archived (issue #4410)")
    void shouldRefuseAmendment_whenOriginalAllergyIsAlreadyArchived() throws Exception {
        mockRequest.setParameter("allergyToArchive", "42");
        Allergy archived = new Allergy();
        archived.setArchived(true);
        when(mockRxPatient.getAllergy(42)).thenReturn(archived);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(409);
        verify(mockRxPatient, never()).addAllergy(any(), any());
        verify(mockRxPatient, never()).amendActiveAllergy(any(), any(), anyInt());
        verify(mockRxPatient, never()).deleteAllergy(anyInt());
        logActionMock.verifyNoInteractions();
    }

    @Test
    @DisplayName("should refuse with 409 and audit nothing when another session archives the original first (issue #4410)")
    void shouldRefuseAmendment_whenOriginalIsArchivedConcurrently() throws Exception {
        mockRequest.setParameter("allergyToArchive", "42");
        when(mockRxPatient.getAllergy(42)).thenReturn(new Allergy());
        when(mockRxPatient.amendActiveAllergy(any(), any(), eq(42))).thenReturn(false);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(409);
        verify(mockRxPatient, never()).addAllergy(any(), any());
        logActionMock.verifyNoInteractions();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-1", "2147483648"})
    @DisplayName("should reject a malformed allergy edit before adding its replacement")
    void shouldRejectEdit_whenAllergyToArchiveIsMalformed(String archiveId) throws Exception {
        mockRequest.setParameter("allergyToArchive", archiveId);

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(400);
        verify(mockRxPatient, never()).addAllergy(any(), any());
        verify(mockRxPatient, never()).deleteAllergy(anyInt());
        logActionMock.verifyNoInteractions();
    }

    @Test
    @DisplayName("should save once when the same saveToken is retried after a successful save")
    void shouldSaveOnce_whenSaveTokenRetriedAfterSuccess() throws Exception {
        mockRequest.setParameter("saveToken", "11111111-2222-3333-4444-555555555555");

        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        // The first response was lost, so the clinician presses Add Allergy again.
        assertThat(new RxAddAllergy2Action().execute()).isEqualTo(ActionSupport.SUCCESS);

        verify(mockRxPatient, times(1)).addAllergy(any(), any());
        assertThat(mockResponse.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("should save on retry when the first attempt with the saveToken failed")
    void shouldSaveOnRetry_whenFirstAttemptFailed() throws Exception {
        mockRequest.setParameter("saveToken", "11111111-2222-3333-4444-555555555555");
        doThrow(new IllegalStateException("db down")).doNothing().when(mockRxPatient).addAllergy(any(), any());

        assertThatThrownBy(() -> action.execute()).isInstanceOf(IllegalStateException.class);
        assertThat(new RxAddAllergy2Action().execute()).isEqualTo(ActionSupport.SUCCESS);

        verify(mockRxPatient, times(2)).addAllergy(any(), any());
    }

    @Test
    @DisplayName("should not burn the saveToken when validation rejects the request")
    void shouldKeepSaveTokenUsable_whenValidationRejects() throws Exception {
        mockRequest.setParameter("saveToken", "11111111-2222-3333-4444-555555555555");
        mockRequest.setParameter("allergyToArchive", "42");
        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(403);

        mockRequest.removeParameter("allergyToArchive");
        assertThat(new RxAddAllergy2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
        verify(mockRxPatient, times(1)).addAllergy(any(), any());
    }

    @Test
    @DisplayName("should answer a retried amendment that did persist as saved instead of 409")
    void shouldSucceedOnce_whenPersistedAmendmentIsRetried() throws Exception {
        mockRequest.setParameter("saveToken", "11111111-2222-3333-4444-555555555555");
        mockRequest.setParameter("allergyToArchive", "42");
        Allergy original = new Allergy();
        when(mockRxPatient.getAllergy(42)).thenReturn(original);
        when(mockRxPatient.amendActiveAllergy(any(), any(), eq(42))).thenReturn(true);
        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);

        // The response was lost; the original is archived now, and the retry must not read as stale.
        original.setArchived(true);
        assertThat(new RxAddAllergy2Action().execute()).isEqualTo(ActionSupport.SUCCESS);

        assertThat(mockResponse.getStatus()).isEqualTo(200);
        verify(mockRxPatient, times(1)).amendActiveAllergy(any(), any(), eq(42));
        verify(mockRxPatient, never()).addAllergy(any(), any());
    }

    @Test
    @DisplayName("should keep a stale amendment refused on retry without burning the saveToken")
    void shouldRefuseStaleAmendment_whenRetriedWithSameToken() throws Exception {
        mockRequest.setParameter("saveToken", "11111111-2222-3333-4444-555555555555");
        mockRequest.setParameter("allergyToArchive", "42");
        when(mockRxPatient.getAllergy(42)).thenReturn(new Allergy());
        when(mockRxPatient.amendActiveAllergy(any(), any(), eq(42))).thenReturn(false);

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(409);
        // Not the save-token conflict: the dialogue must report it as the stale-amendment refusal.
        assertThat(mockResponse.getHeader("X-Allergy-Save-Token")).isNull();

        // The refusal released the token, so the retry reaches the conditional archive again
        // rather than being answered from the ledger.
        MockHttpServletResponse retryResponse = new MockHttpServletResponse();
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(retryResponse);
        assertThat(new RxAddAllergy2Action().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(retryResponse.getStatus()).isEqualTo(409);
        verify(mockRxPatient, times(2)).amendActiveAllergy(any(), any(), eq(42));
        verify(mockRxPatient, never()).addAllergy(any(), any());
    }

    @Test
    @DisplayName("should refuse a retry whose values changed after the token was used")
    void shouldRejectRetry_whenValuesChangedForUsedToken() throws Exception {
        mockRequest.setParameter("saveToken", "11111111-2222-3333-4444-555555555555");
        mockRequest.setParameter("reactionDescription", "rash");
        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);

        mockRequest.setParameter("reactionDescription", "anaphylaxis");
        assertThat(new RxAddAllergy2Action().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(mockResponse.getStatus()).isEqualTo(409);
        // Marked so the dialogue does not read it as the stale-amendment refusal (#4410).
        assertThat(mockResponse.getHeader("X-Allergy-Save-Token")).isEqualTo("conflict");
        verify(mockRxPatient, times(1)).addAllergy(any(), any());
    }

    @Test
    @DisplayName("should reject a malformed saveToken before adding an allergy")
    void shouldRejectAdd_whenSaveTokenIsMalformed() throws Exception {
        mockRequest.setParameter("saveToken", "<script>");

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(400);
        verify(mockRxPatient, never()).addAllergy(any(), any());
    }

    @Test
    @DisplayName("should still save when no saveToken is supplied")
    void shouldSave_whenNoSaveTokenSupplied() throws Exception {
        assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(new RxAddAllergy2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
        verify(mockRxPatient, times(2)).addAllergy(any(), any());
    }

    /**
     * Opens Rx for patient 123 in this session (per-patient Rx state, #3875) and seeds the
     * resolver's per-request patient cache with the mock, so no demographic lookup runs.
     */
    private void openRxForPatient() {
        RxSessionBean rxBean = new RxSessionBean();
        rxBean.setDemographicNo(123);
        RxSessionBeanResolver.register(mockRequest.getSession(), rxBean);
        when(mockRxPatient.getDemographicNo()).thenReturn(123);
        mockRequest.setAttribute(RxSessionBeanResolver.PATIENT_REQUEST_ATTRIBUTE, mockRxPatient);
    }

}
