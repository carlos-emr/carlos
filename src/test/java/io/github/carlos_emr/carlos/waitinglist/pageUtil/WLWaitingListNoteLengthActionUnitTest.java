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
package io.github.carlos_emr.carlos.waitinglist.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockitoAnnotations;

import io.github.carlos_emr.carlos.commn.dao.WaitingListDao;
import io.github.carlos_emr.carlos.commn.model.ProviderPreference;
import io.github.carlos_emr.carlos.commn.model.WaitingList;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.providers.bean.ProviderNameBeanHandler;
import io.github.carlos_emr.carlos.providers.data.ProviderData;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SessionConstants;
import io.github.carlos_emr.carlos.waitinglist.WaitingListAvailabilityService;
import io.github.carlos_emr.carlos.waitinglist.bean.WLWaitingListBeanHandler;
import io.github.carlos_emr.carlos.waitinglist.bean.WLWaitingListNameBeanHandler;

/**
 * #6141: the two waiting-list writers the Master Record save does not reach refuse a note longer
 * than {@code waitingList.note} holds, visibly and before any row changes. The column used to cut
 * such a note with only a warning while the page reported success.
 *
 * @since 2026-11-11
 */
@DisplayName("Waiting-list note length on the waiting-list page and Add to Waiting List")
@Tag("unit")
@Tag("web")
@Tag("waitinglist")
class WLWaitingListNoteLengthActionUnitTest extends CarlosWebTestBase {

    private static final String TOO_LONG = "N".repeat(WaitingList.NOTE_MAX_LENGTH + 1);
    private static final String LONGEST = "𝐀".repeat(WaitingList.NOTE_MAX_LENGTH);
    private static final String REFUSAL = "Waiting list note exceeds maximum length of "
            + WaitingList.NOTE_MAX_LENGTH + " characters.";

    @Mock
    private WaitingListDao mockWaitingListDao;

    @Mock
    private WaitingListAvailabilityService mockAvailability;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);
        replaceSpringUtilsBean(WaitingListDao.class, mockWaitingListDao);
        replaceSpringUtilsBean(WaitingListAvailabilityService.class, mockAvailability);
        setSessionAttribute(LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY", mockLoggedInInfo);
        setSessionAttribute("user", "999998");
        ProviderPreference preference = new ProviderPreference();
        preference.setMyGroupNo("FAKE-GROUP");
        setSessionAttribute(SessionConstants.LOGGED_IN_PROVIDER_PREFERENCE, preference);
        allowPrivilege("_demographic", "r");
        allowPrivilege("_demographic", "w");
        getMockRequest().setMethod("POST");
    }

    @Nested
    @DisplayName("a direct POST to Add to Waiting List")
    class AddToWaitingList {

        private WLAdd2WaitingList2Action action;

        @BeforeEach
        void build() {
            when(mockAvailability.isSelectable(any(), eq(7))).thenReturn(true);
            action = new WLAdd2WaitingList2Action();
            injectField(action, "securityInfoManager", mockSecurityInfoManager);
            addRequestParameter("listId", "7");
            addRequestParameter("demographicNo", "42");
            addRequestParameter("onListSince", "2026-11-11");
        }

        @Test
        void shouldRefuseALongNoteWithBadRequest_andWriteNoRow() throws Exception {
            addRequestParameter("waitingListNote", TOO_LONG);

            assertThat(executeAction(action)).isEqualTo(ActionSupport.NONE);

            assertThat(getMockResponse().getStatus()).isEqualTo(400);
            assertThat(getMockResponse().getErrorMessage()).isEqualTo(REFUSAL);
            verifyNoInteractions(mockWaitingListDao);
        }

        @Test
        void shouldAddTheNoteWhole_whenItFillsTheColumn() throws Exception {
            addRequestParameter("waitingListNote", LONGEST);

            executeAction(action);

            ArgumentCaptor<WaitingList> saved = ArgumentCaptor.forClass(WaitingList.class);
            verify(mockWaitingListDao).persist(saved.capture());
            assertThat(saved.getValue().getNote()).isEqualTo(LONGEST);
            assertThat(getMockResponse().getRedirectedUrl()).contains("/demographic/DemographicEdit");
        }
    }

    @Nested
    @DisplayName("the waiting-list page's own note box")
    class WaitingListPage {

        private void postRowEdit(String note) {
            addRequestParameter("update", "Y");
            addRequestParameter("waitingListId", "7");
            addRequestParameter("demographicNumSelected", "waitingListBean[0].demographicNo");
            addRequestParameter("wlNoteSelected", "waitingListBean[0].note");
            addRequestParameter("onListSinceSelected", "waitingListBean[0].onListSince");
            addRequestParameter("waitingListBean[0].demographicNo", "42");
            addRequestParameter("waitingListBean[0].note", note);
            addRequestParameter("waitingListBean[0].onListSince", "2026-11-11");
        }

        /** Runs the page action with the list, name and provider lookups that render the page stubbed. */
        private String runPage() throws Exception {
            try (MockedConstruction<WLWaitingListBeanHandler> list = mockConstruction(WLWaitingListBeanHandler.class);
                 MockedConstruction<WLWaitingListNameBeanHandler> names = mockConstruction(WLWaitingListNameBeanHandler.class);
                 MockedConstruction<ProviderNameBeanHandler> providers = mockConstruction(ProviderNameBeanHandler.class);
                 MockedConstruction<ProviderData> provider = mockConstruction(ProviderData.class)) {
                WLSetupDisplayWaitingList2Action action = new WLSetupDisplayWaitingList2Action();
                injectField(action, "securityInfoManager", mockSecurityInfoManager);
                return executeAction(action);
            }
        }

        @Test
        void shouldRefuseALongNoteOnThePage_andChangeNoRow() throws Exception {
            postRowEdit(TOO_LONG);

            String result = runPage();

            // The page is shown again, saying why; nothing on the list was read or written.
            assertThat(result).isEqualTo("continue");
            assertThat(getMockResponse().getStatus()).isEqualTo(400);
            assertThat(getMockRequest().getAttribute(WLSetupDisplayWaitingList2Action.NOTE_ERRORS))
                    .isEqualTo(List.of(REFUSAL));
            verifyNoInteractions(mockWaitingListDao);
        }

        @Test
        void shouldSaveTheNoteWhole_whenItFillsTheColumn() throws Exception {
            WaitingList current = new WaitingList();
            current.setPosition(3);
            when(mockWaitingListDao.findByWaitingListIdAndDemographicId(7, 42)).thenReturn(List.of(current));
            postRowEdit(LONGEST);

            String result = runPage();

            assertThat(result).isEqualTo("continue");
            assertThat(getMockRequest().getAttribute(WLSetupDisplayWaitingList2Action.NOTE_ERRORS)).isNull();
            ArgumentCaptor<WaitingList> saved = ArgumentCaptor.forClass(WaitingList.class);
            verify(mockWaitingListDao).saveEntity(saved.capture());
            assertThat(saved.getValue().getNote()).isEqualTo(LONGEST);
        }
    }
}
