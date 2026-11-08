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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementIssueDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.CdsExportNoteAccessDao;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** Both historical web bean names remain installed; export binds to the canonical owners. */
@Tag("unit") @Tag("cds-export")
class CdsExportLegacyDaoWiringUnitTest {
    @Test void shouldUseCanonicalOwners_whenLegacyWebContextContainsDuplicateDaoTypes() throws Exception {
        var issues = mock(CaseManagementIssueDAO.class);
        var oldIssues = mock(CaseManagementIssueDAO.class);
        var notes = mock(CaseManagementNoteDAO.class);
        var oldNotes = mock(CaseManagementNoteDAO.class);
        var scopes = mock(CdsExportNoteAccessDao.class);
        var projection = mock(CdsExportNoteScopeProjectionService.class);
        when(issues.countCdsExportRows(27)).thenReturn(3L);
        when(notes.countCdsExportIssueLinks(27)).thenReturn(4L);
        when(projection.read(27, 8192)).thenReturn(List.of());
        when(scopes.readScopes(27, 8192)).thenReturn(List.of());
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean("caseManagementIssueDAO", CaseManagementIssueDAO.class, () -> issues);
            context.registerBean("CaseManagementIssueDAO", CaseManagementIssueDAO.class, () -> oldIssues);
            context.registerBean("caseManagementNoteDAO", CaseManagementNoteDAO.class, () -> notes);
            context.registerBean("CaseManagementNoteDAO", CaseManagementNoteDAO.class, () -> oldNotes);
            context.registerBean(CdsExportNoteAccessDao.class, () -> scopes);
            context.registerBean(CdsExportNoteScopeProjectionService.class, () -> projection);
            context.register(CdsExportCaseIssueSourceReader.class, CdsExportNoteSourceReader.class,
                    CdsExportNoteIssueLinkSourceReader.class);
            context.refresh();
            assertThat(context.getBean(CdsExportCaseIssueSourceReader.class).count(27)).isEqualTo(3);
            assertThat(context.getBean(CdsExportNoteIssueLinkSourceReader.class).count(27)).isEqualTo(4);
            var prepared = context.getBean(CdsExportNoteSourceReader.class)
                    .forCapture(27, mock(CdsExportRunControl.class));
            assertThat(prepared.count(27)).isZero();
            verify(issues).countCdsExportRows(27);
            verify(notes).countCdsExportIssueLinks(27);
            verify(notes).countCdsExportRows(27);
            verifyNoInteractions(oldIssues, oldNotes);
        }
    }
}
