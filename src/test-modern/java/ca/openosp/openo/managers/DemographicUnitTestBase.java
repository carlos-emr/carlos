/**
 * Copyright (c) 2026. Magenta Health. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for
 * Magenta Health
 * Toronto, Ontario, Canada
 */
package ca.openosp.openo.managers;

import ca.openosp.openo.commn.model.Admission;
import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.commn.model.DemographicContact;
import ca.openosp.openo.commn.model.DemographicCust;
import ca.openosp.openo.commn.model.DemographicExt;
import ca.openosp.openo.commn.model.DemographicMerged;
import ca.openosp.openo.commn.model.Facility;
import ca.openosp.openo.commn.model.Provider;
import ca.openosp.openo.test.unit.OpenOUnitTestBase;
import ca.openosp.openo.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.mockito.Mockito;

import java.util.Date;

/**
 * Base class for Demographic-related unit tests providing common mocks and utilities.
 *
 * <p>This class extends OpenOUnitTestBase and adds Demographic-specific test infrastructure
 * including commonly used mocks, helper methods, and test data builders. It serves as
 * the foundation for all Demographic unit tests, enabling consistent test patterns across
 * DAO, Manager, and validation layers.</p>
 *
 * <p><b>Scalability Strategy:</b></p>
 * <ul>
 *   <li>All Demographic unit tests extend this base class</li>
 *   <li>Tests are organized by component and concern (Query, Logic, Validation, etc.)</li>
 *   <li>Common mocks and helpers are centralized here</li>
 *   <li>Test data builders provide consistent test objects</li>
 * </ul>
 *
 * @since 2026-01-24
 * @see OpenOUnitTestBase
 */
@Tag("unit")
@Tag("fast")
@Tag("demographic")
public abstract class DemographicUnitTestBase extends OpenOUnitTestBase {

    // Common mocks that most Demographic tests will need
    protected SecurityInfoManager mockSecurityInfoManager;
    protected LoggedInInfo mockLoggedInInfo;
    protected Facility mockFacility;

    // Test data constants
    protected static final Integer TEST_DEMO_NO = 12345;
    protected static final String TEST_PROVIDER = "999990";
    protected static final String TEST_FIRST_NAME = "John";
    protected static final String TEST_LAST_NAME = "Doe";
    protected static final String TEST_HIN = "1234567890";
    protected static final String TEST_EMAIL = "john.doe@example.com";
    protected static final String TEST_PHONE = "416-555-1234";
    protected static final Integer TEST_PROGRAM_ID = 100;

    /**
     * Sets up common Demographic-related mocks before each test.
     *
     * <p>This method is called automatically by JUnit before each test method
     * and performs the following setup:</p>
     * <ol>
     *   <li>Creates mock instances for SecurityInfoManager, LoggedInInfo, and Facility</li>
     *   <li>Configures default behavior for facility (integrator disabled)</li>
     *   <li>Configures LoggedInInfo to return test provider number</li>
     *   <li>Registers SecurityInfoManager with SpringUtils for static lookups</li>
     * </ol>
     *
     * <p><b>Note:</b> Subclasses should call additional mock registrations in their
     * own {@code @BeforeEach} methods, which run after this base method.</p>
     */
    @BeforeEach
    void setUpDemographicMocks() {
        // Create mocks manually to avoid mockito-inline issues with interfaces
        mockSecurityInfoManager = Mockito.mock(SecurityInfoManager.class);
        mockLoggedInInfo = Mockito.mock(LoggedInInfo.class);
        mockFacility = Mockito.mock(Facility.class);

        // Configure facility mock to return false for integrator by default
        Mockito.lenient().when(mockLoggedInInfo.getCurrentFacility()).thenReturn(mockFacility);
        Mockito.lenient().when(mockFacility.isIntegratorEnabled()).thenReturn(false);
        Mockito.lenient().when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(TEST_PROVIDER);

        // Register common mocks with SpringUtils
        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
    }

    /**
     * Creates a valid test Demographic with sensible defaults.
     * Can be customized after creation for specific test needs.
     *
     * @return A valid Demographic instance for testing
     */
    protected Demographic createTestDemographic() {
        Demographic demographic = new Demographic();
        demographic.setDemographicNo(TEST_DEMO_NO);
        demographic.setFirstName(TEST_FIRST_NAME);
        demographic.setLastName(TEST_LAST_NAME);
        demographic.setHin(TEST_HIN);
        demographic.setEmail(TEST_EMAIL);
        demographic.setPhone(TEST_PHONE);
        demographic.setProviderNo(TEST_PROVIDER);
        demographic.setPatientStatus(Demographic.PatientStatus.AC.name());
        demographic.setYearOfBirth("1980");
        demographic.setMonthOfBirth("01");
        demographic.setDateOfBirth("15");
        demographic.setSex("M");
        demographic.setLastUpdateUser(TEST_PROVIDER);
        demographic.setLastUpdateDate(new Date());
        return demographic;
    }

    /**
     * Creates a test Demographic with a specific ID.
     *
     * @param id The ID to set on the demographic
     * @return A Demographic instance with the specified ID
     */
    protected Demographic createTestDemographicWithId(Integer id) {
        Demographic demographic = createTestDemographic();
        demographic.setDemographicNo(id);
        return demographic;
    }

    /**
     * Creates a test Demographic with specific patient status.
     *
     * @param status The patient status to set
     * @return A Demographic instance with the specified status
     */
    protected Demographic createTestDemographicWithStatus(Demographic.PatientStatus status) {
        Demographic demographic = createTestDemographic();
        demographic.setPatientStatus(status.name());
        return demographic;
    }

