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

package io.github.carlos_emr.carlos.report.oscarMeasurements.data;

import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import java.util.Date;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Distinct patient IDs are scalar results, not counts or tuple rows.
 * @since 2026-10-05
 */
@Tag("unit")
class RptMeasurementsDataUnitTest extends CarlosUnitTestBase {
    @Test
    void countsPatientsInsteadOfReturningTheLastId() {
        MeasurementDao dao = createAndRegisterMock(MeasurementDao.class);
        when(dao.findByCreateDate(any(Date.class), any(Date.class))).thenReturn(List.of(101, 4320));
        assertThat(new RptMeasurementsData().getNbPatientSeen("2026-01-01", "2026-10-05")).isEqualTo(2);
    }

    @Test
    void convertsScalarIdsForFrequencyQueries() {
        MeasurementDao dao = createAndRegisterMock(MeasurementDao.class);
        when(dao.findByCreateDate(any(Date.class), any(Date.class))).thenReturn(List.of(101, 4320));
        assertThat(new RptMeasurementsData().getPatientsSeen("2026-01-01", "2026-10-05"))
                .containsExactly("101", "4320");
    }

    @Test
    void handlesAnEmptyReportingWindow() {
        createAndRegisterMock(MeasurementDao.class);
        RptMeasurementsData data = new RptMeasurementsData();
        assertThat(data.getNbPatientSeen("2026-01-01", "2026-10-05")).isZero();
        assertThat(data.getPatientsSeen("2026-01-01", "2026-10-05")).isEmpty();
    }
}
