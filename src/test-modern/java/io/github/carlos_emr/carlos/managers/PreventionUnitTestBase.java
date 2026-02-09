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
import io.github.carlos_emr.carlos.commn.model.Prevention;
import io.github.carlos_emr.carlos.commn.model.PreventionExt;
import io.github.carlos_emr.carlos.test.unit.OpenOUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.mockito.Mockito;

import java.util.Date;

/**
 * Base class for Prevention-related unit tests providing common mocks and test data builders.
 *
 * @since 2026-02-09
 * @see OpenOUnitTestBase
 */
@Tag("unit")
@Tag("fast")
@Tag("prevention")
public abstract class PreventionUnitTestBase extends OpenOUnitTestBase {

    protected SecurityInfoManager mockSecurityInfoManager;
    protected LoggedInInfo mockLoggedInInfo;
    protected Facility mockFacility;

    protected static final Integer TEST_DEMO_NO = 12345;
    protected static final String TEST_PROVIDER = "999990";
    protected static final Integer TEST_PREVENTION_ID = 1;
    protected static final String TEST_PREVENTION_TYPE = "Flu";
    protected static final String TEST_PREVENTION_DATE = "2026-01-15";

    @BeforeEach
    void setUpPreventionMocks() {
        mockSecurityInfoManager = Mockito.mock(SecurityInfoManager.class);
        mockLoggedInInfo = Mockito.mock(LoggedInInfo.class);
        mockFacility = Mockito.mock(Facility.class);

        Mockito.lenient().when(mockLoggedInInfo.getCurrentFacility()).thenReturn(mockFacility);
        Mockito.lenient().when(mockFacility.isIntegratorEnabled()).thenReturn(false);
        Mockito.lenient().when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(TEST_PROVIDER);

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
    }

    /**
     * Creates a valid test Prevention with sensible immunization defaults.
     *
     * @return A valid Prevention instance for testing
     */
    protected Prevention createTestPrevention() {
        Prevention prevention = new Prevention();
        prevention.setDemographicId(TEST_DEMO_NO);
        prevention.setPreventionType(TEST_PREVENTION_TYPE);
        prevention.setPreventionDate(new Date());
        prevention.setProviderNo(TEST_PROVIDER);
        prevention.setCreatorProviderNo(TEST_PROVIDER);
        prevention.setDeleted(false);
        prevention.setRefused(false);
        prevention.setNever(false);
        prevention.setLastUpdateDate(new Date());
        return prevention;
    }

    /**
     * Creates a test Prevention with a specific ID.
     *
     * @param id The prevention ID
     * @return A Prevention instance with the specified ID
     */
    protected Prevention createTestPreventionWithId(Integer id) {
        Prevention prevention = createTestPrevention();
        injectId(prevention, id);
        return prevention;
    }

    /**
     * Creates a test PreventionExt with specified key/value.
     *
     * @param preventionId The prevention ID this extends
     * @param keyval The extension key
     * @param val The extension value
     * @return A PreventionExt instance
     */
    protected PreventionExt createTestPreventionExt(Integer preventionId, String keyval, String val) {
        PreventionExt ext = new PreventionExt();
        ext.setPreventionId(preventionId);
        ext.setKeyval(keyval);
        ext.setVal(val);
        return ext;
    }

    /**
     * Creates a refused prevention record.
     *
     * @return A refused Prevention instance
     */
    protected Prevention createRefusedPrevention() {
        Prevention prevention = createTestPrevention();
        prevention.setRefused(true);
        return prevention;
    }

    /**
     * Sets the ID field via reflection for entities with @GeneratedValue.
     */
    private void injectId(Prevention prevention, Integer id) {
        try {
            java.lang.reflect.Field field = Prevention.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(prevention, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set prevention ID via reflection", e);
        }
    }
}
