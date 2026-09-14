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
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;

import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins provider ownership on the Rx favorite delete, update, and use flows.
 *
 * <p>Favorites are a per-provider list, but these actions take a bare {@code favoriteId} request
 * parameter. Each case drives an action with an id owned by a different provider and asserts the
 * request is rejected with {@code 403} before the DAO is asked to mutate anything.
 *
 * @since 2026-05-29
 */
@DisplayName("Rx favorite ownership action tests")
@Tag("unit")
@Tag("prescript")
@Tag("security")
class RxFavoriteOwnershipActionUnitTest extends CarlosWebTestBase {

    private static final String SESSION_PROVIDER = "providerA";
    private static final String OTHER_PROVIDER = "providerB";
    private static final int FAVORITE_ID = 123;

    @Mock
    private FavoriteDao mockFavoriteDao;

    @BeforeEach
    void setUpFavoriteDao() {
        replaceSpringUtilsBean(FavoriteDao.class, mockFavoriteDao);
        setSessionAttribute("user", SESSION_PROVIDER);
    }

    @Nested
    @DisplayName("RxDeleteFavorite2Action")
    class DeleteAction {

        @Test
        @DisplayName("should return forbidden when favorite belongs to another provider")
        void shouldReturnForbidden_whenFavoriteBelongsToAnotherProvider() throws Exception {
            when(mockFavoriteDao.find(FAVORITE_ID)).thenReturn(favoriteOwnedBy(OTHER_PROVIDER));

            RxDeleteFavorite2Action action = new RxDeleteFavorite2Action();
            action.setFavoriteId(String.valueOf(FAVORITE_ID));

            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(getMockResponse().getStatus()).isEqualTo(403);
            verify(mockFavoriteDao).find(FAVORITE_ID);
            verify(mockFavoriteDao, never()).remove(anyInt());
            verifyNoMoreInteractions(mockFavoriteDao);
        }

        @Test
        @DisplayName("should delete when favorite belongs to the session provider")
        void shouldDelete_whenFavoriteBelongsToSessionProvider() throws Exception {
            when(mockFavoriteDao.find(FAVORITE_ID)).thenReturn(favoriteOwnedBy(SESSION_PROVIDER));
            when(mockFavoriteDao.remove(FAVORITE_ID)).thenReturn(true);

            RxDeleteFavorite2Action action = new RxDeleteFavorite2Action();
            action.setFavoriteId(String.valueOf(FAVORITE_ID));

            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            assertThat(getMockResponse().getStatus()).isEqualTo(200);
            verify(mockFavoriteDao).remove(FAVORITE_ID);
        }
    }

    @Nested
    @DisplayName("RxUpdateFavorite2Action")
    class UpdateAction {

        @Test
        @DisplayName("should return forbidden for Struts field update when favorite belongs to another provider")
        void shouldReturnForbiddenForStrutsFieldUpdate_whenFavoriteBelongsToAnotherProvider() throws Exception {
            when(mockFavoriteDao.find(FAVORITE_ID)).thenReturn(favoriteOwnedBy(OTHER_PROVIDER));

            RxUpdateFavorite2Action action = new RxUpdateFavorite2Action();
            action.setFavoriteId(String.valueOf(FAVORITE_ID));

            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(getMockResponse().getStatus()).isEqualTo(403);
            verify(mockFavoriteDao).find(FAVORITE_ID);
            verifyNoSave();
        }

        @Test
        @DisplayName("should return forbidden for ajax update when favorite belongs to another provider")
        void shouldReturnForbiddenForAjaxUpdate_whenFavoriteBelongsToAnotherProvider() throws Exception {
            when(mockFavoriteDao.find(FAVORITE_ID)).thenReturn(favoriteOwnedBy(OTHER_PROVIDER));
            addRequestParameter("favoriteId", String.valueOf(FAVORITE_ID));

            RxUpdateFavorite2Action action = new RxUpdateFavorite2Action();

            String result = executeActionMethod(action, "ajaxEditFavorite");

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(getMockResponse().getStatus()).isEqualTo(403);
            verify(mockFavoriteDao).find(FAVORITE_ID);
            verifyNoSave();
        }
    }

    @Nested
    @DisplayName("RxUseFavorite2Action")
    class UseAction {

        @BeforeEach
        void setUpRxSessionBean() {
            setSessionAttribute("RxSessionBean", mock(RxSessionBean.class));
        }

        @Test
        @DisplayName("should return forbidden for standard use when favorite belongs to another provider")
        void shouldReturnForbiddenForStandardUse_whenFavoriteBelongsToAnotherProvider() throws Exception {
            when(mockFavoriteDao.find(FAVORITE_ID)).thenReturn(favoriteOwnedBy(OTHER_PROVIDER));

            RxUseFavorite2Action action = new RxUseFavorite2Action();
            action.setFavoriteId(String.valueOf(FAVORITE_ID));

            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(getMockResponse().getStatus()).isEqualTo(403);
            verify(mockFavoriteDao).find(FAVORITE_ID);
            verifyNoSave();
        }

        @Test
        @DisplayName("should return forbidden for useFav2 when favorite belongs to another provider")
        void shouldReturnForbiddenForUseFav2_whenFavoriteBelongsToAnotherProvider() throws Exception {
            when(mockFavoriteDao.find(FAVORITE_ID)).thenReturn(favoriteOwnedBy(OTHER_PROVIDER));
            addRequestParameter("favoriteId", String.valueOf(FAVORITE_ID));

            RxUseFavorite2Action action = new RxUseFavorite2Action();

            String result = executeActionMethod(action, "useFav2");

            assertThat(result).isEqualTo(ActionSupport.NONE);
            assertThat(getMockResponse().getStatus()).isEqualTo(403);
            verify(mockFavoriteDao).find(FAVORITE_ID);
            verifyNoSave();
        }
    }

    private void verifyNoSave() {
        verify(mockFavoriteDao, never()).merge(any());
        verify(mockFavoriteDao, never()).persist(any());
    }

    private Favorite favoriteOwnedBy(String providerNo) {
        Favorite favorite = new Favorite();
        favorite.setId(FAVORITE_ID);
        favorite.setProviderNo(providerNo);
        return favorite;
    }
}
