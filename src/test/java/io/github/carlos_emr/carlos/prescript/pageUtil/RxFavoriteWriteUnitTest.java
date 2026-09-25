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

import io.github.carlos_emr.carlos.commn.dao.FavoriteDao;
import io.github.carlos_emr.carlos.commn.model.Favorite;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.prescript.util.RxUtil;
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
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Favourite edits and deletes: POST-only, own favourites only, and a missing favourite or a
 * malformed id or repeat is a 4xx rather than an NPE or NumberFormatException (#3908).
 *
 * @since 2026-09-24
 */
@DisplayName("Rx favourite writes")
@Tag("unit")
@Tag("prescript")
@Tag("security")
class RxFavoriteWriteUnitTest extends CarlosUnitTestBase {

    private static final String PROVIDER_NO = "999998";
    private static final int FAVORITE_ID = 12;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FavoriteDao favoriteDao;
    private SecurityInfoManager securityInfoManager;
    private Favorite stored;
    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("POST", "/rx/updateFavorite2");
        response = new MockHttpServletResponse();
        securityInfoManager = mock(SecurityInfoManager.class);
        favoriteDao = mock(FavoriteDao.class);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(FavoriteDao.class, favoriteDao);
        when(securityInfoManager.hasPrivilege(any(), eq("_rx"), eq("u"), isNull())).thenReturn(true);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn(PROVIDER_NO);

        stored = new Favorite();
        stored.setId(FAVORITE_ID);
        stored.setProviderNo(PROVIDER_NO);
        when(favoriteDao.find(FAVORITE_ID)).thenReturn(stored);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
    }

    @AfterEach
    void tearDown() {
        loggedInInfoMock.close();
        servletActionContextMock.close();
    }

    private String ajaxEdit() throws Exception {
        request.setParameter("method", "ajaxEditFavorite");
        return new RxUpdateFavorite2Action().execute();
    }

    private void editParameters(String favoriteId, String repeat) {
        request.setParameter("favoriteId", favoriteId);
        request.setParameter("repeat", repeat);
        request.setParameter("favoriteName", "renamed");
        request.setParameter("takeMin", "1");
        request.setParameter("takeMax", "1");
        request.setParameter("nosubs", "false");
        request.setParameter("prn", "true");
        request.setParameter("customInstr", "false");
    }

    @Test
    @DisplayName("should save an edit of the caller's own favourite")
    void shouldSaveEdit_whenFavoriteIsOwn() throws Exception {
        editParameters(String.valueOf(FAVORITE_ID), "2");

        assertThat(ajaxEdit()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(favoriteDao).findByEverything(eq(PROVIDER_NO), eq("renamed"), any(), any(), any(), anyFloat(),
                anyFloat(), any(), any(), any(), any(), eq(2), anyBoolean(), eq(true), any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("should answer 404 without an NPE when the favourite does not exist")
    void shouldReturnNotFound_whenFavoriteMissing() throws Exception {
        editParameters("99", "2");

        assertThat(ajaxEdit()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(404);
        verify(favoriteDao, never()).merge(any());
    }

    @Test
    @DisplayName("should refuse to edit another provider's favourite")
    void shouldReturnForbidden_whenFavoriteBelongsToAnotherProvider() throws Exception {
        stored.setProviderNo("123456");
        editParameters(String.valueOf(FAVORITE_ID), "2");

        assertThat(ajaxEdit()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(stored.getName()).isNull();
        verify(favoriteDao, never()).merge(any());
    }

    @ParameterizedTest(name = "favoriteId=\"{0}\"")
    @ValueSource(strings = {"", "abc", "1.5", "12345678901"})
    @DisplayName("should answer 400 for a malformed favourite id")
    void shouldReturnBadRequest_whenFavoriteIdMalformed(String favoriteId) throws Exception {
        editParameters(favoriteId, "2");

        assertThat(ajaxEdit()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(favoriteDao);
    }

    @ParameterizedTest(name = "repeat=\"{0}\"")
    @ValueSource(strings = {"", "two", "-1"})
    @DisplayName("should answer 400 for a malformed repeat count")
    void shouldReturnBadRequest_whenRepeatMalformed(String repeat) throws Exception {
        editParameters(String.valueOf(FAVORITE_ID), repeat);

        assertThat(ajaxEdit()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(favoriteDao);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"GET", "HEAD"})
    @DisplayName("should refuse a non-POST favourite edit or delete before loading anything")
    void shouldRejectWrite_whenMethodIsNotPost(String httpMethod) throws Exception {
        request.setMethod(httpMethod);
        editParameters(String.valueOf(FAVORITE_ID), "2");

        assertThat(ajaxEdit()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        response = new MockHttpServletResponse();
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
        RxDeleteFavorite2Action delete = new RxDeleteFavorite2Action();
        delete.setFavoriteId(String.valueOf(FAVORITE_ID));
        assertThat(delete.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);

        verifyNoInteractions(favoriteDao);
    }

    @Test
    @DisplayName("should delete only the caller's own favourite")
    void shouldDeleteOnlyOwnFavorite_whenDeleting() throws Exception {
        RxDeleteFavorite2Action delete = new RxDeleteFavorite2Action();
        delete.setFavoriteId(String.valueOf(FAVORITE_ID));
        assertThat(delete.execute()).isEqualTo(ActionSupport.SUCCESS);
        verify(favoriteDao).remove(FAVORITE_ID);

        stored.setProviderNo("123456");
        response = new MockHttpServletResponse();
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
        RxDeleteFavorite2Action foreign = new RxDeleteFavorite2Action();
        foreign.setFavoriteId(String.valueOf(FAVORITE_ID));
        assertThat(foreign.execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(403);
        verify(favoriteDao).remove(FAVORITE_ID);
    }
    @ParameterizedTest(name = "AJAX={0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("a concurrent close cannot change which medication is added to favourites")
    void shouldFavoriteRequestedCard_whenAnotherWindowClosesPrecedingCard(boolean ajax) throws Exception {
        openRxForWrite(1001);
        try (ConcurrentRxStashClose bean = new ConcurrentRxStashClose(111)) {
            bean.setDemographicNo(1001);
            bean.setProviderNo(PROVIDER_NO);
            RxPrescriptionData.Prescription first = mock(RxPrescriptionData.Prescription.class);
            RxPrescriptionData.Prescription selected = mock(RxPrescriptionData.Prescription.class);
            RxPrescriptionData.Prescription third = mock(RxPrescriptionData.Prescription.class);
            when(first.getRandomId()).thenReturn(111L);
            when(selected.getRandomId()).thenReturn(222L);
            when(third.getRandomId()).thenReturn(333L);
            bean.getStashList().addAll(List.of(first, selected, third));
            RxSessionBeanResolver.register(request.getSession(), bean);
            RxAddFavorite2Action add = new RxAddFavorite2Action();
            if (ajax) {
                request.setParameter("randomId", "222");
                request.setParameter("favoriteName", "chosen");
                assertThat(add.addFav2()).isEqualTo(ActionSupport.NONE);
            } else {
                add.setStashId("1");
                add.setFavoriteName("chosen");
                bean.armSizeCheck();
                assertThat(add.execute()).isEqualTo(ActionSupport.SUCCESS);
            }
            bean.awaitCompletion();

            verify(selected).AddToFavorites(PROVIDER_NO, "chosen");
            verify(first, never()).AddToFavorites(anyString(), anyString());
            verify(third, never()).AddToFavorites(anyString(), anyString());
            assertThat(bean.getStashList()).containsExactly(selected, third);
        }
    }

    @ParameterizedTest(name = "AJAX={0}, patient={1}")
    @CsvSource({"true, missing", "false, missing", "true, malformed", "false, malformed",
            "true, unopened", "false, unopened"})
    @DisplayName("favorite staging refuses an unresolved patient without a successful redirect")
    void shouldRejectStaging_whenWorkspaceCannotBeResolved(boolean ajax, String patient) throws Exception {
        RxSessionBean bean = openRxForWrite(1001);
        if ("missing".equals(patient)) request.removeParameter("demographicNo");
        else request.setParameter("demographicNo", "unopened".equals(patient) ? "9999" : "invalid");

        assertThat(stageFavorite(ajax)).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(bean.getStashSize()).isZero();
        verifyNoInteractions(favoriteDao);
    }

    @ParameterizedTest(name = "AJAX={0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("favorite staging reports a failure when creating the draft fails")
    void shouldReturnServerError_whenFavoriteStagingThrows(boolean ajax) throws Exception {
        RxSessionBean bean = openRxForWrite(1001);
        RxPrescriptionData.Favorite favorite = mock(RxPrescriptionData.Favorite.class);
        when(favorite.getProviderNo()).thenReturn(PROVIDER_NO);
        try (var _ = mockConstruction(RxPrescriptionData.class, (data, context) -> {
            when(data.getFavorite(FAVORITE_ID)).thenReturn(favorite);
            when(data.newPrescription(anyString(), anyInt(), any(RxPrescriptionData.Favorite.class)))
                    .thenThrow(new IllegalStateException("draft creation failed"));
        })) {
            assertThat(stageFavorite(ajax)).isEqualTo(ActionSupport.NONE);
        }

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(bean.getStashSize()).isZero();
        assertThat(request.getAttribute("listRxDrugs")).isNull();
    }

    @Test
    @DisplayName("concurrent favorite staging cannot render a copy that was discarded as a duplicate")
    void shouldRenderOnlyStoredCard_whenAnotherWindowStagesSameFavorite() throws Exception {
        openRxForWrite(1001);
        stored.setBn("AMOXICILLIN");
        stored.setGcnSeqno("12345");
        try (ConcurrentFavoriteStaging bean = new ConcurrentFavoriteStaging()) {
            bean.setDemographicNo(1001);
            bean.setProviderNo(PROVIDER_NO);
            RxSessionBeanResolver.register(request.getSession(), bean);

            assertThat(useFav2(String.valueOf(FAVORITE_ID))).isEqualTo("useFav2");
            bean.awaitCompletion();

            assertThat(bean.competingStateDuringInsert).isEqualTo(Thread.State.BLOCKED);
            assertThat(bean.getStashSize()).isEqualTo(1);
            List<?> renderedCards = (List<?>) request.getAttribute("listRxDrugs");
            assertThat(renderedCards).hasSize(1);
            assertThat(renderedCards.getFirst()).isSameAs(bean.getStashItem(0));
            assertThat(bean.getStashItem(0).getRandomId()).isEqualTo(123L);
            assertThat(bean.getCurrentStashItem()).isSameAs(bean.getStashItem(0));
        }
    }

    private String stageFavorite(boolean ajax) throws Exception {
        request.setParameter("favoriteId", String.valueOf(FAVORITE_ID));
        request.setParameter("randomId", "123");
        RxUseFavorite2Action use = new RxUseFavorite2Action();
        use.setFavoriteId(String.valueOf(FAVORITE_ID));
        return ajax ? use.useFav2() : use.execute();
    }

    /** Places another window's identical staging between the uniqueness check and insertion. */
    private static final class ConcurrentFavoriteStaging extends RxSessionBean implements AutoCloseable {
        private Thread competing;
        private Thread.State competingStateDuringInsert;

        @Override
        public int addStashItem(LoggedInInfo loggedInInfo, RxPrescriptionData.Prescription item) {
            RxPrescriptionData.Prescription other = new RxPrescriptionData.Prescription(0, PROVIDER_NO, 1001);
            other.setRandomId(456);
            other.setBrandName(item.getBrandName());
            other.setGCN_SEQNO(item.getGCN_SEQNO());
            competing = new Thread(() -> {
                synchronized (this) {
                    if (RxUtil.isRxUniqueInStash(this, other)) getStashList().add(other);
                }
            }, "concurrent-favorite-stage");
            competing.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (competing.isAlive() && competing.getState() != Thread.State.BLOCKED
                    && System.nanoTime() < deadline) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
            }
            competingStateDuringInsert = competing.getState();
            return super.addStashItem(loggedInInfo, item);
        }

        void awaitCompletion() throws InterruptedException {
            if (competing != null) {
                competing.join(TimeUnit.SECONDS.toMillis(5));
                assertThat(competing.isAlive()).isFalse();
            }
        }

        @Override
        public void close() throws InterruptedException {
            awaitCompletion();
        }
    }

    /** Names the patient, grants patient-level Rx write, and opens that patient's bean. */
    private RxSessionBean openRxForWrite(int demographicNo) {
        when(securityInfoManager.hasPrivilege(any(), eq("_rx"), eq("w"), isNull())).thenReturn(true);
        when(securityInfoManager.hasPrivilege(any(), eq("_rx"), eq("w"), eq(demographicNo))).thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(), eq(demographicNo))).thenReturn(true);
        registerMock(io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO.class,
                mock(io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO.class));
        registerMock(io.github.carlos_emr.carlos.commn.dao.PartialDateDao.class,
                mock(io.github.carlos_emr.carlos.commn.dao.PartialDateDao.class));
        RxSessionBean bean = new RxSessionBean();
        bean.setDemographicNo(demographicNo);
        bean.setProviderNo(PROVIDER_NO);
        RxSessionBeanResolver.register(request.getSession(), bean);
        request.setParameter("demographicNo", String.valueOf(demographicNo));
        return bean;
    }

    private String useFav2(String favoriteId) throws Exception {
        request.setParameter("favoriteId", favoriteId);
        request.setParameter("randomId", "123");
        return new RxUseFavorite2Action().useFav2();
    }

    @Test
    @DisplayName("should stage the caller's own favourite for the named patient")
    void shouldStageFavorite_whenFavoriteIsOwn() throws Exception {
        RxSessionBean bean = openRxForWrite(1001);
        stored.setBn("AMOXICILLIN");
        stored.setGcnSeqno("12345");

        assertThat(useFav2(String.valueOf(FAVORITE_ID))).isEqualTo("useFav2");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(bean.getStashSize()).isEqualTo(1);
        assertThat(bean.getStashItem(0).getRandomId()).isEqualTo(123L);
    }

    @Test
    @DisplayName("should refuse to stage another provider's favourite")
    void shouldReturnForbidden_whenStagingAnotherProvidersFavorite() throws Exception {
        RxSessionBean bean = openRxForWrite(1001);
        stored.setProviderNo("someone-else");

        assertThat(useFav2(String.valueOf(FAVORITE_ID))).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(bean.getStashSize()).isZero();
    }

    @Test
    @DisplayName("should answer 404 and stage nothing when the favourite to use does not exist")
    void shouldReturnNotFound_whenStagingMissingFavorite() throws Exception {
        RxSessionBean bean = openRxForWrite(1001);

        assertThat(useFav2("99")).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(bean.getStashSize()).isZero();
    }

    @ParameterizedTest(name = "favoriteId={0}")
    @ValueSource(strings = {"", "abc", "-1", "12345678901"})
    @DisplayName("should answer 400 and stage nothing when the favourite id is malformed")
    void shouldReturnBadRequest_whenStagingMalformedFavoriteId(String favoriteId) throws Exception {
        RxSessionBean bean = openRxForWrite(1001);

        assertThat(useFav2(favoriteId)).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(bean.getStashSize()).isZero();
        verify(favoriteDao, never()).find(any());
    }
}
