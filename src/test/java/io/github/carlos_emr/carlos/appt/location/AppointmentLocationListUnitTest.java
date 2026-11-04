/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.appt.location;

import static io.github.carlos_emr.carlos.appt.location.AppointmentLocationTestItems.item;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.LookupListItem;

/**
 * The Location List snapshot (#3982): when the booking screens offer it, which items each screen
 * offers and in what order, and what an edit screen starts on for a saved booking.
 */
@DisplayName("AppointmentLocationList")
@Tag("unit")
@Tag("appointment")
class AppointmentLocationListUnitTest {

    private final LookupListItem roomTwo = item(12, "Room 2", 2, true);
    private final LookupListItem virtual = item(11, "Virtual", 1, true);
    private final LookupListItem satellite = item(13, "Satellite office", 3, false);
    private final LookupListItem annex = item(14, "annex", 3, false);

    private AppointmentLocationList list(boolean siteSetup) {
        return AppointmentLocationList.of(List.of(roomTwo, satellite, virtual, annex), siteSetup);
    }

    @Test
    void shouldBeLocationMode_whenAnItemIsActiveAndNoSiteSetup() {
        assertThat(list(false).isLocationMode()).isTrue();
    }

    @Test
    void shouldNotBeLocationMode_whenSiteSetupConfigured() {
        assertThat(list(true).isLocationMode()).isFalse();
        assertThat(list(true).isSiteSetup()).isTrue();
    }

    @Test
    void shouldNotBeLocationMode_whenNoItemIsActive() {
        assertThat(AppointmentLocationList.of(List.of(satellite), false).isLocationMode()).isFalse();
        assertThat(AppointmentLocationList.of(null, false).isLocationMode()).isFalse();
    }

    @Test
    void shouldOfferActiveItems_inDisplayOrderWithIdTieBreak() {
        LookupListItem sameOrder = item(10, "Lab", 2, true);
        AppointmentLocationList locations = AppointmentLocationList.of(List.of(roomTwo, sameOrder, virtual), false);

        assertThat(locations.getActiveItems()).containsExactly(virtual, sameOrder, roomTwo);
    }

    @Test
    void shouldListActiveFirstThenInactiveByName_forSettingsPage() {
        assertThat(list(false).getItemsActiveFirst()).containsExactly(virtual, roomTwo, annex, satellite);
    }

    @Test
    void shouldFindItemsActiveOrNot_byId() {
        AppointmentLocationList locations = list(false);

        assertThat(locations.find(13)).isSameAs(satellite);
        assertThat(locations.find(99)).isNull();
        assertThat(locations.find(null)).isNull();
    }

    @Test
    void shouldKeepDeactivatedCurrentItem_amongEditChoices() {
        AppointmentLocationList locations = list(false);

        assertThat(locations.getChoices(13)).containsExactly(virtual, roomTwo, satellite);
        assertThat(locations.getChoices(null)).containsExactly(virtual, roomTwo);
        assertThat(locations.getChoices(99)).containsExactly(virtual, roomTwo);
    }

    @Test
    void shouldStartOnSavedItem_whenCodeNamesOne() {
        AppointmentLocationList.Choice choice = list(false).choiceFor(13, "Old satellite label");

        assertThat(choice.value()).isEqualTo("13");
        assertThat(choice.legacy()).isNull();
        assertThat(choice.code()).isEqualTo(13);
    }

    @Test
    void shouldLinkTypedText_toActiveItemWithSameName() {
        AppointmentLocationList.Choice choice = list(false).choiceFor(null, "  room 2 ");

        assertThat(choice.value()).isEqualTo("12");
        assertThat(choice.legacy()).isNull();
    }

    @Test
    void shouldOfferLegacyText_whenNoItemMatches() {
        AppointmentLocationList.Choice choice = list(false).choiceFor(null, "Basement");

        assertThat(choice.value()).isEqualTo(AppointmentLocationList.LEGACY_VALUE);
        assertThat(choice.legacy()).isEqualTo("Basement");
        assertThat(choice.code()).isNull();
    }

