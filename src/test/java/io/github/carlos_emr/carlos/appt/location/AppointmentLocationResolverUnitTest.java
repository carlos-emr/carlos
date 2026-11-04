/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.appt.location;

import static io.github.carlos_emr.carlos.appt.location.AppointmentLocationTestItems.item;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.LookupListDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.LookupList;
import io.github.carlos_emr.carlos.commn.model.LookupListItem;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/**
 * The one write rule for appointment locations (#3982): a Location List code is saved only with
 * its label, and cleared whenever the text changes another way.
 */
@DisplayName("AppointmentLocationResolver")
@Tag("unit")
@Tag("appointment")
class AppointmentLocationResolverUnitTest extends CarlosUnitTestBase {

    private static final String[] SITE_PROPERTIES = {"multisites", "scheduleSiteID", "ModuleNames", "useProgramLocation"};

    private LookupListDao lookupListDao;
    private AppointmentLocationResolver resolver;
    private final List<String[]> savedProperties = new ArrayList<>();

    @BeforeEach
    void setUp() {
        lookupListDao = mock(LookupListDao.class);
        LookupList list = new LookupList();
        list.setId(AppointmentLocationTestItems.LIST_ID);
        list.setName(AppointmentLocationResolver.LIST_NAME);
        list.setItems(new ArrayList<>(List.of(
                item(11, "Virtual", 1, true),
                item(13, "Satellite office", 2, false),
                item(15, "R".repeat(300), 3, true))));
        when(lookupListDao.findByName(AppointmentLocationResolver.LIST_NAME)).thenReturn(list);
        resolver = new AppointmentLocationResolver(lookupListDao, () -> false);
        registerMock(AppointmentLocationResolver.class, resolver);

        CarlosProperties properties = CarlosProperties.getInstance();
        for (String key : SITE_PROPERTIES) {
            savedProperties.add(new String[]{key, (String) properties.get(key)});
            properties.remove(key);
        }
    }

    @AfterEach
    void restoreProperties() {
        CarlosProperties properties = CarlosProperties.getInstance();
        for (String[] saved : savedProperties) {
            if (saved[1] == null) {
                properties.remove(saved[0]);
            } else {
                properties.setProperty(saved[0], saved[1]);
            }
        }
    }

    private static Appointment booked(String location, Integer code) {
        Appointment appointment = new Appointment();
        appointment.setLocation(location);
        appointment.setLocationCode(code);
        return appointment;
    }

    @Test
    void shouldSaveItemIdAndLabel_whenItemPosted() {
        Appointment appointment = booked("Typed text", null);

        resolver.applyPostedLocation(appointment, "11", "ignored");

        assertThat(appointment.getLocationCode()).isEqualTo(11);
        assertThat(appointment.getLocation()).isEqualTo("Virtual");
    }

    @Test
    void shouldKeepDeactivatedItem_whenEditScreenPostsItUnchanged() {
        Appointment appointment = booked("Satellite office", 13);

        resolver.applyPostedLocation(appointment, "13", "");

        assertThat(appointment.getLocationCode()).isEqualTo(13);
        assertThat(appointment.getLocation()).isEqualTo("Satellite office");
    }

    @Test
    void shouldCutLabelSnapshot_toLocationColumnWidth() {
        Appointment appointment = new Appointment();

        resolver.applyPostedLocation(appointment, "15", null);

        assertThat(appointment.getLocation()).hasSize(AppointmentLocationResolver.LABEL_MAX_LENGTH);
        assertThat(appointment.getLocationCode()).isEqualTo(15);
    }

    @Test
    void shouldKeepLegacyTextAndClearCode_whenLegacyPosted() {
        Appointment appointment = booked("Basement", 11);

        resolver.applyPostedLocation(appointment, AppointmentLocationList.LEGACY_VALUE, "Basement");

        assertThat(appointment.getLocationCode()).isNull();
        assertThat(appointment.getLocation()).isEqualTo("Basement");
        verify(lookupListDao, never()).findByName(anyString());
    }

    @Test
    void shouldClearCodeAndText_whenNotSpecifiedPosted() {
        Appointment appointment = booked("Virtual", 11);

        resolver.applyPostedLocation(appointment, "", "Virtual");

        assertThat(appointment.getLocationCode()).isNull();
        assertThat(appointment.getLocation()).isEmpty();
    }

