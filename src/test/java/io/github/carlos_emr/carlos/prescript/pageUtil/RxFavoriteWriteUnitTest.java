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
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
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
    private Favorite stored;
    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("POST", "/rx/updateFavorite2");
        response = new MockHttpServletResponse();
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
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
}
