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
package io.github.carlos_emr.carlos.webserv.rest.conversion;

import java.util.Calendar;
import java.util.Date;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.dao.AllergyDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.webserv.rest.util.ProductionRestObjectMappers;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The read path a {@code /ws/services} integration takes, from the database to the wire, keeps
 * one JSON type for each timestamp (issue #3877).
 *
 * <p>Rows are written with timestamps at local midnight and on 1970-01-01 (the two values
 * {@code SmartDateSerializer} turns into strings), the persistence context is cleared so they come
 * back from the database as Hibernate loads them ({@code java.sql.Timestamp}, {@code java.sql.Date},
 * {@code java.sql.Time}), and the production converters and the OAuth surface's mapper write them.
 * Timestamps must arrive as epoch milliseconds; the calendar-date and clock-time columns beside
 * them must keep the strings integrations already parse.
 *
 * @since 2026-10-19
 */
@DisplayName("REST timestamp shapes from the database to the OAuth wire")
@Tag("integration")
@Tag("rest")
@Transactional
class RestTimestampEpochShapeIntegrationTest extends CarlosTestBase {

    private static final int DEMOGRAPHIC_NO = 38771;

    @Autowired
    private AllergyDao allergyDao;

    @Autowired
    private MeasurementDao measurementDao;

    @Autowired
    private OscarAppointmentDao oscarAppointmentDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private final ObjectMapper oauth = ProductionRestObjectMappers.oauthSurface();

    private static Date local(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month, day, hour, minute);
        return calendar.getTime();
    }

    private static final Date MIDNIGHT = local(2026, Calendar.MARCH, 11, 0, 0);
    private static final Date EPOCH_DAY = local(1970, Calendar.JANUARY, 1, 9, 30);

    /** Writes and detaches, so the next read is a fresh load from the database. */
    private void flushAndClear() {
        entityManager.flush();
        hibernateTemplate.flush();
        entityManager.clear();
    }

    private static void assertEpoch(JsonNode json, String field, Date expected) {
        JsonNode value = json.get(field);
        assertThat(value).as("%s present", field).isNotNull();
        assertThat(value.isIntegralNumber()).as("%s on the wire: %s", field, value).isTrue();
        assertThat(value.longValue()).as(field).isEqualTo(expected.getTime());
    }

    @Test
    @DisplayName("should write a reloaded allergy's midnight update time as epoch milliseconds")
    void shouldWriteAllergyLastUpdateAsEpochMillis_whenReloadedFromDatabase() throws Exception {
        Allergy allergy = new Allergy();
        allergy.setDemographicNo(DEMOGRAPHIC_NO);
        allergy.setDescription("FAKE-allergen");
        allergy.setArchived(false);
        allergy.setTypeCode(0);
        allergy.setEntryDate(MIDNIGHT);
        allergy.setStartDate(MIDNIGHT);
        allergy.setLastUpdateDate(MIDNIGHT);
        allergyDao.persist(allergy);
        entityManager.flush();
        // Allergy stamps lastUpdateDate with new Date() on every persist/update, so a midnight value
        // reaches the table the way imported and migrated rows do: around the entity callbacks.
        entityManager.createQuery("UPDATE Allergy a SET a.lastUpdateDate = :value WHERE a.id = :id")
                .setParameter("value", MIDNIGHT).setParameter("id", allergy.getId()).executeUpdate();
        flushAndClear();

        Allergy reloaded = allergyDao.find(allergy.getId());
        JsonNode json = oauth.valueToTree(new AllergyConverter().getAsTransferObject(null, reloaded));

        assertThat(reloaded).isNotSameAs(allergy);
        assertEpoch(json, "lastUpdateDate", MIDNIGHT);
        // entry_date is a DATE column: a calendar date keeps its string shape.
        assertThat(json.get("entryDate").textValue()).isEqualTo("2026-03-11");
    }

    @Test
    @DisplayName("should write a reloaded measurement's timestamps as epoch milliseconds, as TO and as entity")
    void shouldWriteMeasurementTimestampsAsEpochMillis_whenReloadedFromDatabase() throws Exception {
        Measurement measurement = new Measurement();
        measurement.setDemographicId(DEMOGRAPHIC_NO);
        measurement.setType("WT");
        measurement.setDataField("70");
        measurement.setProviderNo("999998");
        measurement.setMeasuringInstruction("");
        measurement.setComments("");
        measurement.setDateObserved(MIDNIGHT);
        measurement.setCreateDate(EPOCH_DAY);
        measurementDao.persist(measurement);
        flushAndClear();

        Measurement reloaded = measurementDao.find(measurement.getId());
        // DemographicTo1 carries the TO; /measurements/{demographicNo} carries the entity itself.
        JsonNode transfer = oauth.valueToTree(new MeasurementConverter().getAsTransferObject(null, reloaded));
        JsonNode entity = oauth.valueToTree(reloaded);

        for (JsonNode json : new JsonNode[] {transfer, entity}) {
            assertEpoch(json, "dateObserved", MIDNIGHT);
            assertEpoch(json, "createDate", EPOCH_DAY);
        }
    }

    @Test
    @DisplayName("should write reloaded appointment timestamps as epoch milliseconds and keep its slot strings")
    void shouldWriteAppointmentTimestampsAsEpochMillis_whenReloadedFromDatabase() throws Exception {
        Appointment appointment = new Appointment();
        appointment.setDemographicNo(DEMOGRAPHIC_NO);
        appointment.setProviderNo("999998");
        appointment.setAppointmentDate(MIDNIGHT);
        appointment.setStartTime(local(1970, Calendar.JANUARY, 1, 9, 0));
        appointment.setEndTime(local(1970, Calendar.JANUARY, 1, 9, 15));
        appointment.setName("FAKE-Patient");
        appointment.setNotes("");
        appointment.setReason("Follow-up");
        appointment.setStatus("t");
        appointment.setCreator("999998");
        appointment.setProgramId(0);
        appointment.setCreateDateTime(MIDNIGHT);
        appointment.setUpdateDateTime(EPOCH_DAY);
        entityManager.persist(appointment);
        entityManager.flush();
        // Appointment stamps updateDateTime with new Date() on persist/update; set it as an
        // imported row would carry it.
        entityManager.createQuery("UPDATE Appointment a SET a.updateDateTime = :value WHERE a.id = :id")
                .setParameter("value", EPOCH_DAY).setParameter("id", appointment.getId()).executeUpdate();
        flushAndClear();

        Appointment reloaded = oscarAppointmentDao.find(appointment.getId());
        JsonNode json = oauth.valueToTree(new AppointmentConverter().getAsTransferObject(null, reloaded));

        assertEpoch(json, "createDateTime", MIDNIGHT);
        assertEpoch(json, "updateDateTime", EPOCH_DAY);
        // DATE and TIME columns come back as java.sql.Date/Time and keep the shapes the
        // updateAppointment round trip (issue #2957) depends on.
        assertThat(json.get("appointmentDate").textValue()).isEqualTo("2026-03-11");
        assertThat(json.get("startTime").textValue()).isEqualTo("09:00:00");
        assertThat(json.get("endTime").textValue()).isEqualTo("09:15:00");
    }
}
