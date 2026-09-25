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

import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Per-patient write isolation for the Rx actions that change a patient's staged prescriptions or
 * chart (#3875): {@link RxClearPending2Action}, {@link RxDeleteRx2Action},
 * {@link RxAddFavorite2Action} (staged-card path) and the legacy {@code action=delete} branch of
 * {@link RxStash2Action}. A request that names no patient must not fall back to the session's
 * last-opened Rx patient; a request that names its patient keeps working.
 *
 * @since 2026-09-24
 */
@DisplayName("Rx write actions refuse the no-patient fallback")
@Tag("unit")
@Tag("prescript")
@Tag("security")
class RxStashWriteIsolationUnitTest extends CarlosUnitTestBase {

    private static final int DEMOGRAPHIC_NO = 1001;
    private static final String PROVIDER_NO = "999998";

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private AutoCloseable mocks;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;
    @Mock
    private LoggedInInfo mockLoggedInInfo;
    @Mock
    private DrugDao mockDrugDao;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private RxSessionBean bean;
    private RxPrescriptionData.Prescription stagedCard;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
        registerMock(DrugDao.class, mockDrugDao);
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_rx"), anyString(), isNull())).thenReturn(true);
        // Patient-level Rx access (the shared Rx write check, #3908) is granted unless a test denies it.
        when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), anyInt())).thenReturn(true);
        when(mockSecurityInfoManager.isAllowedAccessToPatientRecord(any(), any())).thenReturn(true);

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setMethod("POST");

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        // The patient is open in Rx and is the session's active (fallback) patient: exactly the
        // state in which a stale window for another chart would otherwise write here.
        bean = new RxSessionBean();
        bean.setDemographicNo(DEMOGRAPHIC_NO);
        bean.setProviderNo(PROVIDER_NO);
        stagedCard = mock(RxPrescriptionData.Prescription.class);
        when(stagedCard.getRandomId()).thenReturn(111111L);
        bean.getStashList().add(stagedCard);
        bean.getStashList().add(new RxPrescriptionData.Prescription(0, PROVIDER_NO, DEMOGRAPHIC_NO));
        bean.setStashIndex(1);
        bean.addReRxDrugIdList("55");
        RxSessionBeanResolver.register(request.getSession(), bean);
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

    private void namePatient() {
        request.setParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));
    }

    @Nested
    @DisplayName("RxClearPending2Action")
    class ClearPending {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"GET", "HEAD"})
        @DisplayName("should refuse a non-POST clear before resolving or clearing anything")
        void shouldRejectClear_whenMethodIsNotPost(String httpMethod) throws Exception {
            // CSRFGuard does not check GET, so a link or image tag must not clear the stash (#3908).
            request.setMethod(httpMethod);
            namePatient();
            RxClearPending2Action action = new RxClearPending2Action();
            action.setAction("");

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(405);
            assertThat(response.getHeader("Allow")).isEqualTo("POST");
            assertThat(bean.getStashSize()).isEqualTo(2);
            verifyNoInteractions(mockSecurityInfoManager, mockDrugDao, stagedCard);
            logActionMock.verifyNoInteractions();
        }

        @Test
        @DisplayName("should keep the stash when the request names no patient")
        void shouldKeepStash_whenRequestNamesNoPatient() throws Exception {
            RxClearPending2Action action = new RxClearPending2Action();
            action.setAction("");

            String result = action.execute();

            assertThat(result).isNull();
            assertThat(response.getRedirectedUrl()).isEqualTo("error.html");
            assertThat(bean.getStashSize()).isEqualTo(2);
        }

        @Test
        @DisplayName("should clear the named patient's stash")
        void shouldClearStash_whenRequestNamesPatient() throws Exception {
            namePatient();
            RxClearPending2Action action = new RxClearPending2Action();
            action.setAction("");

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            assertThat(bean.getStashSize()).isZero();
        }
    }

    @Nested
    @DisplayName("RxDeleteRx2Action")
    class DeleteRx {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"clearStash", "clearReRxDrugList", "Delete2", "Discontinue", ""})
        @DisplayName("should refuse every write that names no patient without touching the stash or drugs")
        void shouldRefuseWrite_whenRequestNamesNoPatient(String parameterValue) throws Exception {
            request.setParameter("parameterValue", parameterValue);
            request.setParameter("deleteRxId", "prefix_77");
            request.setParameter("drugId", "77");
            RxDeleteRx2Action action = new RxDeleteRx2Action();
            action.setDrugList("77");

            action.execute();

            assertThat(response.getRedirectedUrl()).isEqualTo("error.html");
            assertThat(bean.getStashSize()).isEqualTo(2);
            assertThat(bean.getReRxDrugIdList()).containsExactly("55");
            verifyNoInteractions(mockDrugDao);
            logActionMock.verifyNoInteractions();
        }

        @Test
        @DisplayName("should clear the named patient's stash")
        void shouldClearStash_whenRequestNamesPatient() throws Exception {
            namePatient();
            request.setParameter("parameterValue", "clearStash");

            String result = new RxDeleteRx2Action().execute();

            assertThat(result).isEqualTo("successClearStash");
            assertThat(bean.getStashSize()).isZero();
        }

        @Test
        @DisplayName("should refuse to clear the stash without the _rx update privilege")
        void shouldRefuseClearStash_whenRxUpdatePrivilegeMissing() {
            namePatient();
            request.setParameter("parameterValue", "clearStash");
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_rx"), eq("u"), isNull())).thenReturn(false);

            assertThatThrownBy(() -> new RxDeleteRx2Action().execute())
                    .hasMessageContaining("missing required sec object (_rx)");
            assertThat(bean.getStashSize()).isEqualTo(2);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"77,not-an-id", "not-an-id,77", "77,,78", "77,-1", "77,1234567890", "77,", ","})
        @DisplayName("should refuse the whole bulk delete when any drug id is malformed")
        void shouldRefuseWholeBulkDelete_whenAnyDrugIdMalformed(String drugList) throws Exception {
            // A malformed id used to stop validation early and still archive the ids before it (#3908).
            namePatient();
            RxDeleteRx2Action action = new RxDeleteRx2Action();
            action.setDrugList(drugList);

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(400);
            verifyNoInteractions(mockDrugDao);
            logActionMock.verifyNoInteractions();
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "77", "del_", "del_77_extra", "del_bad", "del_2147483648"})
        @DisplayName("should reject malformed single-delete ids without reporting success")
        void shouldRejectSingleDelete_whenIdMalformed(String rawId) throws Exception {
            namePatient();
            if (rawId != null) {
                request.setParameter("deleteRxId", rawId);
            }

            assertThat(new RxDeleteRx2Action().Delete2()).isEqualTo(ActionSupport.NONE);

            assertThat(response.getStatus()).isEqualTo(400);
            verifyNoInteractions(mockDrugDao);
            logActionMock.verifyNoInteractions();
        }

        @Test
        @DisplayName("should archive a valid single-delete target belonging to the requested patient")
        void shouldArchiveSingleDrug_whenPatientOwnsDrug() throws Exception {
            namePatient();
            request.setParameter("deleteRxId", "del_77");
            io.github.carlos_emr.carlos.commn.model.Drug drug = new io.github.carlos_emr.carlos.commn.model.Drug();
            drug.setId(77);
            drug.setDemographicId(DEMOGRAPHIC_NO);
            when(mockDrugDao.find(77)).thenReturn(drug);

            new RxDeleteRx2Action().Delete2();

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(drug.isArchived()).isTrue();
            verify(mockDrugDao).merge(drug);
        }

        @ParameterizedTest
        @ValueSource(booleans = {false, true})
        @DisplayName("should report failed delete persistence to the browser")
        void shouldReportFailure_whenDeletePersistenceFails(boolean bulk) throws Exception {
            namePatient();
            request.setParameter("deleteRxId", "del_77");
            io.github.carlos_emr.carlos.commn.model.Drug drug = new io.github.carlos_emr.carlos.commn.model.Drug();
            drug.setId(77);
            drug.setDemographicId(DEMOGRAPHIC_NO);
            when(mockDrugDao.find(77)).thenReturn(drug);
            doThrow(new IllegalStateException("Persistence unavailable"))
                    .when(mockDrugDao).merge(drug);

            RxDeleteRx2Action action = new RxDeleteRx2Action();
            action.setDrugList("77");
            assertThat(bulk ? action.execute() : action.Delete2()).isEqualTo(ActionSupport.NONE);

            assertThat(response.getStatus()).isEqualTo(500);
            logActionMock.verifyNoInteractions();
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", " "})
        @DisplayName("should reject discontinuation without a reason before archiving")
        void shouldRejectDiscontinue_whenReasonMissing(String reason) throws Exception {
            namePatient();
            request.setParameter("drugId", "77");
            if (reason != null) {
                request.setParameter("reason", reason);
            }

            assertThat(new RxDeleteRx2Action().Discontinue()).isEqualTo(ActionSupport.NONE);

            assertThat(response.getStatus()).isEqualTo(400);
            verifyNoInteractions(mockDrugDao);
            logActionMock.verifyNoInteractions();
        }

        @Test
        @DisplayName("should archive every drug in a well-formed bulk delete")
        void shouldArchiveEveryDrug_whenBulkDeleteWellFormed() throws Exception {
            namePatient();
            // Distinct ids: Drug#equals is field-based, so two blank drugs would be one to Mockito.
            io.github.carlos_emr.carlos.commn.model.Drug first = new io.github.carlos_emr.carlos.commn.model.Drug();
            first.setId(77);
            first.setDemographicId(DEMOGRAPHIC_NO);
            io.github.carlos_emr.carlos.commn.model.Drug second = new io.github.carlos_emr.carlos.commn.model.Drug();
            second.setId(78);
            second.setDemographicId(DEMOGRAPHIC_NO);
            when(mockDrugDao.find(77)).thenReturn(first);
            when(mockDrugDao.find(78)).thenReturn(second);
            RxDeleteRx2Action action = new RxDeleteRx2Action();
            action.setDrugList("77, 78");

            String result = action.execute();

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            assertThat(first.isArchived()).isTrue();
            assertThat(second.isArchived()).isTrue();
            verify(mockDrugDao).merge(first);
            verify(mockDrugDao).merge(second);
        }

        @Test
        @DisplayName("should clear the named patient's re-prescribe list")
        void shouldClearReRxList_whenRequestNamesPatient() throws Exception {
            namePatient();
            request.setParameter("parameterValue", "clearReRxDrugList");

            new RxDeleteRx2Action().execute();

            assertThat(response.getRedirectedUrl()).isNull();
            assertThat(bean.getReRxDrugIdList()).isEmpty();
        }
    }

    @Nested
    @DisplayName("RxAddFavorite2Action")
    class AddFavorite {

        @Test
        @DisplayName("should not favourite a staged card when the request names no patient")
        void shouldNotFavouriteStagedCard_whenRequestNamesNoPatient() throws Exception {
            RxAddFavorite2Action action = new RxAddFavorite2Action();
            action.setStashId("0");
            action.setFavoriteName("fav");

            action.execute();

            assertThat(response.getRedirectedUrl()).isEqualTo("error.html");
            verify(stagedCard, never()).AddToFavorites(anyString(), anyString());
        }

        @Test
        @DisplayName("should favourite the named patient's staged card")
        void shouldFavouriteStagedCard_whenRequestNamesPatient() throws Exception {
            namePatient();
            RxAddFavorite2Action action = new RxAddFavorite2Action();
            action.setStashId("0");
            action.setFavoriteName("fav");

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            verify(stagedCard).AddToFavorites(PROVIDER_NO, "fav");
        }

        @Test
        @DisplayName("should not favourite a staged card by random id when the request names no patient")
        void shouldNotFavouriteByRandomId_whenRequestNamesNoPatient() throws Exception {
            request.setParameter("parameterValue", "addFav2");
            request.setParameter("randomId", "111111");
            request.setParameter("favoriteName", "fav");

            new RxAddFavorite2Action().execute();

            // The AJAX caller gets 409, not a redirect it would follow to a 200 page (#3908).
            assertThat(response.getStatus()).isEqualTo(409);
            verify(stagedCard, never()).AddToFavorites(anyString(), anyString());
        }

        @Test
        @DisplayName("should favourite the named patient's staged card by random id")
        void shouldFavouriteByRandomId_whenRequestNamesPatient() throws Exception {
            namePatient();
            request.setParameter("parameterValue", "addFav2");
            request.setParameter("randomId", "111111");
            request.setParameter("favoriteName", "fav");

            new RxAddFavorite2Action().execute();

            assertThat(response.getRedirectedUrl()).isNull();
            verify(stagedCard).AddToFavorites(PROVIDER_NO, "fav");
        }
    }

    @Nested
    @DisplayName("stash cursor from a request")
    class StashCursor {

        @Test
        @DisplayName("should move only the named patient's cursor with two patients open")
        void shouldMoveOnlyNamedPatientsCursor_withTwoPatientsOpen() throws Exception {
            RxSessionBean other = new RxSessionBean();
            other.setDemographicNo(2002);
            other.setProviderNo(PROVIDER_NO);
            RxPrescriptionData.Prescription otherCard = new RxPrescriptionData.Prescription(0, PROVIDER_NO, 2002);
            otherCard.setRandomId(777777L);
            other.getStashList().add(otherCard);
            other.getStashList().add(new RxPrescriptionData.Prescription(0, PROVIDER_NO, 2002));
            other.setStashIndex(1);
            // Registering the second bean makes 2002 the active (fallback) patient.
            RxSessionBeanResolver.register(request.getSession(), other);
            namePatient();
            request.setParameter("parameterValue", "setStashIndex");
            request.setParameter("randomId", "111111");

            new RxStash2Action().execute();

            assertThat(bean.getStashIndex()).isZero();
            assertThat(other.getStashIndex()).isEqualTo(1);
        }

        @ParameterizedTest(name = "randomId={0}")
        @ValueSource(strings = {"999999", "abc", "-5", ""})
        @DisplayName("should leave the cursor alone for a stale or malformed card key")
        void shouldKeepCursor_whenCardKeyStaleOrMalformed(String randomId) throws Exception {
            namePatient();
            request.setParameter("parameterValue", "setStashIndex");
            request.setParameter("randomId", randomId);

            String result = new RxStash2Action().execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            assertThat(bean.getStashIndex()).isEqualTo(1);
            assertThat(bean.getStashSize()).isEqualTo(2);
        }

        @ParameterizedTest(name = "stashId={0}")
        @ValueSource(ints = {2, 99, -1, -7})
        @DisplayName("should leave the stash alone for an out-of-range legacy edit or delete")
        void shouldKeepStash_whenLegacyIndexOutOfRange(int stashId) throws Exception {
            namePatient();
            for (String legacyAction : new String[]{"edit", "delete"}) {
                RxStash2Action action = new RxStash2Action();
                action.setAction(legacyAction);
                action.setStashId(stashId);

                assertThat(action.execute()).isEqualTo(ActionSupport.SUCCESS);
                assertThat(bean.getStashIndex()).isEqualTo(1);
                assertThat(bean.getStashSize()).isEqualTo(2);
            }
        }

        @ParameterizedTest(name = "stashId={0}")
        @ValueSource(strings = {"2", "99", "-1", "abc"})
        @DisplayName("should favourite nothing for an out-of-range or malformed card position")
        void shouldRejectFavourite_whenCardPositionInvalid(String stashId) throws Exception {
            namePatient();
            RxAddFavorite2Action action = new RxAddFavorite2Action();
            action.setStashId(stashId);
            action.setFavoriteName("fav");

            assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(400);
            verify(stagedCard, never()).AddToFavorites(anyString(), anyString());
        }

        @ParameterizedTest(name = "randomId={0}")
        @ValueSource(strings = {"999999", "abc"})
        @DisplayName("should favourite nothing for a stale or malformed card key")
        void shouldRejectFavourite_whenCardKeyStaleOrMalformed(String randomId) throws Exception {
            namePatient();
            request.setParameter("parameterValue", "addFav2");
            request.setParameter("randomId", randomId);
            request.setParameter("favoriteName", "fav");

            new RxAddFavorite2Action().execute();

            assertThat(response.getStatus()).isEqualTo(400);
            verify(stagedCard, never()).AddToFavorites(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("RxStash2Action legacy action parameter")
    class StashLegacyAction {

        @Test
        @DisplayName("should keep the card when action=delete names no patient")
        void shouldKeepCard_whenLegacyDeleteNamesNoPatient() throws Exception {
            RxStash2Action action = new RxStash2Action();
            action.setAction("delete");
            action.setStashId(0);

            action.execute();

            assertThat(response.getRedirectedUrl()).isEqualTo("error.html");
            assertThat(bean.getStashSize()).isEqualTo(2);
        }

        @Test
        @DisplayName("should remove the card when action=delete names its patient")
        void shouldRemoveCard_whenLegacyDeleteNamesPatient() throws Exception {
            namePatient();
            RxStash2Action action = new RxStash2Action();
            action.setAction("delete");
            action.setStashId(0);

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            assertThat(bean.getStashSize()).isEqualTo(1);
        }

        @Test
        @DisplayName("should still move the cursor through the fallback patient for action=edit")
        void shouldMoveCursor_whenLegacyEditNamesNoPatient() throws Exception {
            RxStash2Action action = new RxStash2Action();
            action.setAction("edit");
            action.setStashId(0);

            String result = action.execute();

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            assertThat(bean.getStashIndex()).isZero();
        }
    }
}
