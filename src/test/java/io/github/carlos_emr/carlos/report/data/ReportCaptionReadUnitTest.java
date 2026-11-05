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
package io.github.carlos_emr.carlos.report.data;

import io.github.carlos_emr.carlos.commn.dao.ReportTableFieldCaptionDao;
import io.github.carlos_emr.carlos.commn.dao.EncounterFormDao;
import io.github.carlos_emr.carlos.commn.model.EncounterForm;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Caption fallback remains compatible; field-discovery failures must not become empty exports. */
@Tag("unit")
class ReportCaptionReadUnitTest extends CarlosUnitTestBase {
    @BeforeEach
    @AfterEach
    void resetEncounterFormDao() {
        org.springframework.test.util.ReflectionTestUtils.setField(RptTableFieldNameCaption.class, "encounterFormDao", null);
    }

    @Test
    void shouldRetainEmptyCaptions_whenDatabaseReadFails() {
        var dao = mock(ReportTableFieldCaptionDao.class);
        registerMock(ReportTableFieldCaptionDao.class, dao);
        when(dao.findByTableName("formAlpha")).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("synthetic failure"));
        assertThat(new RptTableFieldNameCaption().getNameCaptionProp("formAlpha")).isEmpty();
    }

    @Test
    void shouldSurfaceProgrammingErrors_whenCaptionReadIsMisconfigured() {
        var dao = mock(ReportTableFieldCaptionDao.class);
        registerMock(ReportTableFieldCaptionDao.class, dao);
        when(dao.findByTableName("formAlpha")).thenThrow(new IllegalStateException("synthetic wiring failure"));
        var captions = new RptTableFieldNameCaption();
        assertThatThrownBy(() -> captions.getNameCaptionProp("formAlpha"))
                .isInstanceOf(IllegalStateException.class);
    }
    @Test
    void shouldDiscoverColumnsWithoutSelectingPatientRows_forAllowlistedForms() {
        var dao = mock(EncounterFormDao.class);
        registerMock(EncounterFormDao.class, dao);
        var form = new EncounterForm();
        form.setFormTable("formAlpha");
        when(dao.findAll()).thenReturn(java.util.List.of(form));
        try (var queries = mockStatic(ConfiguredReportQuery.class)) {
            queries.when(() -> ConfiguredReportQuery.columnNames("select * from formAlpha where 1=0"))
                    .thenReturn(java.util.List.of("demographic_no", "large_value"));
            assertThat(new RptTableFieldNameCaption().getMetaNameList("formAlpha"))
                    .containsExactly("demographic_no", "large_value");
        }
    }

    @Test
    void shouldAbortFieldDiscovery_whenMetadataQueryFails() {
        var dao = mock(EncounterFormDao.class);
        registerMock(EncounterFormDao.class, dao);
        var form = new EncounterForm();
        form.setFormTable("formAlpha");
        when(dao.findAll()).thenReturn(java.util.List.of(form));
        try (var queries = mockStatic(ConfiguredReportQuery.class)) {
            queries.when(() -> ConfiguredReportQuery.columnNames("select * from formAlpha where 1=0"))
                    .thenThrow(new java.sql.SQLException("synthetic metadata failure"));
            var captions = new RptTableFieldNameCaption();
            assertThatThrownBy(() -> captions.getMetaNameList("formAlpha"))
                    .isInstanceOf(IllegalStateException.class).hasCauseInstanceOf(java.sql.SQLException.class);
        }
    }

    @Test
    void shouldRejectUnregisteredTables_withoutMetadataQueries() {
        var dao = mock(EncounterFormDao.class);
        registerMock(EncounterFormDao.class, dao);
        when(dao.findAll()).thenReturn(java.util.List.of());
        try (var queries = mockStatic(ConfiguredReportQuery.class)) {
            assertThat(new RptTableFieldNameCaption().getMetaNameList("formUnknown")).isEmpty();
            queries.verifyNoInteractions();
        }
    }

}
