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
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BC Administration ▸ Billing ▸ Manage Referral Doctors, as pages: the list ({@code billingManageReferralDoc.jsp})
 * and the Add / Update form ({@code billingAddReferralDoc.jsp}). These pin the markup that lets a doctor's Edit
 * update that doctor, the form's own checks run, and both pages work inside and outside the Administration panel.
 *
 * @since 2026-10-17
 */
@DisplayName("BC referral doctor pages")
@Tag("unit")
@Tag("billing")
class BcReferralDoctorPagesRegressionTest {

    private static final Path BC_JSP = Path.of("src", "main", "webapp", "WEB-INF", "jsp", "billing", "CA", "BC");

    private static String read(String page) throws IOException {
        return Files.readString(BC_JSP.resolve(page), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("should open a doctor's Update form filled from that doctor, each field labelled, and post its id back")
    void shouldFillUpdateForm_fromDoctorWithItsId() throws IOException {
        String jsp = read("billingAddReferralDoc.jsp");

        // Without the id the save took AddReferralDoc's add branch: a new row, or "Billing # already in use".
        assertThat(jsp)
                .contains("Billingreferral doctor = (id != null && id.matches(\"\\\\d{1,9}\")) ? billingReferralDao.getById(Integer.parseInt(id)) : null;")
                .contains("<input type=\"hidden\" name=\"id\" value=\"<carlos:encode value='<%= String.valueOf(doctor.getBillingreferralNo()) %>' context=\"htmlAttribute\"/>\"/>")
                .contains("<legend><%=(doctor == null) ? \"Add\" : \"Update\"%> Referral Doctor</legend>");
        for (String[] field : new String[][] {
                {"referral_no", "getReferralNo"}, {"last_name", "getLastName"}, {"first_name", "getFirstName"},
                {"specialty", "getSpecialty"}, {"address1", "getAddress1"}, {"address2", "getAddress2"},
                {"city", "getCity"}, {"province", "getProvince"}, {"postal", "getPostal"},
                {"phone", "getPhone"}, {"fax", "getFax"}}) {
            assertThat(jsp).contains("<label for=\"" + field[0] + "\">")
                    .contains("name=\"" + field[0] + "\" id=\"" + field[0] + "\"")
                    .contains("value=\"<carlos:encode value='<%= doctor == null ? null : doctor." + field[1] + "() %>' context=\"htmlAttribute\"/>\"");
        }
    }

    @Test
    @DisplayName("should take the two address lines in text boxes")
    void shouldTakeAddressLines_inTextBoxes() throws IOException {
        String jsp = read("billingAddReferralDoc.jsp");

        // As checkboxes the fields could only ever send "on", never the address typed for them.
        assertThat(jsp)
                .contains("<label for=\"address1\">Address 1:</label><input type=\"text\" name=\"address1\"")
                .contains("<label for=\"address2\">Address 2:</label><input type=\"text\" name=\"address2\"")
                .doesNotContain("type=\"checkbox\"");
    }

    @Test
    @DisplayName("should name the form the way its billing number check and the panel look it up")
    void shouldNameForm_forItsCheckAndThePanel() throws IOException {
        String jsp = read("billingAddReferralDoc.jsp");

        // The billing-number check reads the form by its name, AddReferralDocForm, and the panel's submit binding finds
        // it by its id, addReferralDocform. Without either, the check threw, the form posted unchecked, and Save left
        // the Administration panel.
        assertThat(jsp)
                .contains("document.AddReferralDocForm.referral_no")
                .contains("<form name=\"AddReferralDocForm\" id=\"addReferralDocform\" action=\"${pageContext.request.contextPath}/billing/CA/BC/AddReferralDoc\" method=\"post\"")
                .contains("onsubmit=\"return checkBillingNumber(event);\"")
                .contains("registerFormSubmit('addReferralDocform', 'dynamic-content');")
                .doesNotContain("style=\"addReferralDocform\"");
    }

    @Test
    @DisplayName("should stop the panel's own submit handler when the billing number is refused")
    void shouldStopPanelSubmit_whenBillingNumberRefused() throws IOException {
        String jsp = read("billingAddReferralDoc.jsp");

        // registerFormSubmit posts by AJAX from a submit handler of its own; returning false alone does not stop it.
        assertThat(jsp)
                .containsSubsequence("function refuseSubmit(event) {", "event.preventDefault();",
                        "event.stopImmediatePropagation();", "return false;")
                .containsSubsequence("alert(\"You must enter a Billing Number\");", "return refuseSubmit(event);",
                        "alert(\"Billing Number must be digits\");", "return refuseSubmit(event);");
    }

    @ParameterizedTest
    @ValueSource(strings = {"billingAddReferralDoc.jsp", "billingManageReferralDoc.jsp"})
    @DisplayName("should call the panel's registerFormSubmit only where the panel defines it")
    void shouldCallRegisterFormSubmit_onlyWhereDefined(String page) throws IOException {
        String jsp = read(page);

        // Only administration/index.jsp defines it; opened on their own the pages threw a ReferenceError.
        assertThat(jsp).containsSubsequence("if (typeof registerFormSubmit === 'function') {", "registerFormSubmit('");
    }

    @Test
    @DisplayName("should list nothing, not fail, when a last-name search matches nobody")
    void shouldListNothing_whenSearchMatchesNobody() throws IOException {
        String jsp = read("billingManageReferralDoc.jsp");

        // getBillingreferralByLastName answers no match with null, and .size() on it was an HTTP 500.
        assertThat(jsp).containsSubsequence(
                "List<Billingreferral> alist = billingReferralDao.getBillingreferralByLastName(lastname);",
                "if (alist == null) alist = Collections.emptyList();",
                "for (int i = 0; i < alist.size(); i++) {");
    }
}
