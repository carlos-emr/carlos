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

import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.commn.model.Prescription;
import io.github.carlos_emr.carlos.test.unit.OpenOUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.mockito.Mockito;

import java.util.Date;

/**
 * Base class for Prescription-related unit tests providing common mocks and test data builders.
 *
 * @since 2026-02-09
 * @see OpenOUnitTestBase
 */
@Tag("unit")
@Tag("fast")
@Tag("prescription")
public abstract class PrescriptionUnitTestBase extends OpenOUnitTestBase {

    protected SecurityInfoManager mockSecurityInfoManager;
    protected LoggedInInfo mockLoggedInInfo;
    protected Facility mockFacility;

    protected static final Integer TEST_DEMO_NO = 12345;
    protected static final String TEST_PROVIDER = "999990";
    protected static final Integer TEST_DRUG_ID = 1;
    protected static final String TEST_DRUG_NAME = "Amoxicillin";
    protected static final String TEST_ATC_CODE = "J01CA04";

    @BeforeEach
    void setUpPrescriptionMocks() {
        mockSecurityInfoManager = Mockito.mock(SecurityInfoManager.class);
        mockLoggedInInfo = Mockito.mock(LoggedInInfo.class);
        mockFacility = Mockito.mock(Facility.class);

        Mockito.lenient().when(mockLoggedInInfo.getCurrentFacility()).thenReturn(mockFacility);
        Mockito.lenient().when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(TEST_PROVIDER);

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
    }

    /**
     * Creates a valid test Drug with sensible medication defaults.
     *
     * @return A valid Drug instance for testing
     */
    protected Drug createTestDrug() {
        Drug drug = new Drug();
        drug.setDemographicId(TEST_DEMO_NO);
        drug.setProviderNo(TEST_PROVIDER);
        drug.setBrandName(TEST_DRUG_NAME);
        drug.setAtc(TEST_ATC_CODE);
        drug.setArchived(false);
        drug.setRxDate(new Date());
        drug.setEndDate(new Date());
        drug.setCreateDate(new Date());
        drug.setLastUpdateDate(new Date());
        drug.setLongTerm(false);
        drug.setDosage("500mg");
        drug.setFreqCode("BID");
        drug.setQuantity("30");
        return drug;
    }

    /**
     * Creates a test Drug with a specific ID.
     *
     * @param id The drug ID
     * @return A Drug instance with the specified ID
     */
    protected Drug createTestDrugWithId(Integer id) {
        Drug drug = createTestDrug();
        injectDrugId(drug, id);
        return drug;
    }

    /**
     * Creates a long-term medication.
     *
     * @return A Drug instance marked as long-term
     */
    protected Drug createLongTermDrug() {
        Drug drug = createTestDrug();
        drug.setLongTerm(true);
        return drug;
    }

    /**
     * Creates an archived (discontinued) drug.
     *
     * @return An archived Drug instance
     */
    protected Drug createArchivedDrug() {
        Drug drug = createTestDrug();
        drug.setArchived(true);
        return drug;
    }

    /**
     * Creates a test Prescription.
     *
     * @return A Prescription instance for testing
     */
    protected Prescription createTestPrescription() {
        Prescription prescription = new Prescription();
        prescription.setDemographicId(TEST_DEMO_NO);
        prescription.setProviderNo(TEST_PROVIDER);
        prescription.setDatePrescribed(new Date());
        return prescription;
    }

    private void injectDrugId(Drug drug, Integer id) {
        try {
            java.lang.reflect.Field field = Drug.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(drug, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set drug ID via reflection", e);
        }
    }
}
