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
package io.github.carlos_emr.carlos.casemgmt.service;

import io.github.carlos_emr.carlos.PMmodule.dao.ProgramProviderDAO;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Issue searches retain the program DAO's positive Long identifier range.
 * @since 2026-10-24
 */
@Tag("unit")
@Tag("casemgmt")
@DisplayName("Issue search program identifier boundaries")
class CaseManagementIssueProgramIdUnitTest extends CarlosUnitTestBase {
    private ProgramProviderDAO programs;
    private CaseManagementManagerImpl manager;
    private List<CaseManagementIssue> issues;

    @BeforeEach
    void setUp() {
        programs = mock(ProgramProviderDAO.class);
        manager = new CaseManagementManagerImpl();
        manager.setProgramProviderDao(programs);
        issues = List.of(mock(CaseManagementIssue.class));
    }

    @ParameterizedTest
    @ValueSource(longs = {1L, 1_000_000_000L, Long.MAX_VALUE})
    void shouldQueryProgramMembership_whenIdentifierIsAnyPositiveLong(long program) {
        when(programs.getProgramProviderByProviderProgramId("999998", program)).thenReturn(List.of());
        String id = Long.toString(program);

        assertThat(manager.searchIssuesCount("999998", id, "headache")).isZero();
        assertThat(manager.searchIssues("999998", id, "headache")).isEmpty();
        assertThat(manager.filterIssues(null, "999998", issues, id)).isEmpty();

        verify(programs, times(3)).getProgramProviderByProviderProgramId("999998", program);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"0", "-1", "invalid", "9223372036854775808"})
    void shouldReturnNoIssues_whenProgramIdentifierIsInvalid(String program) {
        assertThat(manager.searchIssuesCount("999998", program, "headache")).isZero();
        assertThat(manager.searchIssues("999998", program, "headache")).isEmpty();
        assertThat(manager.filterIssues(null, "999998", issues, program)).isEmpty();
        verifyNoInteractions(programs);
    }
}
