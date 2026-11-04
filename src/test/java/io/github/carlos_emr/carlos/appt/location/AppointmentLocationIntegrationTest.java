/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.appt.location;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.sql.Time;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.commn.dao.AppointmentArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.LookupListDao;
import io.github.carlos_emr.carlos.commn.dao.LookupListItemDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.AppointmentArchive;
import io.github.carlos_emr.carlos.commn.model.LookupList;
import io.github.carlos_emr.carlos.commn.model.LookupListItem;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * The appointment Location List against the H2 schema Hibernate builds from the entities (#3982):
 * the new columns round-trip, the archive copy carries the code, and the settings service keeps
 * the stored list consistent through real DAOs.
 */
@DisplayName("Appointment Location List persistence")
@Tag("integration")
@Tag("dao")
@Tag("appointment")
@Transactional
class AppointmentLocationIntegrationTest extends CarlosTestBase {

    @Autowired
    private OscarAppointmentDao appointmentDao;
    @Autowired
    private AppointmentArchiveDao appointmentArchiveDao;
    @Autowired
    private LookupListDao lookupListDao;
    @Autowired
    private LookupListItemDao lookupListItemDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private AppointmentLocationService service;
    private LoggedInInfo loggedInInfo;
    private LookupList list;

    @BeforeEach
    void setUpList() {
        list = new LookupList();
        list.setName(AppointmentLocationResolver.LIST_NAME);
        list.setListTitle("Appointment Locations");
        list.setActive(true);
        list.setCreatedBy("oscar");
        list.setDateCreated(new java.util.Date());
        lookupListDao.persist(list);

        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(), anyString(), anyString(), isNull())).thenReturn(true);
        loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        service = new AppointmentLocationService(lookupListDao, lookupListItemDao, securityInfoManager,
                new AppointmentLocationResolver(lookupListDao, () -> false)) {
            @Override
            protected void audit(LoggedInInfo who, String operation, String data) {
                // The audit row is covered by the unit test; the log table is not under test here.
            }
        };
    }

    private Appointment appointment(String location, Integer code) {
        Appointment appointment = new Appointment();
        appointment.setProviderNo("999998");
        appointment.setAppointmentDate(Date.valueOf("2027-03-01"));
        appointment.setStartTime(Time.valueOf("09:00:00"));
        appointment.setEndTime(Time.valueOf("09:14:00"));
        appointment.setName("PW_LOCATION_INTEGRATION");
        appointment.setDemographicNo(0);
        appointment.setStatus("t");
        appointment.setLocation(location);
        appointment.setLocationCode(code);
        return appointment;
    }

    @Test
    @Tag("create")
    @Tag("read")
    void shouldRoundTripLocationCode_onAppointment() {
        Appointment saved = appointment("Virtual", 4321);
        appointmentDao.persist(saved);
        entityManager.flush();
        entityManager.clear();

        Appointment found = appointmentDao.find(saved.getId());

        assertThat(found.getLocationCode()).isEqualTo(4321);
        assertThat(found.getLocation()).isEqualTo("Virtual");
    }

    @Test
    @Tag("create")
    void shouldCopyLocationCode_intoArchive() {
        Appointment saved = appointment("Room 2", 98);
        appointmentDao.persist(saved);

        AppointmentArchive archived = appointmentArchiveDao.archiveAppointment(saved);
        entityManager.flush();
        entityManager.clear();

        AppointmentArchive found = appointmentArchiveDao.find(archived.getId());
        assertThat(found.getLocationCode()).isEqualTo(98);
        assertThat(found.getLocation()).isEqualTo("Room 2");
        assertThat(found.getAppointmentNo()).isEqualTo(saved.getId());
    }

    @Test
    @Tag("update")
    void shouldKeepFullLengthLabelSnapshot_inLocationColumn() {
        Appointment saved = appointment("L".repeat(AppointmentLocationResolver.LABEL_MAX_LENGTH), 1);
        appointmentDao.persist(saved);
        entityManager.flush();
        entityManager.clear();

        assertThat(appointmentDao.find(saved.getId()).getLocation()).hasSize(AppointmentLocationResolver.LABEL_MAX_LENGTH);
    }

    @Test
    @Tag("create")
    @Tag("update")
    void shouldStoreStyleAndOrder_throughSettingsService() {
        LookupListItem virtual = service.addLocation(loggedInInfo, "Virtual");
        AppointmentLocationService.AddResult pasted = service.addLocations(loggedInInfo, "Room 2\nvirtual\nAnnex");
        service.updateColour(loggedInInfo, virtual.getId(), "#2FCCCF");
        service.updateIcon(loggedInInfo, virtual.getId(), "fa-video");
        service.move(loggedInInfo, virtual.getId(), false);
        LookupListItem annex = lookupListItemDao.findByLookupListIdAndValue(list.getId(), "Annex");
        service.deactivate(loggedInInfo, annex.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(pasted.added()).isEqualTo(2);
        assertThat(pasted.skipped()).isEqualTo(1);
        List<LookupListItem> active = lookupListItemDao.findActiveByLookupListId(list.getId());
        assertThat(active).extracting(LookupListItem::getLabel).containsExactly("Room 2", "Virtual");
        assertThat(active).extracting(LookupListItem::getDisplayOrder).containsExactly(1, 2);
        LookupListItem styled = lookupListItemDao.find(virtual.getId());
        assertThat(styled.getColour()).isEqualTo("#2fcccf");
        assertThat(styled.getIcon()).isEqualTo("fa-video");
        assertThat(lookupListItemDao.find(annex.getId()).isActive()).isFalse();
    }

    @Test
    @Tag("update")
    void shouldSaveChosenItem_andKeepItAfterRename() {
        LookupListItem room = service.addLocation(loggedInInfo, "Room 2");
        entityManager.flush();
        entityManager.clear();
        LookupList reloaded = lookupListDao.find(list.getId());
        reloaded.setItems(lookupListItemDao.findActiveByLookupListId(list.getId()));
        LookupListDao stubbedDao = mock(LookupListDao.class);
        when(stubbedDao.findByName(AppointmentLocationResolver.LIST_NAME)).thenReturn(reloaded);
        AppointmentLocationResolver resolver = new AppointmentLocationResolver(stubbedDao, () -> false);

        Appointment booked = appointment(null, null);
        resolver.applyPostedLocation(booked, String.valueOf(room.getId()), "");
        appointmentDao.persist(booked);
        service.rename(loggedInInfo, room.getId(), "Room Two");
        entityManager.flush();
        entityManager.clear();

        Appointment found = appointmentDao.find(booked.getId());
        assertThat(found.getLocationCode()).isEqualTo(room.getId());
        assertThat(found.getLocation()).isEqualTo("Room 2");
        assertThat(lookupListItemDao.find(room.getId()).getLabel()).isEqualTo("Room Two");
    }
}
