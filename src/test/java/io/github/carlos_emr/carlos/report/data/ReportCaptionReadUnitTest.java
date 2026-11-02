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
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Caption reads retain their legacy empty-caption fallback for database failures. */
@Tag("unit")
class ReportCaptionReadUnitTest extends CarlosUnitTestBase {
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
        assertThatThrownBy(() -> new RptTableFieldNameCaption().getNameCaptionProp("formAlpha"))
                .isInstanceOf(IllegalStateException.class);
    }
}
