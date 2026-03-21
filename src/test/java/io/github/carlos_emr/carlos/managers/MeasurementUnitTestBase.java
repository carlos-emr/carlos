/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.commn.model.MeasurementMap;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.mockito.Mockito;

import java.util.Date;

/**
 * Base class for Measurement-related unit tests providing common mocks and test data builders.
 *
 * @since 2026-02-09
 * @see CarlosUnitTestBase
 */
@Tag("unit")
@Tag("fast")
@Tag("measurement")
public abstract class MeasurementUnitTestBase extends CarlosUnitTestBase {

    protected SecurityInfoManager mockSecurityInfoManager;
    protected LoggedInInfo mockLoggedInInfo;
    protected Facility mockFacility;

    protected static final Integer TEST_DEMO_NO = 12345;
    protected static final String TEST_PROVIDER = "999990";
    protected static final Integer TEST_MEASUREMENT_ID = 1;
    protected static final String TEST_TYPE_BP = "BP";
    protected static final String TEST_TYPE_WT = "WT";
    protected static final String TEST_TYPE_HT = "HT";

    @BeforeEach
    void setUpMeasurementMocks() {
        mockSecurityInfoManager = Mockito.mock(SecurityInfoManager.class);
        mockLoggedInInfo = Mockito.mock(LoggedInInfo.class);
        mockFacility = Mockito.mock(Facility.class);

        Mockito.lenient().when(mockLoggedInInfo.getCurrentFacility()).thenReturn(mockFacility);
        Mockito.lenient().when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(TEST_PROVIDER);

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
    }

    /**
     * Creates a valid test Measurement with blood pressure defaults.
     *
     * @return A valid Measurement instance for testing
     */
    protected Measurement createTestMeasurement() {
        return createTestMeasurement(TEST_TYPE_BP, "120/80");
    }

    /**
     * Creates a test Measurement with specified type and value.
     *
     * @param type The measurement type (e.g., "BP", "WT", "HT")
     * @param dataField The measurement value
     * @return A Measurement instance
     */
    protected Measurement createTestMeasurement(String type, String dataField) {
        Measurement measurement = new Measurement();
        measurement.setDemographicId(TEST_DEMO_NO);
        measurement.setType(type);
        measurement.setDataField(dataField);
        measurement.setProviderNo(TEST_PROVIDER);
        measurement.setDateObserved(new Date());
        measurement.setCreateDate(new Date());
        return measurement;
    }

    /**
     * Creates a test Measurement with a specific ID.
     *
     * @param id The measurement ID
     * @return A Measurement instance with the specified ID
     */
    protected Measurement createTestMeasurementWithId(Integer id) {
        Measurement measurement = createTestMeasurement();
        injectId(measurement, id);
        return measurement;
    }

    /**
     * Creates a test MeasurementMap.
     *
     * @param identCode The identifier code
     * @param loincCode The LOINC code
     * @param name The display name
     * @return A MeasurementMap instance
     */
    protected MeasurementMap createTestMeasurementMap(String identCode, String loincCode, String name) {
        MeasurementMap map = new MeasurementMap();
        map.setIdentCode(identCode);
        map.setLoincCode(loincCode);
        map.setName(name);
        return map;
    }

    private void injectId(Measurement measurement, Integer id) {
        try {
            java.lang.reflect.Field field = Measurement.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(measurement, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set measurement ID via reflection", e);
        }
    }
}
