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
package io.github.carlos_emr.carlos.appointment;

import io.github.carlos_emr.carlos.appointment.AppointmentCardContact.Field;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The appointment card's contact lines (issue #5134): what Preferences saves, else the older
 * APPT_CARD_* value, else the card's default; a blank saved value counts as not set.
 *
 * @since 2026-10-21
 */
@DisplayName("Appointment card contact lines")
@Tag("unit")
@Tag("appointment")
class AppointmentCardContactUnitTest {

    private static final String PROVIDER = "999998";

    private UserPropertyDAO userProperties;

    @BeforeEach
    void setUp() {
        userProperties = mock(UserPropertyDAO.class);
        when(userProperties.getProp(anyString(), anyString())).thenReturn(null);
    }

    private void saved(String key, String value) {
        UserProperty property = new UserProperty();
        property.setProviderNo(PROVIDER);
        property.setName(key);
        property.setValue(value);
        when(userProperties.getProp(PROVIDER, key)).thenReturn(property);
    }

    private AppointmentCardContact resolve() {
        return AppointmentCardContact.resolve(userProperties, PROVIDER, "Dr. Ann Default", "613-555-0100", "613-555-0101");
    }

    @Test
    void shouldPrintWhatPreferencesSaved_whenEveryLineIsSet() {
        saved("appointmentCardName", "FAKE Pw Clinic");
        saved("appointmentCardPhone", "613-555-0144");
        saved("appointmentCardFax", "613-555-0145");

        assertThat(resolve()).isEqualTo(new AppointmentCardContact("FAKE Pw Clinic", "613-555-0144", "613-555-0145"));
    }

    @Test
    void shouldPrintTheDefaults_whenNothingIsSaved() {
        assertThat(resolve()).isEqualTo(new AppointmentCardContact("Dr. Ann Default", "613-555-0100", "613-555-0101"));
    }

    @Test
    void shouldPrintTheOlderValues_whenOnlyAnUpgradedInstallHoldsThem() {
        saved("APPT_CARD_NAME", "FAKE Old Clinic");
        saved("APPT_CARD_PHONE", "613-555-0122");
        saved("APPT_CARD_FAX", "613-555-0123");

        assertThat(resolve()).isEqualTo(new AppointmentCardContact("FAKE Old Clinic", "613-555-0122", "613-555-0123"));
    }

    @Test
    void shouldPreferTheSavedValue_overTheOlderOne() {
        saved("appointmentCardName", "FAKE New Clinic");
        saved("APPT_CARD_NAME", "FAKE Old Clinic");

        assertThat(resolve().name()).isEqualTo("FAKE New Clinic");
    }

    @Test
    void shouldKeepTheOlderValue_whenSaveAllStoredABlankBox() {
        // Save All writes "" for an empty box, so a blank saved row must not hide the older value.
        saved("appointmentCardName", "");
        saved("APPT_CARD_NAME", "FAKE Old Clinic");
        saved("appointmentCardPhone", "   ");
        saved("APPT_CARD_PHONE", "613-555-0122");

        AppointmentCardContact card = resolve();

        assertThat(card.name()).isEqualTo("FAKE Old Clinic");
        assertThat(card.phone()).isEqualTo("613-555-0122");
        assertThat(card.fax()).isEqualTo("613-555-0101");
    }

    @Test
    void shouldResolveEachLineOnItsOwn_whenOnlySomeAreSaved() {
        saved("appointmentCardPhone", "613-555-0144");
        saved("APPT_CARD_FAX", "613-555-0123");

        assertThat(resolve()).isEqualTo(new AppointmentCardContact("Dr. Ann Default", "613-555-0144", "613-555-0123"));
    }

    @Test
    void shouldPrintTheDefault_whenTheSavedValueIsNull() {
        saved("appointmentCardName", null);

        assertThat(resolve().name()).isEqualTo("Dr. Ann Default");
    }

    @Test
    void shouldShowTheValueTheCardPrints_onThePreferencesPage() {
        Map<String, String> properties = new HashMap<>();
        properties.put("appointmentCardName", "");
        properties.put("APPT_CARD_NAME", "FAKE Old Clinic");
        properties.put("appointmentCardPhone", "613-555-0144");
        properties.put("APPT_CARD_PHONE", "613-555-0122");

        assertThat(AppointmentCardContact.shownValue(properties, Field.NAME)).isEqualTo("FAKE Old Clinic");
        assertThat(AppointmentCardContact.shownValue(properties, Field.PHONE)).isEqualTo("613-555-0144");
        assertThat(AppointmentCardContact.shownValue(properties, Field.FAX)).isEmpty();
    }

    @Test
    void shouldShowTheSameValue_whenReadFromTheDao() {
        saved("appointmentCardFax", "");
        saved("APPT_CARD_FAX", "613-555-0123");

        assertThat(AppointmentCardContact.shownValue(userProperties, PROVIDER, Field.FAX)).isEqualTo("613-555-0123");
        assertThat(AppointmentCardContact.shownValue(userProperties, PROVIDER, Field.NAME)).isEmpty();
    }

    @Test
    void shouldNameTheKeysBothPagesUse_forEachLine() {
        assertThat(Field.NAME.key()).isEqualTo("appointmentCardName");
        assertThat(Field.PHONE.key()).isEqualTo("appointmentCardPhone");
        assertThat(Field.FAX.key()).isEqualTo("appointmentCardFax");
        assertThat(Field.NAME.legacyKey()).isEqualTo("APPT_CARD_NAME");
        assertThat(Field.PHONE.legacyKey()).isEqualTo("APPT_CARD_PHONE");
        assertThat(Field.FAX.legacyKey()).isEqualTo("APPT_CARD_FAX");
    }
}
