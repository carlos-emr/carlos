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
package io.github.carlos_emr.carlos.demographic.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.DemographicMergedDao;
import io.github.carlos_emr.carlos.commn.dao.RecycleBinDao;
import io.github.carlos_emr.carlos.commn.dao.SecObjPrivilegeDao;
import io.github.carlos_emr.carlos.commn.model.DemographicMerged;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.List;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A crafted merge POST must not be able to create a merge cycle (issue #3880): the action rejects the whole
 * request, before any row is written, when a selected record or the target is already merged into another.
 *
 * @since 2026-10-19
 */
@Tag("unit")
@Tag("demographic")
class DemographicMergeRecord2ActionMergeUnitTest extends CarlosUnitTestBase {
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final DemographicMergedDao dao = mock(DemographicMergedDao.class);
    private final LoggedInInfo info = mock(LoggedInInfo.class);

    @BeforeEach
    void setUp() {
        registerMock(SecurityInfoManager.class, security);
        registerMock(DemographicMergedDao.class, dao);
        registerMock(SecObjPrivilegeDao.class, mock(SecObjPrivilegeDao.class));
        registerMock(RecycleBinDao.class, mock(RecycleBinDao.class));
        when(info.getLoggedInProviderNo()).thenReturn("999998");
        when(security.hasPrivilege(info, "_demographic", "w", null)).thenReturn(true);
        request.setMethod("POST");
        request.setParameter("mergeAction", "merge");
    }

    private void alreadyMerged(int source, int head) {
        DemographicMerged row = new DemographicMerged();
        row.setDemographicNo(source);
        row.setMergedTo(head);
        when(dao.findCurrentByDemographicNo(source)).thenReturn(List.of(row));
    }

    private String merge(String head, String... records) {
        request.setParameter("head", head);
        request.setParameter("records", records);
        try (var servlet = org.mockito.Mockito.mockStatic(ServletActionContext.class);
             var loggedIn = org.mockito.Mockito.mockStatic(LoggedInInfo.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            loggedIn.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(info);
            return new DemographicMergeRecord2Action().execute();
        }
    }

    @Test
    void shouldMerge_whenAllRecordsAreHeads() {
        assertThat(merge("10", "10", "11", "12")).isEqualTo("success");

        verify(dao, times(2)).persist(any());
    }

    @Test
    void shouldRejectMerge_whenASelectedRecordIsAlreadyMerged() {
        alreadyMerged(11, 20);

        assertThat(merge("10", "10", "11")).isEqualTo("failure");

        verify(dao, never()).persist(any());
    }

    @Test
    void shouldRejectMerge_whenTheTargetIsAlreadyMergedIntoAnotherRecord() {
        alreadyMerged(10, 20);

        assertThat(merge("10", "10", "11")).isEqualTo("failure");

        verify(dao, never()).persist(any());
    }

    @Test
    void shouldRejectWholeRequest_beforeWritingAnything_whenLaterRecordIsMerged() {
        alreadyMerged(12, 20);

        assertThat(merge("10", "10", "11", "12")).isEqualTo("failure");

        verify(dao, never()).persist(any());
    }

    @Test
    void shouldRejectMerge_whenAnIdIsNotNumeric() {
        assertThat(merge("10", "10", "not-a-number")).isEqualTo("failure");

        verify(dao, never()).persist(any());
    }

    @Test
    void shouldCreateOneRow_whenARecordIsSubmittedTwice() {
        assertThat(merge("10", "10", "11", "11")).isEqualTo("success");

        verify(dao, times(1)).persist(any());
    }
}
