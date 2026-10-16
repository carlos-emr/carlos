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

import io.github.carlos_emr.carlos.casemgmt.common.ChartSessionKeys;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A chart print of all notes, or of a date range, applies the note filters that the printed
 * patient's own chart last applied. Those filters used to be remembered once per login, so a
 * print of one patient silently left out the notes that another chart window had filtered away
 * (issue #4673).
 *
 * @since 2026-10-16
 */
@Tag("unit")
@Tag("casemgmt")
@DisplayName("Chart print note filters")
class CaseManagementPrintNoteFilterUnitTest extends CarlosUnitTestBase {
    private MockHttpSession session;

    @BeforeEach
    void prepare() {
        session = new MockHttpSession();
        // Patient B's chart, in another window, filtered its notes down to one provider and issue.
        session.setAttribute(ChartSessionKeys.appliedNoteFilter("roles", "202"), List.of("9"));
        session.setAttribute(ChartSessionKeys.appliedNoteFilter("providers", "202"), List.of("777"));
        session.setAttribute(ChartSessionKeys.appliedNoteFilter("issues", "202"), List.of("99"));
        // What the print used to read: the filters last applied in any window.
        session.setAttribute("CaseManagementViewAction_filter_providers", List.of("777"));
    }

    @Test
    void shouldApplyNoFilters_whenOnlyAnotherChartFilteredItsNotes() {
        NoteSelectionCriteria criteria = new NoteSelectionCriteria();

        CaseManagementPrint.applyThisChartsNoteFilters(session, "101", criteria);

        assertThat(criteria.getRoles()).isEmpty();
        assertThat(criteria.getProviders()).isEmpty();
        assertThat(criteria.getIssues()).isEmpty();
    }

    @Test
    void shouldApplyThePrintedPatientsOwnFilters_whenBothChartsFilteredTheirNotes() {
        session.setAttribute(ChartSessionKeys.appliedNoteFilter("roles", "101"), List.of("1"));
        session.setAttribute(ChartSessionKeys.appliedNoteFilter("providers", "101"), List.of("4249"));
        session.setAttribute(ChartSessionKeys.appliedNoteFilter("issues", "101"), List.of("73"));
        NoteSelectionCriteria criteria = new NoteSelectionCriteria();

        CaseManagementPrint.applyThisChartsNoteFilters(session, "101", criteria);

        assertThat(criteria.getRoles()).containsExactly("1");
        assertThat(criteria.getProviders()).containsExactly("4249");
        assertThat(criteria.getIssues()).containsExactly("73");
    }

    @Test
    void shouldApplyNoFilters_whenThePrintedPatientIsNotAPatientNumber() {
        NoteSelectionCriteria criteria = new NoteSelectionCriteria();

        CaseManagementPrint.applyThisChartsNoteFilters(session, "202abc", criteria);

        assertThat(criteria.getRoles()).isEmpty();
        assertThat(criteria.getProviders()).isEmpty();
        assertThat(criteria.getIssues()).isEmpty();
    }
}
