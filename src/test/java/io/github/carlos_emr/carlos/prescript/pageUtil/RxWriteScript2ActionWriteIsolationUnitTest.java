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
package io.github.carlos_emr.carlos.prescript.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.PrescriptionSignatureStampService;
import io.github.carlos_emr.carlos.managers.RxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Per-patient write isolation for {@link RxWriteScript2Action}'s legacy default path and
 * {@code updateLongTermStatus} (#3875): an {@code update*} action or a long-term toggle that names
 * no patient must not change the session's last-opened Rx patient, while a request that names
 * its patient, and the non-update re-render, keep working.
 *
 * @since 2026-09-24
 */
@DisplayName("RxWriteScript2Action write isolation")
@Tag("unit")
@Tag("prescript")
@Tag("security")
class RxWriteScript2ActionWriteIsolationUnitTest extends CarlosUnitTestBase {

    private static final int DEMOGRAPHIC_NO = 1001;

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mocks;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;
    @Mock
    private UserPropertyDAO mockUserPropertyDAO;
    @Mock
    private PartialDateDao mockPartialDateDao;
    @Mock
    private DemographicManager mockDemographicManager;
    @Mock
    private RxManager mockRxManager;
    @Mock
    private PrescriptionSignatureStampService mockSignatureStampService;
    @Mock
    private LoggedInInfo mockLoggedInInfo;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private RxSessionBean bean;
    private RxPrescriptionData.Prescription stagedCard;
    private RxWriteScript2Action action;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerMock(UserPropertyDAO.class, mockUserPropertyDAO);
        registerMock(PartialDateDao.class, mockPartialDateDao);
        registerMock(DemographicManager.class, mockDemographicManager);
        registerMock(RxManager.class, mockRxManager);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_rx"), eq("w"), isNull()))
                .thenReturn(true);
        // Patient-level Rx access (the shared Rx write check, #3908) is granted unless a test denies it.
        when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), anyInt())).thenReturn(true);
        when(mockSecurityInfoManager.isAllowedAccessToPatientRecord(any(), any())).thenReturn(true);

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setMethod("POST");

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);

        // The patient is open in Rx and is the session's active (fallback) patient.
        bean = new RxSessionBean();
        bean.setDemographicNo(DEMOGRAPHIC_NO);
        bean.setProviderNo("999998");
        stagedCard = mock(RxPrescriptionData.Prescription.class);
        bean.getStashList().add(stagedCard);
        bean.setStashIndex(0);
        RxSessionBeanResolver.register(request.getSession(), bean);

        action = new RxWriteScript2Action(mockSignatureStampService);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"9999", "malformed"})
    @DisplayName("saveDrug safely refuses a request whose patient bean cannot be resolved")
    void shouldSkipSave_whenPatientBeanCannotBeResolved(String patient) {
        request.setParameter("demographicNo", patient);

        action.saveDrug(request);

        assertThat(bean.getStash()).containsExactly(stagedCard);
        verifyNoInteractions(stagedCard, mockRxManager, mockSignatureStampService);
        logActionMock.verifyNoInteractions();
    }

    @Test
    @DisplayName("saving cards whose keys share a prefix preserves each medication name")
    void shouldMatchExactCardKey_whenDrugNameKeysSharePrefix() throws Exception {
        bean.clearStash();
        RxPrescriptionData.Prescription first = draft(1, "first");
        RxPrescriptionData.Prescription second = draft(12, "second");
        bean.getStashList().add(first);
        bean.getStashList().add(second);
        request.setParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));
        request.setParameter("drugName_1", "First medication");
        request.setParameter("drugName_12", "Second medication");
        action = spy(action);
        doReturn("9001").when(action).persistStash(mockLoggedInInfo, bean);

        assertThat(action.updateSaveAllDrugs()).isEqualTo("refresh");

        assertThat(first.getBrandName()).isEqualTo("First medication");
        assertThat(second.getBrandName()).isEqualTo("Second medication");
        verify(action).persistStash(mockLoggedInInfo, bean);
    }

    @Test
    @DisplayName("concurrent close cannot shift a saved card's index onto another medication")
    void shouldSaveSubmittedCards_whenAnotherWindowClosesPrecedingCard() throws Exception {
        try (ConcurrentRxStashClose concurrentBean = new ConcurrentRxStashClose(111111)) {
            concurrentBean.setDemographicNo(DEMOGRAPHIC_NO);
            concurrentBean.setProviderNo("999998");
            RxPrescriptionData.Prescription first = draft(111111, "closed");
            RxPrescriptionData.Prescription second = draft(222222, "second");
            RxPrescriptionData.Prescription third = draft(333333, "third");
            concurrentBean.getStashList().add(first);
            concurrentBean.getStashList().add(second);
            concurrentBean.getStashList().add(third);
            RxSessionBeanResolver.register(request.getSession(), concurrentBean);
            request.setParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));
            request.setParameter("drugName_222222", "Second medication");
            request.setParameter("drugName_333333", "Third medication");
            action = spy(action);
            doAnswer(invocation -> {
                assertThat(concurrentBean.getStash()).containsExactly(second, third);
                assertThat(second.getBrandName()).isEqualTo("Second medication");
                assertThat(third.getBrandName()).isEqualTo("Third medication");
                return "9001";
            }).when(action).persistStash(mockLoggedInInfo, concurrentBean);

            assertThat(action.updateSaveAllDrugs()).isEqualTo("refresh");
            concurrentBean.awaitCompletion();

            assertThat(concurrentBean.getStash()).containsExactly(second, third);
            verify(action).persistStash(mockLoggedInInfo, concurrentBean);
        }
    }

    @Test
    @DisplayName("unticking a ReRx source removes every staged copy of that source")
    void shouldRemoveEverySourceCard_whenReRxUnchecked() throws Exception {
        bean.clearStash();
        RxPrescriptionData.Prescription first = draft(1, "first");
        RxPrescriptionData.Prescription second = draft(2, "second");
        RxPrescriptionData.Prescription other = draft(3, "other");
        first.setDrugReferenceId(55);
        second.setDrugReferenceId(55);
        other.setDrugReferenceId(66);
        bean.getStashList().add(first);
        bean.getStashList().add(second);
        bean.getStashList().add(other);
        bean.setStashIndex(2);
        bean.addReRxDrugIdList("55");
        bean.addReRxDrugIdList("66");
        request.setParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));
        request.setParameter("action", "removeFromReRxDrugIdList");
        request.setParameter("reRxDrugId", "55");

        action.updateReRxDrug();

        assertThat(bean.getStash()).containsExactly(other);
        assertThat(bean.getCurrentStashItem()).isSameAs(other);
        assertThat(bean.getReRxDrugIdList()).containsExactly("66");
    }

    @Test
    @DisplayName("a concurrent close cannot make a custom-name edit overwrite another card")
    void shouldEditOnlyNamedCard_whenPrecedingCardCloses() throws Exception {
        try (ConcurrentRxStashClose concurrentBean = new ConcurrentRxStashClose(1)) {
            concurrentBean.setDemographicNo(DEMOGRAPHIC_NO);
            RxPrescriptionData.Prescription first = draft(1, "first");
            RxPrescriptionData.Prescription edited = draft(2, "second");
            RxPrescriptionData.Prescription other = draft(3, "third");
            concurrentBean.getStashList().addAll(java.util.List.of(first, edited, other));
            RxSessionBeanResolver.register(request.getSession(), concurrentBean);
            request.setParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));
            request.setParameter("randomId", "2");
            request.setParameter("customName", "Updated medication");

            action.saveCustomName();
            concurrentBean.awaitCompletion();

            assertThat(concurrentBean.getStash()).containsExactly(edited, other);
            assertThat(edited.getCustomName()).isEqualTo("Updated medication");
            assertThat(other.getBrandName()).isEqualTo("third");
        }
    }

    @Test
    @DisplayName("Save And Print keeps the selected medication until its update and save finish")
    void shouldSaveSelectedCard_whenAnotherWindowClosesIt() throws Exception {
        try (ConcurrentRxStashClose concurrentBean = new ConcurrentRxStashClose(2)) {
            concurrentBean.setDemographicNo(DEMOGRAPHIC_NO);
            RxPrescriptionData.Prescription first = draft(1, "first");
            RxPrescriptionData.Prescription selected = draft(2, "selected");
            RxPrescriptionData.Prescription other = draft(3, "third");
            concurrentBean.getStashList().addAll(java.util.List.of(first, selected, other));
            concurrentBean.setStashIndex(1);
            RxSessionBeanResolver.register(request.getSession(), concurrentBean);
            request.setParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));
            action.setAction("updateAndPrint");
            action.setGCN_SEQNO("0");
            action.setCustomName("Updated medication");
            action.setSpecial("take one tablet daily");
            action.setUnit("tab");
            action.setDosage("1");
            action = spy(action);
            doAnswer(invocation -> {
                assertThat(concurrentBean.getStash()).containsExactly(first, selected, other);
                assertThat(selected.getCustomName()).isEqualTo("Updated medication");
                return "9001";
            }).when(action).persistStash(mockLoggedInInfo, concurrentBean);

            assertThat(action.execute()).isEqualTo("viewScript");
            concurrentBean.awaitCompletion();

            assertThat(concurrentBean.getStash()).containsExactly(first, other);
            verify(action).persistStash(mockLoggedInInfo, concurrentBean);
        }
    }

    @Test
    @DisplayName("a last-card close before persistence creates no empty script or archival")
    void shouldSkipPersistence_whenLastCardClosesAfterSavePrecheck() throws Exception {
        try (ConcurrentRxStashClose concurrentBean = new ConcurrentRxStashClose(1);
             var data = org.mockito.Mockito.mockConstruction(RxPrescriptionData.class)) {
            concurrentBean.setDemographicNo(DEMOGRAPHIC_NO);
            concurrentBean.getStashList().add(draft(1, "first"));
            concurrentBean.addReRxDrugIdList("55");
            RxSessionBeanResolver.register(request.getSession(), concurrentBean);
            request.setParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));
            concurrentBean.armSizeCheck();

            action.saveDrug(request);
            concurrentBean.awaitCompletion();

            assertThat(data.constructed()).isEmpty();
            assertThat(concurrentBean.getReRxDrugIdList()).containsExactly("55");
            verifyNoInteractions(mockRxManager);
        }
    }

    @Test
    @DisplayName("saving clears only the reprint that existed before the save")
    void shouldPreserveNewReprint_whenOpenedDuringSave() throws Exception {
        bean.clearStash();
        bean.getStashList().add(draft(1, "first"));
        RxSessionBean prior = new RxSessionBean();
        prior.setDemographicNo(DEMOGRAPHIC_NO);
        RxSessionBean newer = new RxSessionBean();
        newer.setDemographicNo(DEMOGRAPHIC_NO);
        RxReprintWorkspace.store(request.getSession(), prior, "previous");
        request.setParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));
        request.setParameter("drugName_1", "First medication");
        action = spy(action);
        doAnswer(invocation -> {
            RxReprintWorkspace.store(request.getSession(), newer, "newer");
            return "9001";
        }).when(action).persistStash(mockLoggedInInfo, bean);

        assertThat(action.updateSaveAllDrugs()).isEqualTo("refresh");

        assertThat(RxReprintWorkspace.find(request.getSession(), DEMOGRAPHIC_NO).bean()).isSameAs(newer);
    }

    @ParameterizedTest
    @ValueSource(strings = {"elementId", "propertyValue"})
    @DisplayName("a missing property edit parameter returns 400 before touching the card")
    void shouldRejectPropertyEdit_whenParameterIsMissing(String missing) throws Exception {
        request.setParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));
        request.setParameter("elementId", "method_1");
        request.setParameter("propertyValue", "oral");
        request.removeParameter(missing);

        assertThat(action.updateProperty()).isEqualTo(RxWriteScript2Action.NONE);
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(stagedCard);
    }

    private static RxPrescriptionData.Prescription draft(int key, String name) {
        RxPrescriptionData.Prescription card = new RxPrescriptionData.Prescription(0, "999998", DEMOGRAPHIC_NO);
        card.setRandomId(key);
        card.setBrandName(name);
        card.setSpecial("Take daily");
        card.setQuantity("30");
        return card;
    }

    @ParameterizedTest(name = "action={0}")
    @ValueSource(strings = {"update", "updateAddAnother", "updateAndPrint"})
    @DisplayName("should not rewrite the staged item when an update names no patient")
    void shouldKeepStagedItem_whenUpdateNamesNoPatient(String updateAction) throws Exception {
        action.setAction(updateAction);

        String result = action.execute();

        assertThat(result).isNull();
        assertThat(response.getRedirectedUrl()).isEqualTo("error.html");
        assertThat(bean.getStashItem(0)).isSameAs(stagedCard);
        verifyNoInteractions(stagedCard);
        verifyNoInteractions(mockSignatureStampService);
    }

    @Test
    @DisplayName("should rewrite the named patient's staged item")
    void shouldRewriteStagedItem_whenUpdateNamesPatient() throws Exception {
        request.setParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));
        action.setAction("update");
        action.setGCN_SEQNO("0");
        action.setCustomName("Custom drug");
        action.setRxDate("2026-09-24");
        action.setWrittenDate("2026-09-24");
        action.setSpecial("take one tablet daily");
        action.setUnit("tab");
        action.setDosage("1");

        String result = action.execute();

        assertThat(result).isEqualTo("refresh");
        verify(stagedCard).setCustomName("Custom drug");
        verify(stagedCard).setSpecial("take one tablet daily");
    }

    @Test
    @DisplayName("should refuse an update when no staged item is selected instead of failing")
    void shouldRefuseUpdate_whenCursorSelectsNothing() throws Exception {
        request.setParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));
        bean.setStashIndex(-1);
        action.setAction("update");

        String result = action.execute();

        assertThat(result).isEqualTo(RxWriteScript2Action.NONE);
        assertThat(response.getStatus()).isEqualTo(409);
        verifyNoInteractions(stagedCard, mockSignatureStampService);
    }

    @Test
    @DisplayName("should skip a malformed card key in a save instead of failing")
    void shouldSkipMalformedCardKey_whenSaving() throws Exception {
        request.setParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));
        request.setParameter("parameterValue", "updateSaveAllDrugs");
        request.setParameter("drugName_abc", "MALFORMED");

        String result = action.execute();

        // Only a malformed key: nothing names a staged card, so it is an empty save (400).
        assertThat(result).isEqualTo(RxWriteScript2Action.NONE);
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(bean.getStashList()).containsExactly(stagedCard);
    }

    @Test
    @DisplayName("should still re-render through the fallback patient for a non-update action")
    void shouldRefresh_whenNonUpdateActionNamesNoPatient() throws Exception {
        action.setAction("refresh");

        String result = action.execute();

        assertThat(result).isEqualTo("refresh");
        assertThat(response.getRedirectedUrl()).isNull();
        verifyNoInteractions(stagedCard);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"GET", "HEAD"})
    @DisplayName("should reject a non-POST save before resolving or touching the stash")
    void shouldRejectSave_whenMethodIsNotPost(String httpMethod) throws Exception {
        request.setMethod(httpMethod);
        request.setParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));
        request.setParameter("parameterValue", "updateSaveAllDrugs");
        request.setParameter("drugName_111111", "CARD");

        String result = action.execute();

        assertThat(result).isEqualTo(RxWriteScript2Action.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        assertThat(bean.getStashList()).containsExactly(stagedCard);
        verifyNoInteractions(stagedCard, mockRxManager, mockSignatureStampService);
        logActionMock.verifyNoInteractions();
    }

    @ParameterizedTest(name = "{0} action={1}")
    @org.junit.jupiter.params.provider.CsvSource({"GET,update", "GET,updateAndPrint", "HEAD,updateAddAnother"})
    @DisplayName("should reject a non-POST update before rewriting or saving the staged item")
    void shouldRejectUpdate_whenMethodIsNotPost(String httpMethod, String updateAction) throws Exception {
        request.setMethod(httpMethod);
        request.setParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));
        action.setAction(updateAction);
        action.setGCN_SEQNO("0");
        action.setCustomName("Custom drug");

        String result = action.execute();

        assertThat(result).isEqualTo(RxWriteScript2Action.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        verifyNoInteractions(stagedCard, mockRxManager, mockSignatureStampService);
        logActionMock.verifyNoInteractions();
    }

    @ParameterizedTest(name = "{0} {1}")
    @org.junit.jupiter.params.provider.CsvSource({
            "GET,updateLongTermStatus", "HEAD,updateLongTermStatus", "GET,saveCustomName", "GET,newCustomNote",
            "GET,newCustomDrug", "GET,normalDrugSetCustom", "GET,createNewRx", "GET,updateDrug",
            "GET,updateSpecialInstruction", "HEAD,updateProperty"})
    @DisplayName("should reject a non-POST stash or chart write dispatch before touching anything")
    void shouldRejectWriteDispatch_whenMethodIsNotPost(String httpMethod, String dispatch) throws Exception {
        request.setMethod(httpMethod);
        request.setParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));
        request.setParameter("parameterValue", dispatch);
        request.setParameter("ltDrugId", "77");
        request.setParameter("isLongTerm", "true");
        request.setParameter("randomId", "111111");

        String result = action.execute();

        assertThat(result).isEqualTo(RxWriteScript2Action.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        assertThat(bean.getStashList()).containsExactly(stagedCard);
        verifyNoInteractions(stagedCard, mockRxManager, mockSignatureStampService);
        logActionMock.verifyNoInteractions();
    }

    @Test
    @DisplayName("should end Struts processing after writing the special-instruction JSON")
    void shouldReturnNone_afterWritingSpecialInstructionJson() throws Exception {
        // A named result forwarded prescribe.jsp over the JSON the autocomplete reads.
        request.setParameter("query", "take");
        // Dispatched before execute(): the read requires global _rx read (#3908).
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_rx"), eq("r"), isNull())).thenReturn(true);
        when(mockRxManager.getStoredInstructionsMatching("take")).thenReturn(java.util.Set.of("take one daily"));

        String result = action.searchSpecialInstructions();

        assertThat(result).isEqualTo(RxWriteScript2Action.NONE);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).contains("take one daily");
    }

    @Test
    @DisplayName("should still allow a GET re-render that is not an update")
    void shouldAllowGet_forNonUpdateRender() throws Exception {
        request.setMethod("GET");
        action.setAction("edit");

        assertThat(action.execute()).isEqualTo("refresh");
        verifyNoInteractions(stagedCard);
    }

    @Test
    @DisplayName("should refuse an empty save without pruning the staged medications")
    void shouldKeepStash_whenSaveSubmitsNoStagedMedication() throws Exception {
        request.setParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));
        request.setParameter("parameterValue", "updateSaveAllDrugs");
        RxPrescriptionData.Prescription second = mock(RxPrescriptionData.Prescription.class);
        bean.getStashList().add(second);

        String result = action.execute();

        assertThat(result).isEqualTo(RxWriteScript2Action.NONE);
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(bean.getStashList()).containsExactly(stagedCard, second);
        verifyNoInteractions(stagedCard, second, mockRxManager, mockSignatureStampService);
        logActionMock.verifyNoInteractions();
    }

    @Test
    @DisplayName("should refuse a save whose cards are all stale without pruning the staged medications")
    void shouldKeepStash_whenSaveNamesOnlyStaleCards() throws Exception {
        request.setParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));
        request.setParameter("parameterValue", "updateSaveAllDrugs");
        request.setParameter("drugName_999999", "STALE CARD");
        when(stagedCard.getRandomId()).thenReturn(111111L);

        String result = action.execute();

        assertThat(result).isEqualTo(RxWriteScript2Action.NONE);
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(bean.getStashList()).containsExactly(stagedCard);
        verifyNoInteractions(mockRxManager, mockSignatureStampService);
        logActionMock.verifyNoInteractions();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"updateReRxDrug", "saveCustomName", "newCustomNote", "newCustomDrug",
            "normalDrugSetCustom", "createNewRx", "updateDrug", "updateLongTermStatus",
            "updateSpecialInstruction", "updateProperty", "updateSaveAllDrugs"})
    @DisplayName("should return an AJAX failure when the named patient has no workspace")
    void shouldRejectAjaxMutation_whenPatientWorkspaceIsMissing(String method) throws Exception {
        request.setParameter("parameterValue", method);
        request.setParameter("demographicNo", "9999");
        request.setParameter("ltDrugId", "77");
        request.setParameter("action", "removeFromReRxDrugIdList");
        request.setParameter("reRxDrugId", "77");
        bean.addReRxDrugIdList("77");

        assertThat(action.execute()).isEqualTo(RxWriteScript2Action.NONE);

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(bean.getStashList()).containsExactly(stagedCard);
        assertThat(bean.getReRxDrugIdList()).containsExactly("77");
        verifyNoInteractions(stagedCard, mockRxManager, mockSignatureStampService);
        logActionMock.verifyNoInteractions();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"malformed", "9999"})
    @DisplayName("should reject renewal removal without falsely acknowledging an unresolved patient")
    void shouldRejectReRxRemoval_whenPatientCannotBeResolved(String patient) throws Exception {
        request.setParameter("parameterValue", "updateReRxDrug");
        if (patient != null) request.setParameter("demographicNo", patient);
        request.setParameter("action", "removeFromReRxDrugIdList");
        request.setParameter("reRxDrugId", "77");
        bean.addReRxDrugIdList("77");

        assertThat(action.execute()).isEqualTo(RxWriteScript2Action.NONE);

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(bean.getStashList()).containsExactly(stagedCard);
        assertThat(bean.getReRxDrugIdList()).containsExactly("77");
        verifyNoInteractions(stagedCard, mockRxManager, mockSignatureStampService);
    }

    @Test
    @DisplayName("should not change a drug's long-term status when the request names no patient")
    void shouldNotArchive_whenLongTermToggleNamesNoPatient() throws Exception {
        request.setParameter("parameterValue", "updateLongTermStatus");
        request.setParameter("ltDrugId", "77");
        request.setParameter("isLongTerm", "true");

        String result = action.execute();

        assertThat(result).isEqualTo(RxWriteScript2Action.NONE);
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getRedirectedUrl()).isNull();
        verifyNoInteractions(mockRxManager);
    }
}