    @Test
    void shouldNotLinkTypedText_toInactiveItem() {
        AppointmentLocationList.Choice choice = list(false).choiceFor(null, "Satellite office");

        assertThat(choice.value()).isEqualTo(AppointmentLocationList.LEGACY_VALUE);
        assertThat(choice.legacy()).isEqualTo("Satellite office");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldStartOnNotSpecified_whenBookingHasNoLocation(String location) {
        AppointmentLocationList.Choice choice = list(false).choiceFor(null, location);

        assertThat(choice.value()).isEmpty();
        assertThat(choice.legacy()).isNull();
    }

    @Test
    void shouldDrawChip_forItemCodeActiveOrNot() {
        Appointment appointment = new Appointment();
        appointment.setLocationCode(13);
        assertThat(list(false).chipItemFor(appointment)).isSameAs(satellite);

        appointment.setLocationCode(12);
        assertThat(list(false).chipItemFor(appointment)).isSameAs(roomTwo);
    }

    @Test
    void shouldDrawNoChip_inSiteSetupOrWithoutCode() {
        Appointment coded = new Appointment();
        coded.setLocationCode(12);
        Appointment uncoded = new Appointment();
        uncoded.setLocation("Room 2");

        assertThat(list(true).chipItemFor(coded)).isNull();
        assertThat(list(false).chipItemFor(uncoded)).isNull();
        assertThat(list(false).chipItemFor(null)).isNull();
    }

    @Test
    void shouldFindOtherActiveItemWithName_ignoringCaseSpacesAndInactiveItems() {
        AppointmentLocationList locations = list(false);

        assertThat(locations.findActiveByName(" VIRTUAL ", null)).isSameAs(virtual);
        assertThat(locations.findActiveByName("Virtual", 11)).isNull();
        assertThat(locations.findActiveByName("Satellite office", null)).isNull();
        assertThat(locations.findActiveByName("", null)).isNull();
        assertThat(locations.findActiveByName(null, null)).isNull();
    }

    @ParameterizedTest
    @CsvSource(value = {"12, 12", " 7 , 7", "0, NULL", "-3, NULL", "legacy, NULL", "abc, NULL", "'', NULL",
            "99999999999, NULL"}, nullValues = "NULL")
    void shouldParsePostedCode_asPositiveIdOnly(String posted, Integer expected) {
        assertThat(AppointmentLocationList.parseCode(posted)).isEqualTo(expected);
    }

    @Test
    void shouldKeepPostedChoice_asOptionValueOnly() {
        assertThat(AppointmentLocationList.Choice.posted(" 12 ", null).value()).isEqualTo("12");
        assertThat(AppointmentLocationList.Choice.posted("legacy", "Basement"))
                .isEqualTo(new AppointmentLocationList.Choice(AppointmentLocationList.LEGACY_VALUE, "Basement"));
        assertThat(AppointmentLocationList.Choice.posted("12\"><script>", null).value()).isEmpty();
        assertThat(AppointmentLocationList.Choice.posted(null, null).value()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {
            // An active item is selected by its code.
            "12, Old room label, 12, NULL",
            // An item disabled since keeps its own name, not the stored snapshot.
            "13, Old satellite label, legacy, Satellite office",
            // Typed text naming an active item is linked to it, as the edit screen links it.
            "NULL, '  room 2 ', 12, NULL",
            "99, Virtual, 11, NULL",
            // Other text (or a code naming no item) is kept as a legacy option.
            "NULL, Basement, legacy, Basement",
            "99, Basement, legacy, Basement",
            "legacy, Basement, legacy, Basement",
            // No location at all starts on "Not specified".
            "NULL, NULL, '', NULL",
            "NULL, '   ', '', NULL"
    })
    void shouldStartPastedBooking_onItemOrLegacyOption(String code, String location, String value, String legacy) {
        AppointmentLocationList.Choice choice = list(false).pastedChoice(code, location);

        assertThat(choice.value()).isEqualTo(value);
        assertThat(choice.legacy()).isEqualTo(legacy);
    }

    @Test
    void shouldListInactiveItemWithoutLabel_withoutFailing() {
        LookupListItem unnamed = item(15, null, 1, false);
        AppointmentLocationList locations = AppointmentLocationList.of(List.of(roomTwo, satellite, unnamed), false);

        assertThat(locations.getItemsActiveFirst()).containsExactly(roomTwo, unnamed, satellite);
    }

    @Test
    void shouldParseNullCode_asNoCode() {
        assertThat(AppointmentLocationList.parseCode(null)).isNull();
    }
}
