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
import io.github.carlos_emr.carlos.commn.dao.MeasurementsExtDao;
import io.github.carlos_emr.carlos.commn.dao.ValidationsDao;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.commn.model.MeasurementsExt;
import io.github.carlos_emr.carlos.commn.model.MeasurementType;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Validations;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.data.MeasurementTypes;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;

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
    private MeasurementsExtDao extensions;

    @BeforeEach
    void setUp() {
        measurementDao = mock(MeasurementDao.class);
        registerMock(MeasurementDao.class, measurementDao);
        extensions = mock(MeasurementsExtDao.class);
        registerMock(MeasurementsExtDao.class, extensions);
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

        assertThat(data).containsEntry("value", "Yes");
        assertThat(data.get("comments")).isNull();
        assertThat(data.get("dateObserved_date")).isNull();
        assertThat(data).containsEntry("provider_last", "Tester");
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

        assertThat(data).containsEntry("comments", "reviewed");
        assertThat(data).containsEntry("dateObserved_date", observed);
        assertThat(data).containsEntry("typeDisplayName", "Asthma Action Plan");
    }

    @Test
    void shouldLoadSourceLabsInOneBatch_withManualReadingsUnlinked() {
        Object[] lab = reading(7);
        Object[] manual = reading(8);
        when(measurementDao.findMeasurementsAndProvidersByType("A1C", 3)).thenReturn(List.of(lab, manual));
        when(extensions.getMeasurementsExtListByMeasurementIdList(anyList())).thenReturn(List.of(
                extension(7, "lab_no", "216"), extension(8, "unit", "%")));

        List<EctMeasurementsDataBean> readings = EctMeasurementsDataBeanHandler.getMeasurementObjectByType("A1C", 3);

        assertThat(readings).extracting(EctMeasurementsDataBean::getSourceLabNo).containsExactly(216, null);
        verify(extensions).getMeasurementsExtListByMeasurementIdList(argThat(ids -> ids.size() == 2 && ids.containsAll(List.of(7, 8))));
    }

    @Test
    void shouldIgnoreLabIdentities_whenInvalid() {
        when(measurementDao.findMeasurementsAndProvidersByType("A1C", 3))
                .thenReturn(Collections.singletonList(reading(7)));
        for (String value : new String[] {null, "", "0", "-1", "2147483648", "216&other=1", "<script>"}) {
            when(extensions.getMeasurementsExtListByMeasurementIdList(anyList()))
                    .thenReturn(List.of(extension(7, "lab_no", value)));
            assertThat(EctMeasurementsDataBeanHandler.getMeasurementObjectByType("A1C", 3).get(0).getSourceLabNo())
                    .as("invalid source lab %s", value).isNull();
        }
    }

    @Test
    void shouldNotQueryExtensions_whenHistoryIsEmpty() {
        assertThat(EctMeasurementsDataBeanHandler.getMeasurementObjectByType("A1C", 3)).isEmpty();
        verifyNoInteractions(extensions);
    }

    @Test
    void shouldIncludeSourceLab_inDisplayHistory() {
        MeasurementTypes catalog = mock(MeasurementTypes.class);
        EctMeasurementTypesBean type = mock(EctMeasurementTypesBean.class);
        when(catalog.getByType("A1C")).thenReturn(type);
        when(type.getValidation()).thenReturn("1");
        ValidationsDao validationsDao = mock(ValidationsDao.class);
        registerMock(ValidationsDao.class, validationsDao);
        ProviderDao providers = mock(ProviderDao.class);
        registerMock(ProviderDao.class, providers);
        Validations validation = new Validations();
        validation.setName("A1C");
        Object[] row = reading(7);
        when(validationsDao.findValidationsBy(3, "A1C", 1))
                .thenReturn(Collections.singletonList(new Object[] {validation, row[0]}));
        when(extensions.getMeasurementsExtListByMeasurementIdList(List.of(7)))
                .thenReturn(List.of(extension(7, "lab_no", "216")));

        try (var catalogAccess = mockStatic(MeasurementTypes.class)) {
            catalogAccess.when(MeasurementTypes::getInstance).thenReturn(catalog);
            assertThat(new EctMeasurementsDataBeanHandler(3, "A1C").getMeasurementsDataVector())
                    .extracting(EctMeasurementsDataBean::getSourceLabNo).containsExactly(216);
        }
    }

    private static Object[] reading(int id) {
        Object[] r = row("reviewed", new Date(0));
        Measurement measurement = (Measurement) r[0];
        ReflectionTestUtils.setField(measurement, "id", id);
        measurement.setDemographicId(3);
        measurement.setType("A1C");
        return r;
    }

    private static MeasurementsExt extension(int measurementId, String key, String value) {
        MeasurementsExt extension = new MeasurementsExt(measurementId);
        extension.setKeyVal(key);
        extension.setVal(value);
        return extension;
    }
}
