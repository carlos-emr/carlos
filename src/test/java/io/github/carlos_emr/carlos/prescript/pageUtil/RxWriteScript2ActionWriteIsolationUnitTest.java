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

    @Test
    @DisplayName("should not change a drug's long-term status when the request names no patient")
    void shouldNotArchive_whenLongTermToggleNamesNoPatient() throws Exception {
        request.setParameter("parameterValue", "updateLongTermStatus");
        request.setParameter("ltDrugId", "77");
        request.setParameter("isLongTerm", "true");

        String result = action.execute();

        assertThat(result).isNull();
        assertThat(response.getRedirectedUrl()).isEqualTo("error.html");
        verifyNoInteractions(mockRxManager);
    }
}
