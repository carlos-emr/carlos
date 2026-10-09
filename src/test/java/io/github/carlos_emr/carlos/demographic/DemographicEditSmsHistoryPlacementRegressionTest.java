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
package io.github.carlos_emr.carlos.demographic;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins where the patient record's "Text Messages" section (#3839) sits in {@code demographic/edit.jsp}.
 *
 * <p>The left column lists the appointment history link and, when the waiting list is turned on, the
 * waiting-list link under the same appointment heading. The SMS section brings its own heading, so it must
 * come after the waiting-list block closes; placed between the two, it pulled the waiting-list link under
 * "Text Messages".</p>
 *
 * @since 2026-10-08
 */
@DisplayName("Demographic edit JSP SMS history placement regression tests")
@Tag("unit")
@Tag("demographic")
@Tag("regression")
class DemographicEditSmsHistoryPlacementRegressionTest {

    private static final Path MASTER_JSP = Path.of("src/main/webapp/WEB-INF/jsp/demographic/edit.jsp");
    private static final String SMS_HISTORY_ROW = "id=\"sms_hx\"";
    private static final String APPOINTMENT_HISTORY_ROW = "id=\"appt_hx\"";
    private static final String WAITING_LIST_LINK = "/waitinglist/SetupDisplayPatientWaitingList";
    private static final String HEADER_ROW = "class=\"Header\"";
    private static final Pattern CLOSE_BLOCK = Pattern.compile("<%\\s*}\\s*%>");
    private static final String BILLING_SECTION = "objectName=\"_billing\"";

    @Test
    @DisplayName("should place the SMS history section after the waiting-list block and before billing")
    void shouldPlaceSmsSection_afterWaitingListBlock() throws Exception {
        String jsp = Files.readString(resolveProjectPath(MASTER_JSP), StandardCharsets.UTF_8);

        int waitingListLink = jsp.indexOf(WAITING_LIST_LINK);
        int waitingListClose = indexOf(CLOSE_BLOCK, jsp, waitingListLink);
        int smsHistoryRow = jsp.indexOf(SMS_HISTORY_ROW);
        int billingSection = jsp.indexOf(BILLING_SECTION, Math.max(waitingListClose, 0));

        assertThat(waitingListLink).as("waiting-list link").isPositive();
        assertThat(waitingListClose).as("end of the waiting-list block").isPositive();
        assertThat(billingSection).as("billing section after the waiting-list block").isPositive();
        assertThat(jsp.indexOf(SMS_HISTORY_ROW, smsHistoryRow + 1)).as("one SMS history row").isEqualTo(-1);
        assertThat(smsHistoryRow)
                .as("SMS history after the waiting-list block, so that link keeps its own heading")
                .isGreaterThan(waitingListClose)
                .isLessThan(billingSection);
    }

    @Test
    @DisplayName("should keep the waiting-list link under the appointment heading, with no other heading between")
    void shouldKeepWaitingListLink_underAppointmentHeading() throws Exception {
        String jsp = Files.readString(resolveProjectPath(MASTER_JSP), StandardCharsets.UTF_8);

        int appointmentHistoryRow = jsp.indexOf(APPOINTMENT_HISTORY_ROW);
        int waitingListLink = jsp.indexOf(WAITING_LIST_LINK);

        assertThat(appointmentHistoryRow).as("appointment history row").isPositive();
        assertThat(waitingListLink).as("waiting-list link after the appointment history row")
                .isGreaterThan(appointmentHistoryRow);
        assertThat(jsp.substring(appointmentHistoryRow, waitingListLink))
                .as("no section heading between the appointment history row and the waiting-list link")
                .doesNotContain(HEADER_ROW);
    }

    private static int indexOf(Pattern pattern, String text, int from) {
        if (from < 0) {
            return -1;
        }
        Matcher matcher = pattern.matcher(text);
        return matcher.find(from) ? matcher.start() : -1;
    }

    /**
     * Resolves against Maven's project directory when Maven set it, otherwise the working directory, like the
     * other source-scan regression tests.
     */
    private static Path resolveProjectPath(Path relativePath) {
        Path candidate = Path.of(System.getProperty(
                "maven.multiModuleProjectDirectory",
                System.getProperty("user.dir"))).toAbsolutePath().resolve(relativePath);
        if (!Files.exists(candidate)) {
            throw new IllegalStateException("Could not locate " + relativePath + " under the project directory");
        }
        return candidate;
    }
}
