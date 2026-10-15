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
package io.github.carlos_emr.carlos.prescript.web;

import io.github.carlos_emr.carlos.commn.dao.FavoritesDao;
import io.github.carlos_emr.carlos.commn.dao.FavoritesPrivilegeDao;
import io.github.carlos_emr.carlos.commn.model.Favorites;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Conditional mutation guard adapted from Copilot's PR #2478; reads remain GET-compatible. */
@Tag("unit")
@Tag("prescription")
@Tag("security")
@DisplayName("CopyFavorites2Action method contract")
class CopyFavorites2ActionUnitTest extends CarlosUnitTestBase {
    private MockedStatic<ServletActionContext> servletContext;
    private MockedStatic<LoggedInInfo> sessionInfo;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FavoritesDao favoritesDao;
    private FavoritesPrivilegeDao privilegeDao;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        favoritesDao = mock(FavoritesDao.class);
        privilegeDao = mock(FavoritesPrivilegeDao.class);
        registerMock(SecurityInfoManager.class, security);
        registerMock(FavoritesDao.class, favoritesDao);
        registerMock(FavoritesPrivilegeDao.class, privilegeDao);
        when(security.hasPrivilege(any(), eq("_rx"), eq("w"), isNull())).thenReturn(true);
        servletContext = mockStatic(ServletActionContext.class);
        servletContext.when(ServletActionContext::getRequest).thenReturn(request);
        servletContext.when(ServletActionContext::getResponse).thenReturn(response);
        sessionInfo = mockStatic(LoggedInInfo.class);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        sessionInfo.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
    }

    @AfterEach
    void tearDown() {
        sessionInfo.close();
        servletContext.close();
    }

    @ParameterizedTest
    @CsvSource({"GET,update", "HEAD,update", "GET,copy", "HEAD,copy"})
    @DisplayName("should refuse GET and HEAD writes before any favourite or privilege DAO access")
    void shouldRejectMutation_whenMethodIsGetOrHead(String httpMethod, String dispatch) throws Exception {
        request.setMethod(httpMethod);
        request.setParameter("dispatch", dispatch);
        request.setParameter("userProviderNo", "999998");
        request.setParameter("rb_share", "1");
        request.setParameter("providerNo", "999998");
        request.setParameter("ddl_provider", "999997");
        request.setParameter("countFavorites", "1");
        request.setParameter("selected0", "on");
        request.setParameter("fldFavoriteId0", "7");

        assertThat(new CopyFavorites2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(favoritesDao, privilegeDao);
    }

    @Test
    @DisplayName("should render the favourite selection page through GET without writing")
    void shouldRenderReadView_whenMethodIsGet() throws Exception {
        request.setMethod("GET");
        request.setParameter("ddl_provider", "999997");
        publicSource();

        assertThat(new CopyFavorites2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(request.getAttribute("copyProviderNo")).isEqualTo("999997");
        verify(favoritesDao).findByProviderNo("999997");
        verify(favoritesDao, never()).persist(any());
        verify(privilegeDao, never()).setFavoritesPrivilege(any(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    @DisplayName("should update sharing preferences through the existing POST workflow")
    void shouldUpdateSharing_whenMethodIsPost() throws Exception {
        request.setMethod("POST");
        request.setParameter("dispatch", "update");
        request.setParameter("userProviderNo", "999998");
        request.setParameter("rb_share", "1");

        assertThat(new CopyFavorites2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
        verify(privilegeDao).setFavoritesPrivilege("999998", true, false);
        verifyNoInteractions(favoritesDao);
    }

    @Test
    @DisplayName("should copy the selected favourite through the existing POST workflow")
    void shouldCopyFavorite_whenMethodIsPost() throws Exception {
        request.setMethod("POST");
        request.setParameter("dispatch", "copy");
        request.setParameter("providerNo", "999998");
        request.setParameter("ddl_provider", "999997");
        request.setParameter("countFavorites", "1");
        request.setParameter("selected0", "on");
        request.setParameter("fldFavoriteId0", "7");
        Favorites source = new Favorites();
        source.setId(7);
        source.setProviderNo("999997");
        when(favoritesDao.find((Object) Integer.valueOf(7))).thenReturn(source);
        publicSource();

        assertThat(new CopyFavorites2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
        verify(favoritesDao).persist(argThat(model -> model instanceof Favorites copy && copy != source
                && copy.getId() == null && "999998".equals(copy.getProviderNo())));
        assertThat(request.getAttribute("copiedFavoritesCount")).isEqualTo(1);
        verify(privilegeDao, never()).setFavoritesPrivilege(any(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean());
    }
    @Test
    @DisplayName("should carry the dispensing flag onto the copy instead of resetting it")
    void shouldCarryDispensingFlag_whenFavoriteIsCopied() throws Exception {
        copyRequest();
        Favorites source = favorite(7, "999997");
        source.setDispenseInternal(true);
        publicSource();

        assertThat(new CopyFavorites2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
        verify(favoritesDao).persist(argThat(model -> model instanceof Favorites copy && copy != source
                && copy.isDispenseInternal()));
    }

    private void publicSource() {
        var privilege = new io.github.carlos_emr.carlos.commn.model.FavoritesPrivilege();
        privilege.setProviderNo("999997");
        privilege.setOpenToPublic(true);
        when(privilegeDao.findByProviderNo("999997")).thenReturn(privilege);
    }

    private Favorites favorite(int id, String owner) {
        Favorites value = new Favorites();
        value.setId(id);
        value.setProviderNo(owner);
        when(favoritesDao.find((Object) Integer.valueOf(id))).thenReturn(value);
        return value;
    }

    private void copyRequest() {
        request.setMethod("POST");
        request.setParameter("dispatch", "copy");
        request.setParameter("ddl_provider", "999997");
        request.setParameter("countFavorites", "1");
        request.setParameter("selected0", "on");
        request.setParameter("fldFavoriteId0", "7");
    }

    @ParameterizedTest
    @CsvSource({"update,userProviderNo", "update,providerNo", "copy,userProviderNo", "copy,providerNo"})
    @DisplayName("should refuse a forged destination before changing anyone's favourites or sharing")
    void shouldRejectForeignDestination_whenHiddenProviderIsTampered(String dispatch, String parameter) throws Exception {
        copyRequest();
        request.setParameter("dispatch", dispatch);
        request.setParameter(parameter, "999996");
        request.setParameter("rb_share", "1");

        assertThat(new CopyFavorites2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(favoritesDao, privilegeDao);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST"})
    @DisplayName("should refuse private sources before reading or copying their favourites")
    void shouldRejectPrivateSource_whenSharingIsMissingOrRevoked(String method) throws Exception {
        copyRequest();
        request.setMethod(method);
        request.setParameter("dispatch", "GET".equals(method) ? "refresh" : "copy");
        var privilege = new io.github.carlos_emr.carlos.commn.model.FavoritesPrivilege();
        privilege.setProviderNo("999997");
        privilege.setOpenToPublic(false);
        when(privilegeDao.findByProviderNo("999997")).thenReturn(privilege);

        assertThat(new CopyFavorites2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(request.getAttribute("copyFavorites")).isNull();
        verifyNoInteractions(favoritesDao);
    }

    @ParameterizedTest
    @ValueSource(strings = {"foreign", "missing", "null-owner"})
    @DisplayName("should validate every selected favourite before writing any copy")
    void shouldRejectWholeSelection_whenLaterFavoriteDoesNotBelongToSource(String invalid) throws Exception {
        copyRequest();
        publicSource();
        favorite(7, "999997");
        request.setParameter("countFavorites", "2");
        request.setParameter("selected1", "on");
        request.setParameter("fldFavoriteId1", "8");
        if (!"missing".equals(invalid)) favorite(8, "foreign".equals(invalid) ? "999996" : null);

        assertThat(new CopyFavorites2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo("missing".equals(invalid) ? 404 : 403);
        verify(favoritesDao, never()).persist(any());
        assertThat(request.getAttribute("copiedFavoritesCount")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "invalid", "2147483648", "10001"})
    @DisplayName("should refuse malformed or excessive selection counts before loading favourites")
    void shouldRejectMalformedSelection_whenCountIsInvalid(String count) throws Exception {
        copyRequest();
        publicSource();
        request.setParameter("countFavorites", count);

        assertThat(new CopyFavorites2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(favoritesDao);
    }

    @Test
    @DisplayName("should copy a repeated selected ID only once")
    void shouldCopyOnce_whenSelectedIdsAreRepeated() throws Exception {
        copyRequest();
        publicSource();
        favorite(7, "999997");
        request.setParameter("countFavorites", "2");
        request.setParameter("selected1", "on");
        request.setParameter("fldFavoriteId1", "7");

        assertThat(new CopyFavorites2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
        verify(favoritesDao).persist(any());
        assertThat(request.getAttribute("copiedFavoritesCount")).isEqualTo(1);
    }

    @Test
    @DisplayName("should report partial copy failure explicitly instead of showing success")
    void shouldReportIncompleteCopy_whenLaterPersistFails() throws Exception {
        copyRequest();
        publicSource();
        favorite(7, "999997");
        favorite(8, "999997");
        request.setParameter("countFavorites", "2");
        request.setParameter("selected1", "on");
        request.setParameter("fldFavoriteId1", "8");
        java.util.concurrent.atomic.AtomicInteger writes = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(invocation -> {
            if (writes.incrementAndGet() == 2) throw new IllegalStateException("database unavailable");
            return null;
        }).when(favoritesDao).persist(any());

        assertThat(new CopyFavorites2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getErrorMessage()).contains("Some items may already have been copied");
        assertThat(request.getAttribute("copiedFavoritesCount")).isNull();
        verify(favoritesDao, times(2)).persist(any());
    }

    @Test
    @DisplayName("should report sharing persistence failure without confirming success")
    void shouldReportSharingFailure_whenPreferenceWriteFails() throws Exception {
        request.setMethod("POST");
        request.setParameter("dispatch", "update");
        request.setParameter("rb_share", "1");
        doThrow(new IllegalStateException("database unavailable")).when(privilegeDao)
                .setFavoritesPrivilege("999998", true, false);

        assertThat(new CopyFavorites2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getErrorMessage()).contains("not confirmed");
        verifyNoInteractions(favoritesDao);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"0", "-1", "invalid", "2147483648"})
    @DisplayName("should refuse malformed selected IDs before loading or copying favourites")
    void shouldRejectMalformedSelection_whenFavoriteIdIsInvalid(String id) throws Exception {
        copyRequest();
        publicSource();
        if (id == null) request.removeParameter("fldFavoriteId0");
        else request.setParameter("fldFavoriteId0", id);

        assertThat(new CopyFavorites2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(favoritesDao);
    }

    @Test
    @DisplayName("should reject a missing source sharing record before reading its favourites")
    void shouldRejectMissingSharing_whenSourceHasNoPrivilegeRecord() throws Exception {
        copyRequest();

        assertThat(new CopyFavorites2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(favoritesDao);
    }

    @Test
    @DisplayName("should refuse a session without an authenticated provider identity")
    void shouldRejectUnauthenticatedProvider_whenSessionIdentityIsMissing() throws Exception {
        copyRequest();
        when(LoggedInInfo.getLoggedInInfoFromSession(request).getLoggedInProviderNo()).thenReturn(null);

        assertThatThrownBy(() -> new CopyFavorites2Action().execute()).isInstanceOf(SecurityException.class);
        verifyNoInteractions(favoritesDao, privilegeDao);
    }

    @Test
    @DisplayName("should preserve self-copy without requiring public sharing")
    void shouldAllowOwnSource_whenFavoritesArePrivate() throws Exception {
        copyRequest();
        request.setParameter("ddl_provider", "999998");
        favorite(7, "999998");

        assertThat(new CopyFavorites2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
        verify(favoritesDao).persist(argThat(model -> model instanceof Favorites copy
                && "999998".equals(copy.getProviderNo())));
    }

    @Test
    @DisplayName("should refuse the complete batch when a later favourite lookup fails")
    void shouldAvoidPartialCopy_whenLookupFails() throws Exception {
        copyRequest();
        publicSource();
        favorite(7, "999997");
        request.setParameter("countFavorites", "2");
        request.setParameter("selected1", "on");
        request.setParameter("fldFavoriteId1", "8");
        when(favoritesDao.find((Object) Integer.valueOf(8))).thenThrow(new IllegalStateException("database unavailable"));

        assertThat(new CopyFavorites2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getErrorMessage()).contains("Nothing was copied");
        verify(favoritesDao, never()).persist(any());
    }

    @Test
    @DisplayName("should distinguish saved copies from a failed page reload")
    void shouldReportSavedCopies_whenRefreshAfterCopyFails() throws Exception {
        copyRequest();
        publicSource();
        favorite(7, "999997");
        when(privilegeDao.getProviders()).thenThrow(new IllegalStateException("database unavailable"));

        assertThat(new CopyFavorites2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getErrorMessage()).contains("Favorites were copied", "before copying again");
        assertThat(request.getAttribute("copiedFavoritesCount")).isEqualTo(1);
        verify(favoritesDao).persist(any());
    }

    @Test
    @DisplayName("should distinguish saved sharing preferences from a failed page reload")
    void shouldReportSavedSharing_whenRefreshAfterUpdateFails() throws Exception {
        request.setMethod("POST");
        request.setParameter("dispatch", "update");
        request.setParameter("rb_share", "1");
        when(privilegeDao.getProviders()).thenThrow(new IllegalStateException("database unavailable"));

        assertThat(new CopyFavorites2Action().execute()).isEqualTo(ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getErrorMessage()).contains("Favorite sharing was saved", "review the saved preference");
        verify(privilegeDao).setFavoritesPrivilege("999998", true, false);
        verifyNoInteractions(favoritesDao);
    }

}
