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

import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.AppointmentArchive;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.test.unit.OpenOUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.mockito.Mockito;

import java.util.Calendar;
import java.util.Date;

/**
 * Base class for Appointment-related unit tests providing common mocks and test data builders.
 *
 * @since 2026-02-09
 * @see OpenOUnitTestBase
 */
@Tag("unit")
@Tag("fast")
@Tag("appointment")
public abstract class AppointmentUnitTestBase extends OpenOUnitTestBase {

    protected SecurityInfoManager mockSecurityInfoManager;
    protected LoggedInInfo mockLoggedInInfo;
    protected Facility mockFacility;

    protected static final Integer TEST_DEMO_NO = 12345;
    protected static final String TEST_PROVIDER = "999990";
    protected static final Integer TEST_APPOINTMENT_ID = 1;
    protected static final String TEST_REASON = "Follow-up visit";
    protected static final String TEST_STATUS = "t"; // confirmed

    @BeforeEach
    void setUpAppointmentMocks() {
        mockSecurityInfoManager = Mockito.mock(SecurityInfoManager.class);
        mockLoggedInInfo = Mockito.mock(LoggedInInfo.class);
        mockFacility = Mockito.mock(Facility.class);

        Mockito.lenient().when(mockLoggedInInfo.getCurrentFacility()).thenReturn(mockFacility);
        Mockito.lenient().when(mockFacility.isIntegratorEnabled()).thenReturn(false);
        Mockito.lenient().when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(TEST_PROVIDER);

        registerMock(SecurityInfoManager.class, mockSecurityInfoManager);
    }

    /**
     * Creates a valid test Appointment with sensible defaults.
     *
     * @return A valid Appointment instance for testing
     */
    protected Appointment createTestAppointment() {
        Appointment appointment = new Appointment();
        appointment.setProviderNo(TEST_PROVIDER);
        appointment.setDemographicNo(TEST_DEMO_NO);
        appointment.setAppointmentDate(new Date());
        appointment.setStartTime(createTime(9, 0));
        appointment.setEndTime(createTime(9, 15));
        appointment.setStatus(TEST_STATUS);
        appointment.setReason(TEST_REASON);
        appointment.setType("");
        appointment.setNotes("");
        appointment.setCreator(TEST_PROVIDER);
        appointment.setCreateDateTime(new Date());
        appointment.setLastUpdateUser(TEST_PROVIDER);
        appointment.setUpdateDateTime(new Date());
        return appointment;
    }

    /**
     * Creates a test Appointment with a specific ID.
     *
     * @param id The appointment ID
     * @return An Appointment instance with the specified ID
     */
    protected Appointment createTestAppointmentWithId(Integer id) {
        Appointment appointment = createTestAppointment();
        setIdViaReflection(appointment, id);
        return appointment;
    }

    /**
     * Creates a test AppointmentArchive from an appointment.
     *
     * @return An AppointmentArchive instance for testing
     */
    protected AppointmentArchive createTestAppointmentArchive() {
        AppointmentArchive archive = new AppointmentArchive();
        archive.setProviderNo(TEST_PROVIDER);
        archive.setDemographicNo(TEST_DEMO_NO);
        archive.setAppointmentDate(new Date());
        return archive;
    }

    /**
     * Creates a Date representing a specific time of day.
     */
    protected Date createTime(int hour, int minute) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    private void setIdViaReflection(Appointment appointment, Integer id) {
        try {
            java.lang.reflect.Field field = Appointment.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(appointment, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set appointment ID", e);
        }
    }
}
