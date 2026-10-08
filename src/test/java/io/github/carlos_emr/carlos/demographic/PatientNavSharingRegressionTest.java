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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The master record and the patient portal page show one navigation (patient-nav.jsp), which keeps
 * the record's privilege checks: a link the user cannot open is not shown on either page.
 */
@Tag("unit")
@Tag("fast")
@DisplayName("Patient navigation shared by the record and the portal page")
class PatientNavSharingRegressionTest {

    private static final Path JSP = Path.of("src/main/webapp/WEB-INF/jsp/demographic");
    private static final String INCLUDE = "<jsp:include page=\"/WEB-INF/jsp/demographic/patient-nav.jsp\"/>";

    private static String read(String name) throws IOException {
        return Files.readString(JSP.resolve(name), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("should include the shared navigation on the record and the portal page")
    void shouldIncludeSharedNavigation_onBothPages() throws IOException {
        assertThat(read("edit.jsp")).contains(INCLUDE);
        assertThat(read("portalManage.jsp")).contains(INCLUDE);
    }

    @Test
    @DisplayName("should keep no inline copy of the navigation on the record")
    void shouldKeepNoInlineNavigation_onTheRecord() throws IOException {
        String record = read("edit.jsp");
        assertThat(record).doesNotContain("DemographicApptHistory", "efmpatientformlist", "ViewTicklerMain",
                "ViewPreventionIndex", "ViewDisplayDemographicConsultationRequests");
    }

    @Test
    @DisplayName("should keep the record's privilege checks around the guarded links")
    void shouldKeepPrivilegeChecks_aroundGuardedLinks() throws IOException {
        String nav = read("patient-nav.jsp");
        assertThat(between(nav, "objectName=\"_billing\" rights=\"r\">", "</security:oscarSec>"))
                .contains("billingHistoryUrl", "invoiceListUrl", "createInvoiceUrl");
        assertThat(between(nav, "objectName=\"_eChart\" rights=\"r\"", "</security:oscarSec>"))
                .contains("echartUrl", "preventionsUrl");
        assertThat(between(nav, "objectName=\"_portal.invite,_portal.account\" rights=\"r\">", "</security:oscarSec>"))
                .contains("portalUrl");
        assertThat(nav).contains("roleName=\"${nav.roleName}\"").doesNotContain("<%=");
    }

    @Test
    @DisplayName("should encode every target for its HTML attribute")
    void shouldEncodeEveryTarget_forItsAttribute() throws IOException {
        String nav = read("patient-nav.jsp");
        assertThat(nav.split("href=\"\\$\\{").length - 1)
                .isEqualTo(nav.split("href=\"\\$\\{carlos:forHtmlAttribute\\(").length - 1);
    }

    private static String between(String text, String start, String end) {
        int from = text.indexOf(start);
        assertThat(from).as("block starting %s", start).isNotNegative();
        int to = text.indexOf(end, from);
        return text.substring(from, to);
    }
}
