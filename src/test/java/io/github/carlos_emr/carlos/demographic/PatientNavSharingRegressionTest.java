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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
            "<security:oscarSec(?=\\s|>)([^<>]*+)>|</security:oscarSec>");
    private static final Pattern SECURITY_ATTRIBUTE = Pattern.compile(
            "\\G\\s++([A-Za-z_:][A-Za-z0-9_:.-]*+)\\s*+=\\s*+(?:\"([^\"]*+)\"|'([^']*+)')");
    private static final String NESTED_GUARDS = """
            <security:oscarSec rights = 'r' roleName="doctor" objectName = "_billing">
                MARKER
                <security:oscarSec objectName="_billing" roleName='doctor' rights="w">
                    MARKER
                </security:oscarSec>
            </security:oscarSec>
            MARKER
            """;

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
        // Ontario's billing form needs read; BC's (the second link) also needs write.
        assertThat(guardsOf(nav, "nav.createInvoiceUrl", 1)).containsExactly("_billing r");
        assertThat(guardsOf(nav, "nav.createInvoiceUrl", 2)).containsExactly("_billing r", "_billing w");
        assertThat(guardsOf(nav, "nav.consultationsUrl")).containsExactly("_eChart r", "_con r");
        assertThat(guardsOf(nav, "nav.prescriptionsUrl")).containsExactly("_rx r");
        assertThat(guardsOf(nav, "nav.echartUrl")).containsExactly("_eChart r");
        assertThat(guardsOf(nav, "nav.preventionsUrl")).containsExactly("_eChart r", "_prevention r");
        assertThat(guardsOf(nav, "nav.ticklerUrl")).containsExactly("_tickler r");
        assertThat(guardsOf(nav, "nav.portalUrl")).containsExactly("_portal.invite,_portal.account r");
        assertThat(guardsOf(nav, "nav.getArFormUrl('AR1')")).containsExactly("_form r");
        assertThat(guardsOf(nav, "nav.inboxManagerUrl")).containsExactly("_edoc r");
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

    @Test
    @DisplayName("should encode the eligibility link's data values and every title for their attributes")
    void shouldEncodeDataValuesAndTitles_forTheirAttributes() throws IOException {
        String nav = read("patient-nav.jsp");
        assertThat(nav).contains("data-nav-eligibility=\"${carlos:forHtmlAttribute(nav.eligibilityUrl)}\"",
                "data-demographic-no=\"${carlos:forHtmlAttribute(nav.demographicNo)}\"");
        // Bill patient on both Create invoice links, and E-Chart.
        assertThat(nav.split("title=\"").length - 1).isEqualTo(3);
        assertThat(nav.split("title=\"\\$\\{carlos:forHtmlAttribute\\(").length - 1).isEqualTo(3);
    }

    @Test
    @DisplayName("should link back to the record and mark itself, not link to itself, on the portal page")
    void shouldLinkToRecordAndMarkItself_onThePortalPage() throws IOException {
        String nav = read("patient-nav.jsp");
        String recordLink = between(nav, "<c:if test=\"${nav.onPortalPage}\">", "</c:if>");
        String portalEntry = between(nav, "<c:when test=\"${nav.onPortalPage}\">", "</c:when>");

        assertThat(recordLink).contains("href=\"${carlos:forHtmlAttribute(nav.recordUrl)}\"");
        assertThat(portalEntry).contains("aria-current=\"page\"").doesNotContain("nav.portalUrl", "<a ");
        // The marker must win over the link: otherwise the portal page links to itself while the portal is on.
        assertThat(nav.indexOf("<c:when test=\"${nav.onPortalPage}\">"))
                .isLessThan(nav.indexOf("<c:when test=\"${nav.portalSwitchedOn}\">"));
    }

    @Test
    @DisplayName("should preserve nested guards when attribute order and quoting vary")
    void shouldPreserveNestedGuards_withAttributeOrderAndQuoting() {
        assertThat(guardsOf(NESTED_GUARDS, "MARKER", 1)).containsExactly("_billing r");
        assertThat(guardsOf(NESTED_GUARDS, "MARKER", 2)).containsExactly("_billing r", "_billing w");
        assertThat(guardsOf(NESTED_GUARDS, "MARKER", 3)).isEmpty();
    }

    @Test
    @DisplayName("should reject a guard with a missing right rather than ignore it")
    void shouldRejectIncompleteGuard_withMissingRight() {
        String nav = "<security:oscarSec objectName=\"_billing\">MARKER</security:oscarSec>";

        assertThatThrownBy(() -> guardsOf(nav, "MARKER"))
                .isInstanceOf(AssertionError.class).hasMessageContaining("rights");
    }

    @Test
    @DisplayName("should reject duplicate guard attributes rather than choose one")
    void shouldRejectAmbiguousGuard_withDuplicateAttribute() {
        String nav = "<security:oscarSec objectName=\"_billing\" rights=\"r\" rights=\"w\">MARKER</security:oscarSec>";

        assertThatThrownBy(() -> guardsOf(nav, "MARKER"))
                .isInstanceOf(AssertionError.class).hasMessageContaining("duplicate security attribute rights");
    }

    @Test
    @DisplayName("should preserve the same guard results after many unterminated tag prefixes")
    void shouldPreserveGuards_withLargeUnterminatedPrefix() {
        String nav = "<security:oscarSec ".repeat(50_000) + NESTED_GUARDS;

        assertTimeout(Duration.ofSeconds(5), () -> {
            assertThat(guardsOf(nav, "MARKER", 1)).containsExactly("_billing r");
            assertThat(guardsOf(nav, "MARKER", 2)).containsExactly("_billing r", "_billing w");
            assertThat(guardsOf(nav, "MARKER", 3)).isEmpty();
        });
    }

    /** The text between the first {@code start} and the next {@code end}. */
    private static String between(String text, String start, String end) {
        int from = text.indexOf(start);
        assertThat(from).as("start marker %s", start).isNotNegative();
        int to = text.indexOf(end, from);
        assertThat(to).as("end marker %s", end).isGreaterThan(from);
        return text.substring(from, to);
    }

    /** The privilege checks (object and right) around the first use of {@code marker}, outermost first. */
    private static List<String> guardsOf(String nav, String marker) {
        return guardsOf(nav, marker, 1);
    }

    /** The privilege checks around the {@code occurrence}-th use of {@code marker} (1-based). */
    private static List<String> guardsOf(String nav, String marker, int occurrence) {
        int at = -1;
        for (int found = 0; found < occurrence; found++) {
            at = nav.indexOf(marker, at + 1);
            assertThat(at).as("use %d of %s", found + 1, marker).isNotNegative();
        }
        Deque<String> open = new ArrayDeque<>();
        Matcher tag = SECURITY_TAG.matcher(nav);
        while (tag.find() && tag.start() < at) {
            if (tag.group(1) != null) {
                open.addLast(guardOf(tag.group(1)));
            } else {
                assertThat(open).as("matching opening security tag").isNotEmpty();
                open.removeLast();
            }
        }
        return new ArrayList<>(open);
    }

    /** Parse each quoted attribute once; do not search inside another attribute's value. */
    private static String guardOf(String attributes) {
        Map<String, String> values = new HashMap<>();
        Matcher attribute = SECURITY_ATTRIBUTE.matcher(attributes);
        int end = 0;
        while (attribute.find()) {
            String name = attribute.group(1);
            String value = attribute.group(2) != null ? attribute.group(2) : attribute.group(3);
            assertThat(values.put(name, value)).as("duplicate security attribute %s", name).isNull();
            end = attribute.end();
        }
        assertThat(attributes.substring(end)).as("unparsed security attributes").isBlank();
        assertThat(values).containsKeys("objectName", "rights");
        assertThat(values.get("objectName")).isNotEmpty();
        assertThat(values.get("rights")).isNotEmpty();
        return values.get("objectName") + " " + values.get("rights");
    }
}
