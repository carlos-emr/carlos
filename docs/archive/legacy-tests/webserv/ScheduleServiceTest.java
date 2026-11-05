
package io.github.carlos_emr.carlos.webserv.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.junit.Before;
import org.junit.Test;

import io.github.carlos_emr.carlos.webserv.rest.to.SchedulingResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.model.AppointmentTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.NewAppointmentTo1;

public class ScheduleServiceTest extends BaseRestServiceTest {

    /*
     * Replace this with the provider ID created for integration testing.
     */
    private static final String TEST_PROVIDER_NO = "TEST_PROVIDER_ID";

    private ScheduleService service;
    private DemographicService demographicService;

    @Before
    public void setUp() {
        mediaType = MediaType.APPLICATION_JSON;

        if (enabled) {
            service = getResource(ScheduleService.class);
            demographicService = getResource(DemographicService.class);
        }
    }

    /**
     * Creates a patient, creates an appointment for that patient, exercises
     * the appointment endpoints, and cleans up the test patient.
     */
    @Test
    public void testAppointmentLifecycle() {
        if (!enabled) {
            return;
        }

        DemographicTo1 demographic = new DemographicTo1();
        populate(demographic);
        demographic.setDemographicNo(null);

        Integer demographicNo = null;
        Integer appointmentId = null;
        boolean appointmentDeleted = false;

        try {
            // Create patient
            demographic = demographicService.createDemographicData(demographic);

            assertNotNull("Patient creation should return a patient", demographic);
            assertNotNull("Patient should have a generated ID", demographic.getDemographicNo());

            demographicNo = demographic.getDemographicNo();

            // Create appointment
            NewAppointmentTo1 newAppointment = new NewAppointmentTo1();
            populate(newAppointment);

            newAppointment.setProviderNo(TEST_PROVIDER_NO);
            newAppointment.setDemographicNo(demographicNo);
            newAppointment.setAppointmentDate(futureDate(1));
            newAppointment.setStartTime("10:00");
            newAppointment.setDuration(30);
            newAppointment.setName("Integration Test Appointment");
            newAppointment.setNotes("Created by ScheduleServiceTest");
            newAppointment.setReason("Integration testing");
            newAppointment.setLocation("Test");
            newAppointment.setType("Regular");
            newAppointment.setStatus("Booked");
            newAppointment.setUrgency("Normal");

            SchedulingResponse created = service.addAppointment(newAppointment);

            assertNotNull("Appointment creation should return a response", created);
            assertNotNull("Appointment creation should return an appointment",
                    created.getAppointment());
            assertNotNull("Created appointment should have an ID",
                    created.getAppointment().getId());

            appointmentId = created.getAppointment().getId();

            // Retrieve appointment
            AppointmentTo1 lookup = new AppointmentTo1();
            lookup.setId(appointmentId);

            SchedulingResponse retrieved = service.getAppointment(lookup);

            assertNotNull(retrieved);
            assertNotNull(retrieved.getAppointment());
            assertEquals(appointmentId, retrieved.getAppointment().getId());
            assertEquals(demographicNo,
                    retrieved.getAppointment().getDemographicNo());

            // General appointment modification
            AppointmentTo1 appointment = retrieved.getAppointment();
            appointment.setNotes("Updated by ScheduleServiceTest");
            appointment.setReason("Updated integration test reason");

            SchedulingResponse updated = service.updateAppointment(appointment);

            assertNotNull(updated);
            assertNotNull(updated.getAppointment());
            assertEquals(appointmentId, updated.getAppointment().getId());
            assertEquals("Updated by ScheduleServiceTest",
                    updated.getAppointment().getNotes());

            // Update appointment status
            appointment = updated.getAppointment();
            appointment.setStatus("Booked");

            SchedulingResponse statusUpdated =
                    service.updateAppointmentStatus(appointmentId, appointment);

            assertNotNull(statusUpdated);
            assertNotNull(statusUpdated.getAppointment());

            // Update appointment type
            appointment = statusUpdated.getAppointment();
            appointment.setType("Regular");

            SchedulingResponse typeUpdated =
                    service.updateAppointmentType(appointmentId, appointment);

            assertNotNull(typeUpdated);
            assertNotNull(typeUpdated.getAppointment());

            // Update appointment urgency
            appointment = typeUpdated.getAppointment();
            appointment.setUrgency("Normal");

            SchedulingResponse urgencyUpdated =
                    service.updateAppointmentUrgency(appointmentId, appointment);

            assertNotNull(urgencyUpdated);
            assertNotNull(urgencyUpdated.getAppointment());

            // Retrieve appointment history for the patient
            SchedulingResponse history =
                    service.findExistAppointments(demographicNo);

            assertNotNull(history);
            assertNotNull(history.getAppointments());

            // Retrieve appointments for the appointment date
            assertNotNull(service.getAppointmentsForDay(
                    TEST_PROVIDER_NO, futureDate(1)));

            // Retrieve monthly appointment data
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DAY_OF_MONTH, 1);

            SchedulingResponse monthly = service.fetchMonthlyData(
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH) + 1,
                    TEST_PROVIDER_NO);

            assertNotNull(monthly);

            // Exercise appointment listing endpoints
            String startDate = futureDate(0);
            String endDate = futureDate(7);

            assertNotNull(service.listAppointmentsByPeriodProvider(
                    startDate, endDate, TEST_PROVIDER_NO));

            assertNotNull(service.listProviderAppointmentCounts(
                    startDate, endDate));

            assertNotNull(service.listProviderApptsForPeriod(
                    TEST_PROVIDER_NO, startDate, endDate));

            // Delete appointment
            Response deleteResponse = service.deleteAppointment(
                    urgencyUpdated.getAppointment());

            assertNotNull(deleteResponse);
            assertTrue("Appointment deletion should succeed",
                    deleteResponse.getStatus() >= 200
                            && deleteResponse.getStatus() < 300);

            appointmentDeleted = true;

            // Verify appointment can no longer be retrieved
            try {
                SchedulingResponse afterDelete = service.getAppointment(lookup);

                assertNotNull(afterDelete);
                assertNull("Deleted appointment should no longer be returned",
                        afterDelete.getAppointment());

            } catch (WebApplicationException e) {
                assertEquals(404, e.getResponse().getStatus());
            }

        } finally {
            // Clean up appointment if the test failed before deleting it
            if (appointmentId != null && !appointmentDeleted) {
                try {
                    AppointmentTo1 appointment = new AppointmentTo1();
                    appointment.setId(appointmentId);
                    service.deleteAppointment(appointment);
                } catch (Exception ignored) {
                    // Preserve the original test failure.
                }
            }

            // Clean up patient
            if (demographicNo != null) {
                try {
                    demographicService.deleteDemographicData(demographicNo);
                } catch (Exception ignored) {
                    // Preserve the original test failure.
                }
            }
        }
    }

    @Test
    public void testAppointmentReferenceData() {
        if (!enabled) {
            return;
        }

        assertNotNull(service.getAppointmentStatuses());
        assertNotNull(service.getAppointmentTypes());
        assertNotNull(service.getScheduleTemplateCodes());
        assertNotNull(service.getAppointmentReasons());
    }

    @Test
    public void testGetAppointmentsForDay() {
        if (!enabled) {
            return;
        }

        assertNotNull(service.getAppointmentsForDay("today"));
        assertNotNull(service.getAppointmentsForDay("", "today"));
    }

    private String futureDate(int daysFromToday) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, daysFromToday);

        return new SimpleDateFormat("yyyy-MM-dd").format(calendar.getTime());
    }
}