    @Test
    void shouldSaveNoLocation_whenPostedIdIsNotAnItemOfTheList() {
        // An id from another lookup list (or one made up) never becomes a location code.
        Appointment appointment = booked("Virtual", 11);

        resolver.applyPostedLocation(appointment, "4242", "Virtual");

        assertThat(appointment.getLocationCode()).isNull();
        assertThat(appointment.getLocation()).isEmpty();
    }

    @Test
    void shouldSaveTypedText_whenFormHadNoDropdown() {
        Appointment appointment = booked("Virtual", 11);

        resolver.applyPostedLocation(appointment, null, "Room 9");

        assertThat(appointment.getLocationCode()).isNull();
        assertThat(appointment.getLocation()).isEqualTo("Room 9");
    }

    @Test
    void shouldKeepCode_whenTextIsUnchanged() {
        Appointment appointment = booked("Virtual", 11);

        AppointmentLocationResolver.setLocationText(appointment, "Virtual");

        assertThat(appointment.getLocationCode()).isEqualTo(11);
    }

    @Test
    void shouldTreatNullAndBlankAlike_whenComparingText() {
        Appointment appointment = booked(null, 11);

        AppointmentLocationResolver.setLocationText(appointment, "");

        assertThat(appointment.getLocationCode()).isEqualTo(11);
        assertThat(appointment.getLocation()).isEmpty();
    }

    @Test
    void shouldClearCode_whenIntegrationChangesText() {
        Appointment appointment = booked("Virtual", 11);

        AppointmentLocationResolver.setLocationText(appointment, "Room from HL7");

        assertThat(appointment.getLocationCode()).isNull();
        assertThat(appointment.getLocation()).isEqualTo("Room from HL7");
    }

    @Test
    void shouldNotLoadList_whenRequestPostsNoCode() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("location", "Typed");
        Appointment appointment = booked("Virtual", 11);

        AppointmentLocationResolver.applyPostedLocation(appointment, request);

        assertThat(appointment.getLocation()).isEqualTo("Typed");
        assertThat(appointment.getLocationCode()).isNull();
        verify(lookupListDao, never()).findByName(anyString());
    }

    @Test
    void shouldResolvePostedCode_throughRequestEntryPoint() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("locationCode", "11");
        request.addParameter("location", "");
        Appointment appointment = new Appointment();

        AppointmentLocationResolver.applyPostedLocation(appointment, request);

        assertThat(appointment.getLocationCode()).isEqualTo(11);
        assertThat(appointment.getLocation()).isEqualTo("Virtual");
    }

    @Test
    void shouldLoadEmptyList_whenLookupListIsMissing() {
        when(lookupListDao.findByName(AppointmentLocationResolver.LIST_NAME)).thenReturn(null);

        AppointmentLocationList locations = resolver.load();

        assertThat(locations.isLocationMode()).isFalse();
        assertThat(locations.getItemsActiveFirst()).isEmpty();
    }

    @Test
    void shouldPassSiteSetupIntoSnapshot_whenLoading() {
        AppointmentLocationList locations = new AppointmentLocationResolver(lookupListDao, () -> true).load();

        assertThat(locations.isSiteSetup()).isTrue();
        assertThat(locations.isLocationMode()).isFalse();
        assertThat(locations.getActiveItems()).extracting(LookupListItem::getId).containsExactly(11, 15);
    }

    @Test
    void shouldReportNoSiteSetup_whenNoSiteSwitchIsOn() {
        assertThat(AppointmentLocationResolver.isSiteSetupConfigured()).isFalse();
    }

    @Test
    void shouldReportSiteSetup_whenMultisitesEnabled() {
        CarlosProperties.getInstance().setProperty("multisites", "on");

        assertThat(AppointmentLocationResolver.isSiteSetupConfigured()).isTrue();
    }

    @Test
    void shouldReportSiteSetup_whenScheduleSiteConfigured() {
        CarlosProperties.getInstance().setProperty("scheduleSiteID", "Main");

        assertThat(AppointmentLocationResolver.isSiteSetupConfigured()).isTrue();
    }

    @Test
    void shouldReportSiteSetup_onlyWhenCaisiUsesProgramLocations() {
        CarlosProperties properties = CarlosProperties.getInstance();
        properties.setProperty("ModuleNames", "Caisi");
        assertThat(AppointmentLocationResolver.isSiteSetupConfigured()).isFalse();

        properties.setProperty("useProgramLocation", "true");
        assertThat(AppointmentLocationResolver.isSiteSetupConfigured()).isTrue();
    }
}
