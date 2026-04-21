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
package io.github.carlos_emr.carlos.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Stale removed JSP reference tests")
class StaleRemovedJspReferenceTest {

    @Test
    @DisplayName("Appointment admin day should not link to removed PMmodule popup JSPs")
    void shouldNotReferenceRemovedPmmodulePopups_whenRenderingAppointmentAdminDay() throws IOException {
        String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/provider/appointmentprovideradminday.jsp"));

        assertThat(jsp)
                .doesNotContain("/PMmodule/createAnonymousClient.jsp")
                .doesNotContain("/PMmodule/createPEClient.jsp");
    }

    @Test
    @DisplayName("SearchDrug3 should not reference removed TreatmentMyD JSP")
    void shouldNotReferenceRemovedTreatmentMyDJsp_whenRenderingSearchDrug3() throws IOException {
        String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/rx/SearchDrug3.jsp"));

        assertThat(jsp).doesNotContain("/rx/TreatmentMyD.jsp");
    }
}
