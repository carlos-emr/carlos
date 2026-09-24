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
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
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

            assertThat(response.getRedirectedUrl()).isEqualTo("error.html");
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
