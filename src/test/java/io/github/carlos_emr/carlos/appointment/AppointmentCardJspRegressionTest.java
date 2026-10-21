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

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #5134: Print Card and the Preferences page read the appointment card lines through
 * {@link AppointmentCardContact}, so the card prints what Preferences saves.
 *
 * @since 2026-10-21
 */
@Tag("unit")
@Tag("appointment")
class AppointmentCardJspRegressionTest {

    @Test
    @DisplayName("should print the card lines Preferences saves")
    void shouldResolveTheCardLines_throughAppointmentCardContact() throws IOException {
        String card = read("WEB-INF/jsp/appointment/appointmentviewrecordcard.jsp");

        assertThat(card).contains("AppointmentCardContact.resolve(");
        // The card used to read only these keys, which nothing has written since June 2025.
        assertThat(card).doesNotContain("\"APPT_CARD_");
    }

    @Test
    @DisplayName("should show the values the card prints on the Preferences page")
    void shouldShowTheCardLines_onThePreferencesPage() throws IOException {
        String preferences = read("WEB-INF/jsp/provider/providerpreference.jsp");

        assertThat(preferences)
                .contains("AppointmentCardContact.shownValue(props, AppointmentCardContact.Field.NAME)")
                .contains("AppointmentCardContact.shownValue(props, AppointmentCardContact.Field.PHONE)")
                .contains("AppointmentCardContact.shownValue(props, AppointmentCardContact.Field.FAX)");
    }

    private static String read(String webappPath) throws IOException {
        return Files.readString(projectRoot().resolve("src/main/webapp").resolve(webappPath), StandardCharsets.UTF_8);
    }

    private static Path projectRoot() {
        try {
            Path location = Path.of(AppointmentCardJspRegressionTest.class
                    .getProtectionDomain().getCodeSource().getLocation().toURI());
            for (Path current = Files.isRegularFile(location) ? location.getParent() : location;
                 current != null; current = current.getParent()) {
                if (Files.isDirectory(current.resolve("src/main/webapp"))) {
                    return current;
                }
            }
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Unable to resolve test class location", e);
        }
        Path workingDirectory = Path.of(System.getProperty("user.dir"));
        if (Files.isDirectory(workingDirectory.resolve("src/main/webapp"))) {
            return workingDirectory;
        }
        throw new IllegalStateException("Unable to locate CARLOS EMR project root from test classpath");
    }
}
