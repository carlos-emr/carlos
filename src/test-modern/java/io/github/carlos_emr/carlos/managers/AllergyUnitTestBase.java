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

import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.test.unit.OpenOUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.mockito.Mockito;

import java.util.Date;

/**
 * Base class for Allergy-related unit tests providing common mocks and test data builders.
 *
 * <p>Extends OpenOUnitTestBase and adds Allergy-specific test infrastructure
 * including commonly used mocks, helper methods, and test data builders.</p>
 *
 * @since 2026-02-09
 * @see OpenOUnitTestBase
 */
@Tag("unit")
@Tag("fast")
@Tag("allergy")
public abstract class AllergyUnitTestBase extends OpenOUnitTestBase {

    protected SecurityInfoManager mockSecurityInfoManager;
    protected LoggedInInfo mockLoggedInInfo;
    protected Facility mockFacility;

    protected static final Integer TEST_DEMO_NO = 12345;
    protected static final String TEST_PROVIDER = "999990";
    protected static final Integer TEST_ALLERGY_ID = 1;
    protected static final String TEST_DESCRIPTION = "Penicillin";
    protected static final Integer TEST_TYPE_CODE = 13; // drug allergy

    @BeforeEach
    void setUpAllergyMocks() {
        mockSecurityInfoManager = Mockito.mock(SecurityInfoManager.class);
        mockLoggedInInfo = Mockito.mock(LoggedInInfo.class);
        mockFacility = Mockito.mock(Facility.class);

        Mockito.lenient().when(mockLoggedInInfo.getCurrentFacility()).thenReturn(mockFacility);
        Mockito.lenient().when(mockFacility.isIntegratorEnabled()).thenReturn(false);
        Mockito.lenient().when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(TEST_PROVIDER);

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
    }

    /**
     * Creates a valid test Allergy with sensible clinical defaults.
     *
     * @return A valid Allergy instance for testing
     */
    protected Allergy createTestAllergy() {
        Allergy allergy = new Allergy();
        allergy.setDemographicNo(TEST_DEMO_NO);
        allergy.setDescription(TEST_DESCRIPTION);
        allergy.setTypeCode(TEST_TYPE_CODE);
        allergy.setArchived(false);
        allergy.setSeverityOfReaction("1"); // mild
        allergy.setStartDate(new Date());
        allergy.setLastUpdateDate(new Date());
        allergy.setProviderNo(TEST_PROVIDER);
        return allergy;
    }

    /**
     * Creates a test Allergy with a specific ID.
     *
     * @param id The allergy ID
     * @return An Allergy instance with the specified ID
     */
    protected Allergy createTestAllergyWithId(Integer id) {
        Allergy allergy = createTestAllergy();
        setIdViaReflection(allergy, id);
        return allergy;
    }

    /**
     * Creates an archived (inactive) test Allergy.
     *
     * @return An archived Allergy instance
     */
    protected Allergy createArchivedAllergy() {
        Allergy allergy = createTestAllergy();
        allergy.setArchived(true);
        return allergy;
    }

    /**
     * Sets ID via reflection since Allergy uses @GeneratedValue.
     */
    private void setIdViaReflection(Allergy allergy, Integer id) {
        try {
            java.lang.reflect.Field field = Allergy.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(allergy, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set allergy ID", e);
        }
    }
}
