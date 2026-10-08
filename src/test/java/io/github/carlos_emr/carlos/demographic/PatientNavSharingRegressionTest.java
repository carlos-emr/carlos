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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The master record and the patient portal page show one navigation (patient-nav.jsp), which keeps
 * the record's privilege checks and adds each target page's own: a link the user cannot open is not
 * shown on either page.
 *
 * @since 2026-10-08
 */
@Tag("unit")
@Tag("fast")
@DisplayName("Patient navigation shared by the record and the portal page")
class PatientNavSharingRegressionTest {

    private static final Path JSP = Path.of("src/main/webapp/WEB-INF/jsp/demographic");
    private static final String INCLUDE = "<jsp:include page=\"/WEB-INF/jsp/demographic/patient-nav.jsp\"/>";
    private static final Pattern SECURITY_TAG = Pattern.compile(
            "<security:oscarSec\\b[^>]*objectName=\"([^\"]+)\"[^>]*rights=\"([^\"]+)\"[^>]*>|</security:oscarSec>");

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
    @DisplayName("should show each link only under the record's check and its target page's own")
    void shouldGuardEachLink_withRecordAndTargetChecks() throws IOException {
        String nav = read("patient-nav.jsp");
        assertThat(guardsOf(nav, "nav.billingHistoryUrl")).containsExactly("_billing r");
        assertThat(guardsOf(nav, "nav.invoiceListUrl")).containsExactly("_billing r", "_billing w");
        assertThat(guardsOf(nav, "nav.createInvoiceUrl")).containsExactly("_billing r");
        assertThat(guardsOf(nav, "nav.consultationsUrl")).containsExactly("_eChart r");
        assertThat(guardsOf(nav, "nav.prescriptionsUrl")).containsExactly("_rx r");
        assertThat(guardsOf(nav, "nav.echartUrl")).containsExactly("_eChart r");
        assertThat(guardsOf(nav, "nav.preventionsUrl")).containsExactly("_eChart r", "_prevention r");
        assertThat(guardsOf(nav, "nav.ticklerUrl")).containsExactly("_tickler r");
        assertThat(guardsOf(nav, "nav.portalUrl")).containsExactly("_portal.invite,_portal.account r");
        assertThat(guardsOf(nav, "nav.getArFormUrl('AR1')")).containsExactly("_form r");
        assertThat(guardsOf(nav, "nav.inboxManagerUrl")).containsExactly("_hrm r");
        assertThat(guardsOf(nav, "nav.documentsUrl")).containsExactly("_edoc r");
        assertThat(guardsOf(nav, "nav.documentBrowserUrl")).containsExactly("_edoc r");
        assertThat(guardsOf(nav, "nav.eformsUrl")).containsExactly("_eform r");
        // The record's own pages: the user is already on the patient's record or portal page.
        assertThat(guardsOf(nav, "nav.appointmentHistoryUrl")).isEmpty();
        assertThat(guardsOf(nav, "nav.waitingListUrl")).isEmpty();
        assertThat(nav).contains("roleName=\"${nav.roleName}\"").doesNotContain("<%=");
    }

    @Test
    @DisplayName("should encode every target for its HTML attribute")
    void shouldEncodeEveryTarget_forItsAttribute() throws IOException {
        String nav = read("patient-nav.jsp");
        assertThat(nav.split("href=\"\\$\\{").length - 1)
                .isEqualTo(nav.split("href=\"\\$\\{carlos:forHtmlAttribute\\(").length - 1);
    }

    /** The privilege checks (object and right) around the first use of {@code marker}, outermost first. */
    private static List<String> guardsOf(String nav, String marker) {
        int at = nav.indexOf(marker);
        assertThat(at).as("link %s", marker).isNotNegative();
        Deque<String> open = new ArrayDeque<>();
        Matcher tag = SECURITY_TAG.matcher(nav);
        while (tag.find() && tag.start() < at) {
            if (tag.group(1) != null) {
                open.addLast(tag.group(1) + " " + tag.group(2));
            } else {
                open.removeLast();
            }
        }
        return new ArrayList<>(open);
    }
}