    /**
     * Creates a test DemographicExt with specified values.
     *
     * @param demoNo The demographic number
     * @param key The extension key
     * @param value The extension value
     * @return A DemographicExt instance
     */
    protected DemographicExt createTestDemographicExt(Integer demoNo, String key, String value) {
        DemographicExt ext = new DemographicExt();
        ext.setDemographicNo(demoNo);
        ext.setKey(key);
        ext.setValue(value);
        ext.setProviderNo(TEST_PROVIDER);
        ext.setDateCreated(new Date());
        return ext;
    }

    /**
     * Creates a test DemographicExt with an ID.
     *
     * @param id The extension ID
     * @param demoNo The demographic number
     * @param key The extension key
     * @param value The extension value
     * @return A DemographicExt instance with the specified ID
     */
    protected DemographicExt createTestDemographicExtWithId(Integer id, Integer demoNo, String key, String value) {
        DemographicExt ext = createTestDemographicExt(demoNo, key, value);
        ext.setId(id);
        return ext;
    }

    /**
     * Creates a test DemographicContact with specified values.
     *
     * @param demoNo The demographic number
     * @param category The contact category (personal/professional)
     * @param type The contact type (0=provider, 1=demographic, 2=contact, 3=specialist)
     * @return A DemographicContact instance
     */
    protected DemographicContact createTestDemographicContact(Integer demoNo, String category, int type) {
        DemographicContact contact = new DemographicContact();
        contact.setDemographicNo(demoNo);
        contact.setCategory(category);
        contact.setType(type);
        contact.setContactId(TEST_PROVIDER);
        contact.setCreated(new Date());
        contact.setActive(true);
        contact.setDeleted(false);
        return contact;
    }

    /**
     * Creates a test DemographicContact with an ID.
     *
     * @param id The contact ID
     * @param demoNo The demographic number
     * @param category The contact category
     * @param type The contact type
     * @return A DemographicContact instance with the specified ID
     */
    protected DemographicContact createTestDemographicContactWithId(Integer id, Integer demoNo, String category, int type) {
        DemographicContact contact = createTestDemographicContact(demoNo, category, type);
        try {
            java.lang.reflect.Field idField = DemographicContact.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(contact, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set contact ID", e);
        }
        return contact;
    }

    /**
     * Creates a test DemographicCust with specified values.
     *
     * @param id The demographic number (which is the ID for DemographicCust)
     * @return A DemographicCust instance
     */
    protected DemographicCust createTestDemographicCust(Integer id) {
        DemographicCust cust = new DemographicCust();
        cust.setId(id);
        cust.setNurse("nurse1");
        cust.setResident("resident1");
        cust.setMidwife("midwife1");
        cust.setAlert("alert notes");
        cust.setNotes("general notes");
        return cust;
    }

    /**
     * Creates a test DemographicMerged with specified values.
     *
     * @param childId The child demographic number (the one being merged)
     * @param parentId The parent demographic number (the one receiving the merge)
     * @return A DemographicMerged instance
     */
    protected DemographicMerged createTestDemographicMerged(Integer childId, Integer parentId) {
        DemographicMerged merged = new DemographicMerged();
        merged.setDemographicNo(childId);
        merged.setMergedTo(parentId);
        merged.setDeleted(0);
        merged.setLastUpdateUser(TEST_PROVIDER);
        merged.setLastUpdateDate(new Date());
        return merged;
    }

    /**
     * Creates a test DemographicMerged with an ID.
     *
     * @param id The merge record ID
     * @param childId The child demographic number
     * @param parentId The parent demographic number
     * @return A DemographicMerged instance with the specified ID
     */
    protected DemographicMerged createTestDemographicMergedWithId(Integer id, Integer childId, Integer parentId) {
        DemographicMerged merged = createTestDemographicMerged(childId, parentId);
        merged.setId(id);
        return merged;
    }

    /**
     * Creates a test Provider with sensible defaults.
     *
     * @return A Provider instance for testing
     */
    protected Provider createTestProvider() {
        Provider provider = new Provider();
        provider.setProviderNo(TEST_PROVIDER);
        provider.setFirstName("Test");
        provider.setLastName("Provider");
        return provider;
    }

    /**
     * Creates a test Provider with specified provider number.
     *
     * @param providerNo The provider number
     * @return A Provider instance with the specified provider number
     */
    protected Provider createTestProviderWithNo(String providerNo) {
        Provider provider = createTestProvider();
        provider.setProviderNo(providerNo);
        return provider;
    }

    /**
     * Creates a test Admission with sensible defaults.
     *
     * @param demographicNo The demographic number
     * @param programId The program ID
     * @return An Admission instance for testing
     */
    protected Admission createTestAdmission(Integer demographicNo, Integer programId) {
        Admission admission = new Admission();
        admission.setClientId(demographicNo);
        admission.setProgramId(programId);
        admission.setProviderNo(TEST_PROVIDER);
        admission.setAdmissionDate(new Date());
        admission.setAdmissionStatus(Admission.STATUS_CURRENT);
        admission.setAdmissionNotes("");
        return admission;
    }

    /**
     * Helper method to inject dependencies into manager using reflection.
     *
     * @param target The target object to inject into
     * @param fieldName The field name to inject
     * @param dependency The dependency object to inject
     */
    protected void injectDependency(Object target, String fieldName, Object dependency) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, dependency);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject " + fieldName, e);
        }
    }
}
