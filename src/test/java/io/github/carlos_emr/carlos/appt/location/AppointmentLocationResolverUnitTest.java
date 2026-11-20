/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.appt.location;

import static io.github.carlos_emr.carlos.appt.location.AppointmentLocationTestItems.item;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    void shouldRefuseDeactivatedItem_whenNewBookingPostsIt() {
        // The booking form may have been opened before an administrator disabled this location.
        Appointment appointment = new Appointment();

        assertThatThrownBy(() -> resolver.applyPostedLocation(appointment, "13", "Satellite office"))
                .isInstanceOf(AppointmentLocationUnavailableException.class);

        assertThat(appointment.getLocationCode()).isNull();
        assertThat(appointment.getLocation()).isNull();
    }

    @Test
    void shouldKeepSavedLocation_whenAnotherBookingPostsDeactivatedItem() {
        // A form opened before the location was disabled changes nothing rather than losing the
        // booking's location.
        Appointment appointment = booked("Virtual", 11);

        assertThatThrownBy(() -> resolver.applyPostedLocation(appointment, "13", ""))
                .isInstanceOf(AppointmentLocationUnavailableException.class);

        assertThat(appointment.getLocationCode()).isEqualTo(11);
        assertThat(appointment.getLocation()).isEqualTo("Virtual");
    }

    @Test
    void shouldKeepSavedLabel_whenOwnItemRenamedSinceBooking() {
        // Item 11 is now called "Virtual"; the booking keeps the name it was made with.
        Appointment appointment = booked("Room A", 11);

        resolver.applyPostedLocation(appointment, "11", "");

        assertThat(appointment.getLocationCode()).isEqualTo(11);
        assertThat(appointment.getLocation()).isEqualTo("Room A");
        verify(lookupListDao, never()).findByName(anyString());
    }

    @Test
    void shouldRefusePostedItem_whenSiteSetupConfigured() {
        AppointmentLocationResolver siteResolver = new AppointmentLocationResolver(lookupListDao, () -> true);
        Appointment appointment = new Appointment();

        assertThatThrownBy(() -> siteResolver.applyPostedLocation(appointment, "11", "Main site"))
                .isInstanceOf(AppointmentLocationUnavailableException.class);

        assertThat(appointment.getLocationCode()).isNull();
        assertThat(appointment.getLocation()).isNull();
    }

    @Test
    void shouldSaveSiteText_whenBlankCodePassedThroughInSiteSetup() {
        // The patient-search pages post locationCode="" even when the form offered sites.
        AppointmentLocationResolver siteResolver = new AppointmentLocationResolver(lookupListDao, () -> true);
        Appointment appointment = booked("Main site", null);

        siteResolver.applyPostedLocation(appointment, "", "North site");

        assertThat(appointment.getLocationCode()).isNull();
        assertThat(appointment.getLocation()).isEqualTo("North site");
    }

    @Test
    void shouldLeaveSavedLocation_whenStaleFormRepostsItsCodeOutsideLocationMode() {
        AppointmentLocationResolver siteResolver = new AppointmentLocationResolver(lookupListDao, () -> true);
        Appointment appointment = booked("Virtual", 11);

        siteResolver.applyPostedLocation(appointment, "11", "");

        assertThat(appointment.getLocationCode()).isEqualTo(11);
        assertThat(appointment.getLocation()).isEqualTo("Virtual");
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
    void shouldKeepSavedLocation_whenPostedIdIsNotAnItemOfTheList() {
        // An id from another lookup list (or one made up) never becomes a location code.
        Appointment appointment = booked("Basement", null);

        assertThatThrownBy(() -> resolver.applyPostedLocation(appointment, "4242", "Virtual"))
                .isInstanceOf(AppointmentLocationUnavailableException.class);

        assertThat(appointment.getLocationCode()).isNull();
        assertThat(appointment.getLocation()).isEqualTo("Basement");
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
    void shouldTreatSameTextAndCode_asSameLocation() {
        assertThat(AppointmentLocationResolver.isSameLocation(booked("Virtual", 11), booked("Virtual", 11))).isTrue();
        assertThat(AppointmentLocationResolver.isSameLocation(booked(null, null), booked("", null))).isTrue();
    }

    @Test
    void shouldTreatTypedTextLinkedToItsItem_asSameLocation() {
        assertThat(AppointmentLocationResolver.isSameLocation(booked(" virtual ", null), booked("Virtual", 11))).isTrue();
    }

    @Test
    void shouldTreatAnotherPlace_asChangedLocation() {
        assertThat(AppointmentLocationResolver.isSameLocation(booked("Room 9", null), booked("Virtual", 11))).isFalse();
        assertThat(AppointmentLocationResolver.isSameLocation(booked("Virtual", 11), booked("Virtual", 15))).isFalse();
        assertThat(AppointmentLocationResolver.isSameLocation(booked("", null), booked("Virtual", 11))).isFalse();
        assertThat(AppointmentLocationResolver.isSameLocation(booked("Virtual", 11), booked("Virtual", null))).isFalse();
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
    void shouldRejectMalformedChoice_withoutClearingSavedLocation() {
        Appointment appointment = booked("Virtual", 11);

        assertThatThrownBy(() -> resolver.applyPostedLocation(appointment, "not-an-id", ""))
                .isInstanceOf(AppointmentLocationUnavailableException.class);

        assertThat(appointment.getLocationCode()).isEqualTo(11);
        assertThat(appointment.getLocation()).isEqualTo("Virtual");
    }

    @Test
    void shouldPreflightOnACopy_withoutChangingTheSavedAppointment() {
        Appointment saved = booked("Satellite office", 13);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("locationCode", "11");

        AppointmentLocationResolver.ResolvedLocation resolved =
                AppointmentLocationResolver.resolvePostedLocation(saved, request);

        assertThat(saved.getLocationCode()).isEqualTo(13);
        assertThat(saved.getLocation()).isEqualTo("Satellite office");
        // Applying a validated result does not re-read a list that may change partway through a group.
        when(lookupListDao.findByName(AppointmentLocationResolver.LIST_NAME)).thenReturn(null);
        Appointment replacement = new Appointment();
        resolved.applyTo(replacement);
        assertThat(replacement.getLocationCode()).isEqualTo(11);
        assertThat(replacement.getLocation()).isEqualTo("Virtual");
    }

    @Test
    void shouldGiveEveryGroupMemberTheEditedLocation_whenItsItemWasDisabledSince() {
        // Group Update (appointmentgrouprecords.jsp) resolves the edit form once, against the
        // appointment it came from, and applies that snapshot to every member it recreates: a
        // member booked elsewhere, and a provider ticked for the first time with no row at all.
        Appointment edited = booked("Satellite office", 13);
        Appointment bookedElsewhere = booked("Virtual", 11);
        Appointment newMember = new Appointment();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("locationCode", "13");
        request.addParameter("location", "");

        AppointmentLocationResolver.ResolvedLocation group = AppointmentLocationResolver.resolvePostedLocation(edited, request);
        group.applyTo(bookedElsewhere);
        group.applyTo(newMember);

        assertThat(group).isEqualTo(new AppointmentLocationResolver.ResolvedLocation(13, "Satellite office"));
        assertThat(List.of(bookedElsewhere, newMember)).allSatisfy(member -> {
            assertThat(member.getLocationCode()).isEqualTo(13);
            assertThat(member.getLocation()).isEqualTo("Satellite office");
        });
        assertThat(edited.getLocationCode()).isEqualTo(13);
    }

    @Test
    void shouldRefuseDisabledLocation_whenGroupIsBookedWithoutEditedAppointment() {
        // Add Group books new appointments only, so it resolves against nothing: a location
        // disabled while the page was open refuses the whole group before any member is written.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("locationCode", "13");

        assertThatThrownBy(() -> AppointmentLocationResolver.resolvePostedLocation(null, request))
                .isInstanceOf(AppointmentLocationUnavailableException.class);
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
