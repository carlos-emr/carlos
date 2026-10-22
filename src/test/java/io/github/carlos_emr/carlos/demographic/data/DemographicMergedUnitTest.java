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
package io.github.carlos_emr.carlos.demographic.data;

import io.github.carlos_emr.carlos.commn.dao.DemographicMergedDao;
import io.github.carlos_emr.carlos.commn.dao.RecycleBinDao;
import io.github.carlos_emr.carlos.commn.dao.SecObjPrivilegeDao;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The {@code demographic_merged} chain must resolve to a head without recursing forever and must refuse
 * merges that would close a loop (issue #3880).
 *
 * @since 2026-10-19
 */
@Tag("unit")
@Tag("demographic")
class DemographicMergedUnitTest extends CarlosUnitTestBase {
    private final DemographicMergedDao dao = mock(DemographicMergedDao.class);
    private final LoggedInInfo info = mock(LoggedInInfo.class);
    private DemographicMerged merged;

    @BeforeEach
    void setUp() {
        registerMock(DemographicMergedDao.class, dao);
        registerMock(SecObjPrivilegeDao.class, mock(SecObjPrivilegeDao.class));
        registerMock(RecycleBinDao.class, mock(RecycleBinDao.class));
        when(info.getLoggedInProviderNo()).thenReturn("999998");
        merged = new DemographicMerged();
    }

    private void link(int source, int head) {
        var row = new io.github.carlos_emr.carlos.commn.model.DemographicMerged();
        row.setDemographicNo(source);
        row.setMergedTo(head);
        when(dao.findCurrentByDemographicNo(source)).thenReturn(List.of(row));
    }

    @Test
    void shouldReturnTheRecord_whenItIsNotMerged() {
        assertThat(merged.getHead(10)).isEqualTo(10);
    }

    @Test
    void shouldFollowTheChain_toItsHead() {
        link(1, 2);
        link(2, 3);

        assertThat(merged.getHead(1)).isEqualTo(3);
        assertThat(merged.getHead("1")).isEqualTo("3");
    }

    @Test
    void shouldReturnTheStartingRecord_whenTheChainIsACycle() {
        link(1, 2);
        link(2, 1);

        assertThat(merged.getHead(1)).isEqualTo(1);
        assertThat(merged.getHead(2)).isEqualTo(2);
    }

    @Test
    void shouldFindNoHead_whenTheChainIsACycle() {
        link(1, 2);
        link(2, 1);
        link(3, 4);

        assertThat(merged.findHead(1)).isNull();
        assertThat(merged.findHead(2)).isNull();
        assertThat(merged.findHead(3)).isEqualTo(4);
    }

    @Test
    void shouldReturnTheStartingRecord_whenARecordIsMergedIntoItself() {
        link(7, 7);

        assertThat(merged.getHead(7)).isEqualTo(7);
    }

    @Test
    void shouldReportMergedSource_onlyForRecordsWithAnActiveMerge() {
        link(1, 2);

        assertThat(merged.isMergedSource(1)).isTrue();
        assertThat(merged.isMergedSource(2)).isFalse();
    }

    @Test
    void shouldRefuseMerge_whenTargetIsAMergedSourceOfTheRecord() {
        link(1, 2);

        assertThatThrownBy(() -> merged.Merge(info, "2", "1")).isInstanceOf(IllegalArgumentException.class);
        verify(dao, never()).persist(any());
    }

    @Test
    void shouldRefuseMerge_whenRecordIsMergedIntoItself() {
        assertThatThrownBy(() -> merged.Merge(info, "5", "5")).isInstanceOf(IllegalArgumentException.class);
        verify(dao, never()).persist(any());
    }

    @Test
    void shouldPersistMerge_whenNoCycleResults() {
        merged.Merge(info, "5", "6");

        verify(dao).persist(any());
    }
}
