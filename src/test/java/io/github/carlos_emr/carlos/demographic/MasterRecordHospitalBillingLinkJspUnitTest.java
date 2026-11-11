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

import java.io.IOException;
import java.net.URISyntaxException;
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
 * The Master Record's Hospital Billing link is the only opener of Ontario shortcut billing
 * ({@code billing/CA/ON/billingShortcutPg1View}). #654 removed it with the other sidebar billing
 * links, which left the shortcut pages unreachable (#5055). These tests keep it in the
 * Ontario-only, {@code _billing}-gated part of the sidebar, pointed at the mapped route.
 *
 * @since 2026-10-22
 */
@Tag("unit")
@Tag("demographic")
@Tag("billing")
@DisplayName("Master Record Hospital Billing link")
class MasterRecordHospitalBillingLinkJspUnitTest {

    private static final String ROUTE = "billing/CA/ON/billingShortcutPg1View";
    private static final Pattern URL_TAG = Pattern.compile(
            "(?s)<c:url var=\"masterRecordHospitalBilling\" value=\"([^\"]+)\">(.*?)</c:url>");
    private static final Pattern CLOSING_SCRIPTLET = Pattern.compile("<%\\s*}\\s*%>");

    private static Path projectRoot() throws URISyntaxException {
        Path current = Path.of(MasterRecordHospitalBillingLinkJspUnitTest.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        while (current != null && !Files.isDirectory(current.resolve("src/main/webapp"))) {
            current = current.getParent();
        }
        assertThat(current).as("project root with src/main/webapp").isNotNull();
        return current;
    }

    private static String read(String relative) throws IOException, URISyntaxException {
        return Files.readString(projectRoot().resolve(relative), StandardCharsets.UTF_8);
    }

    private static String editJsp() throws IOException, URISyntaxException {
        return read("src/main/webapp/WEB-INF/jsp/demographic/edit.jsp");
    }

    /** The sidebar's Billing section: the body of the {@code _billing} read gate. */
    private static String billingSection(String jsp) {
        int start = jsp.indexOf("objectName=\"_billing\" rights=\"r\">");
        assertThat(start).as("_billing read gate in edit.jsp").isNotNegative();
        int end = jsp.indexOf("</security:oscarSec>", start);
        assertThat(end).as("end of the _billing read gate").isGreaterThan(start);
        return jsp.substring(start, end);
    }

    /** The second {@code "ON".equals(prov)} block of the Billing section, the one after Create Invoice. */
    private static String ontarioLinkBlock(String section) {
        int invoice = section.indexOf("msgCreateInvoice");
        assertThat(invoice).as("Create Invoice link in the Billing section").isNotNegative();
        int start = section.indexOf("if (\"ON\".equals(prov)) {", invoice);
        assertThat(start).as("Ontario-only block after Create Invoice").isNotNegative();
        Matcher close = CLOSING_SCRIPTLET.matcher(section);
        assertThat(close.find(start)).as("end of the Ontario-only block").isTrue();
        return section.substring(start, close.start());
    }

    @Test
    void shouldOfferHospitalBilling_inTheOntarioBlockOfTheBillingSection() throws Exception {
        String block = ontarioLinkBlock(billingSection(editJsp()));

        assertThat(block)
                .contains("id=\"hospitalBillingLink\"")
                .contains("href=\"${carlos:forHtmlAttribute(masterRecordHospitalBilling)}\"")
                .contains("<fmt:message key=\"demographic.demographiceditdemographic.msgHospitalBilling\"/>");
    }

    @Test
    void shouldTargetTheShortcutPageOneRoute_notTheRetiredJsp() throws Exception {
        String jsp = editJsp();
        Matcher url = URL_TAG.matcher(jsp);

        assertThat(url.find()).as("c:url for the Hospital Billing link").isTrue();
        assertThat(url.group(1)).isEqualTo("/" + ROUTE);
        assertThat(jsp).doesNotContain("billingShortcutPg1.jsp");
    }

    @Test
    void shouldPassTheParametersPageOneReads_toTheShortcutRoute() throws Exception {
        Matcher url = URL_TAG.matcher(editJsp());
        assertThat(url.find()).isTrue();
        String params = url.group(2);

        // BillingShortcutPg1ViewModelAssembler reads these; a missing demographic_no opens an
        // empty bill, and billForm picks the service-code grid.
        assertThat(params)
                .contains("<c:param name=\"demographic_no\" value='<%= String.valueOf(demographic.getDemographicNo()) %>'/>")
                .contains("<c:param name=\"demographic_name\"")
                .contains("<c:param name=\"billForm\" value='<%= oscarProps.getProperty(\"hospital_view\", "
                        + "oscarProps.getProperty(\"default_view\")) %>'/>")
                .contains("<c:param name=\"appointment_no\" value=\"0\"/>")
                .contains("<c:param name=\"apptProvider_no\" value=\"none\"/>")
                .contains("<c:param name=\"appointment_date\" value='<%= dateString %>'/>")
                .contains("<c:param name=\"providerview\" value='<%= demographic.getProviderNo() %>'/>");
    }

    @Test
    void shouldMapTheLinkedRoute_toTheShortcutPageOneGate() throws Exception {
        String struts = read("src/main/webapp/WEB-INF/classes/struts-billing.xml");

        assertThat(struts).containsPattern(Pattern.compile(
                "(?s)<action name=\"" + Pattern.quote(ROUTE) + "\"\\s+class=\""
                        + Pattern.quote("io.github.carlos_emr.carlos.billings.ca.on.web.ViewBillingShortcutPg1View2Action")
                        + "\">\\s*<result name=\"success\">/WEB-INF/jsp/billing/CA/ON/billingShortcutPg1.jsp</result>"));
    }
}
