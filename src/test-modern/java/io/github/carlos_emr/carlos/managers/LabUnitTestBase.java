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
import io.github.carlos_emr.carlos.commn.model.Hl7TextInfo;
import io.github.carlos_emr.carlos.commn.model.Hl7TextMessage;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.test.unit.OpenOUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.mockito.Mockito;

import java.util.Date;

/**
 * Base class for Lab-related unit tests providing common mocks and test data builders.
 *
 * @since 2026-02-09
 * @see OpenOUnitTestBase
 */
@Tag("unit")
@Tag("fast")
@Tag("lab")
public abstract class LabUnitTestBase extends OpenOUnitTestBase {

    protected SecurityInfoManager mockSecurityInfoManager;
    protected LoggedInInfo mockLoggedInInfo;
    protected Facility mockFacility;

    protected static final Integer TEST_DEMO_NO = 12345;
    protected static final String TEST_PROVIDER = "999990";
    protected static final Integer TEST_LAB_ID = 1;
    protected static final String TEST_LAB_TYPE = "HL7";

    @BeforeEach
    void setUpLabMocks() {
        mockSecurityInfoManager = Mockito.mock(SecurityInfoManager.class);
        mockLoggedInInfo = Mockito.mock(LoggedInInfo.class);
        mockFacility = Mockito.mock(Facility.class);

        Mockito.lenient().when(mockLoggedInInfo.getCurrentFacility()).thenReturn(mockFacility);
        Mockito.lenient().when(mockFacility.isIntegratorEnabled()).thenReturn(false);
        Mockito.lenient().when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(TEST_PROVIDER);

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
    }

    /**
     * Creates a valid test Hl7TextInfo with sensible lab defaults.
     *
     * @return A valid Hl7TextInfo instance for testing
     */
    protected Hl7TextInfo createTestHl7TextInfo() {
        Hl7TextInfo info = new Hl7TextInfo();
        info.setLabNumber(TEST_LAB_ID);
        info.setLastName("Doe");
        info.setFirstName("John");
        info.setSex("M");
        info.setHealthNumber("1234567890");
        info.setResultStatus("F"); // final
        info.setObrDate("20260115120000");
        info.setReportStatus("F");
        info.setAccessionNumber("ACC001");
        info.setFillerOrderNum("FILL001");
        info.setSendingFacility("TestLab");
        return info;
    }

    /**
     * Creates a test Hl7TextMessage with HL7 message content.
     *
     * @param message The HL7 message string (stored as base64 encoded)
     * @return An Hl7TextMessage instance
     */
    protected Hl7TextMessage createTestHl7TextMessage(String message) {
        Hl7TextMessage msg = new Hl7TextMessage();
        msg.setBase64EncodedeMessage(message);
        msg.setType(TEST_LAB_TYPE);
        return msg;
    }

    /**
     * Creates a test PatientLabRouting record linking a lab to a demographic.
     *
     * @param labNo The lab number
     * @param demographicNo The demographic number
     * @return A PatientLabRouting instance
     */
    protected PatientLabRouting createTestPatientLabRouting(Integer labNo, Integer demographicNo) {
        PatientLabRouting routing = new PatientLabRouting();
        routing.setLabNo(labNo);
        routing.setDemographicNo(demographicNo);
        routing.setLabType(TEST_LAB_TYPE);
        routing.setCreated(new Date());
        return routing;
    }
}
