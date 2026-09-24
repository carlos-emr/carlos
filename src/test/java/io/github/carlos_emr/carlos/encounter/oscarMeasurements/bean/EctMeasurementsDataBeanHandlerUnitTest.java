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

package io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean;

import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.commn.model.MeasurementType;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Date;
import java.util.Hashtable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A reading with a NULL comment or observation date must still load for the add/edit measurement
 * page, eForm measurement prefill and renal dosing. {@code measurements.comments} and
 * {@code dateObserved} are nullable, and the legacy {@code Hashtable} result rejects null values,
 * so such a reading used to fail the whole page with a 500.
 *
 * @since 2026-09-24
 */
@Tag("unit")
@Tag("fast")
@Tag("measurement")
class EctMeasurementsDataBeanHandlerUnitTest extends CarlosUnitTestBase {

    private MeasurementDao measurementDao;

    @BeforeEach
    void setUp() {
        measurementDao = mock(MeasurementDao.class);
        registerMock(MeasurementDao.class, measurementDao);
    }

    private static Object[] row(String comments, Date observed) {
        Measurement m = new Measurement();
        m.setDataField("Yes");
        m.setComments(comments);
        m.setDateObserved(observed);
        m.setCreateDate(new Date());
        MeasurementType mt = new MeasurementType();
        mt.setTypeDisplayName("Asthma Action Plan");
        mt.setTypeDescription("Asthma Action Plan");
        Provider p = new Provider();
        p.setFirstName("Pat");
        p.setLastName("Tester");
        return new Object[] {m, mt, p};
    }

    @Test
    @DisplayName("should load a reading by id when its comment and observation date are null")
    void shouldLoadReadingById_whenCommentAndObservedDateAreNull() {
        when(measurementDao.findMeasurementsAndProviders(7))
                .thenReturn(Collections.singletonList(row(null, null)));

        Hashtable<String, Object> data = EctMeasurementsDataBeanHandler.getMeasurementDataById("7");

        assertThat(data.get("value")).isEqualTo("Yes");
        assertThat(data.get("comments")).isNull();
        assertThat(data.get("dateObserved_date")).isNull();
        assertThat(data.get("provider_last")).isEqualTo("Tester");
    }

    @Test
    @DisplayName("should keep every present field of the latest reading")
    void shouldKeepPresentFields_forLatestReading() {
        Date observed = new Date(0);
        Object[] r = row("reviewed", observed);
        // getLast's query returns (measurement, provider, type).
        when(measurementDao.findMeasurementsAndProvidersByDemoAndType(3, "AACP"))
                .thenReturn(new Object[] {r[0], r[2], r[1]});

        Hashtable<String, Object> data = EctMeasurementsDataBeanHandler.getLast("3", "AACP");

        assertThat(data.get("comments")).isEqualTo("reviewed");
        assertThat(data.get("dateObserved_date")).isEqualTo(observed);
        assertThat(data.get("typeDisplayName")).isEqualTo("Asthma Action Plan");
    }
}
